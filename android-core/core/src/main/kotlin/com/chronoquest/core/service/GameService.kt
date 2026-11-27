package com.chronoquest.core.service

import androidx.room.withTransaction
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.CelebrationSeenEntity
import com.chronoquest.core.data.DayRecordEntity
import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.core.data.RecordSource
import com.chronoquest.core.domain.Celebration
import com.chronoquest.core.domain.Celebrations
import com.chronoquest.core.domain.CleanupFilter
import com.chronoquest.core.domain.DayActivity
import com.chronoquest.core.domain.RecordPolicy
import com.chronoquest.core.domain.RecordResult
import com.chronoquest.core.domain.RejectReason
import com.chronoquest.core.domain.RepresentativeTitle
import com.chronoquest.core.domain.Today
import com.chronoquest.core.domain.TodaySummary
import com.chronoquest.rules.engine.Engine
import com.chronoquest.rules.engine.GameState
import com.chronoquest.rules.model.QuestKind
import com.chronoquest.rules.model.QuestStatus
import com.chronoquest.rules.titles.TitleCatalog
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ActionResult {
    /** 쓰기 성공. [newCelebrations]는 이번 쓰기로 새로 생겼고 아직 안 본 연출이다. */
    data class Done(
        val xpGained: Int,
        val newCelebrations: List<Celebration>,
        val askSelfControl: Boolean = false,
    ) : ActionResult

    data class Rejected(val reason: RejectReason) : ActionResult

    /** 기준 시각보다 [minutesEarly]분 이르다. 기록하지 않았다. 일정을 바꾸거나 `allowEarly`로 다시 부른다. */
    data class ConfirmEarly(val minutesEarly: Long) : ActionResult

    data object NotFound : ActionResult
}

/**
 * 기록 쓰기와 연출의 창구. 앱에서 하나만 만들어 재사용한다(엔진 인스턴스도 하나, 인계 메모 A2).
 * 쓰기는 뮤텍스와 DB 트랜잭션 안에서 읽기-판단-쓰기를 한 번에 하므로, 인스턴스가 여럿이어도 먼저 확정된 것이 이긴다.
 * 쓰기 전후 상태를 비교해 새로 생긴 연출을 돌려준다.
 */
class GameService(
    private val db: ChronoDatabase,
    private val clock: Clock,
    private val catalog: TitleCatalog = TitleCatalog.load(),
    private val engine: Engine = Engine(catalog),
) {
    private val ledgers = LedgerRepository(db)
    private val mutex = Mutex()

    suspend fun state(): GameState = stateAt(clock.nowLocal())

    /** 출발 기록. 목표 출발보다 30분 이상 이르면 [ActionResult.ConfirmEarly]를 돌려주고, [allowEarly]면 그대로 기록한다. */
    suspend fun depart(
        questId: String,
        at: LocalDateTime? = null,
        source: RecordSource = RecordSource.BUTTON,
        allowEarly: Boolean = false,
    ): ActionResult = write { now ->
        val result = recordAction(questId, now) { quest, current ->
            RecordPolicy.depart(quest, current, at ?: now, now, source, allowEarly)
        }
        if (result is ActionResult.Done) result.copy(askSelfControl = shouldAskSelfControl(questId)) else result
    }

    /** 도착 기록. 예정보다 30분 이상 이르면 [ActionResult.ConfirmEarly]를 돌려주고, [allowEarly]면 그대로 기록한다. */
    suspend fun arrive(
        questId: String,
        safe: Boolean,
        at: LocalDateTime? = null,
        source: RecordSource = RecordSource.BUTTON,
        allowEarly: Boolean = false,
    ): ActionResult = write { now ->
        recordAction(questId, now) { quest, current ->
            RecordPolicy.arrive(quest, current, at ?: now, safe, now, source, allowEarly)
        }
    }

    suspend fun editRecord(
        questId: String,
        departedAt: LocalDateTime?,
        arrivedAt: LocalDateTime?,
        safe: Boolean?,
    ): ActionResult = write { now ->
        recordAction(questId, now) { quest, current -> RecordPolicy.edit(quest, current, departedAt, arrivedAt, safe, now) }
    }

    suspend fun resolveNoRecord(questId: String): ActionResult = write { now ->
        recordAction(questId, now) { quest, current -> RecordPolicy.resolveNoRecord(quest, current, now) }
    }

    /**
     * 불가피한 취소(스펙 6.4, D13). 판정에서 빠진다.
     * 기록(출발·도착·기록 없음 정리)이 있는 일정은 취소할 수 없다. 실수로 XP를 날리지 않게 하려는 것이다.
     * 기록 없는 지난 일정은 홈 "정리 필요"에서 취소할 수 있다.
     */
    suspend fun cancelQuest(questId: String): ActionResult = write { now ->
        val quest = db.questDao().get(questId) ?: return@write ActionResult.NotFound
        if (quest.status == QuestStatus.CANCELED) return@write ActionResult.Rejected(RejectReason.QUEST_CANCELED)
        if (DayActivity.hasRecord(db.recordDao().get(questId))) {
            return@write ActionResult.Rejected(RejectReason.HAS_RECORD)
        }
        applyChange(now) { db.questDao().update(quest.copy(status = QuestStatus.CANCELED)) }
    }

    /** 취소 되돌리기. 오늘과 그 뒤 일정만 된다(스펙 D13). */
    suspend fun restoreQuest(questId: String): ActionResult = write { now ->
        val quest = db.questDao().get(questId) ?: return@write ActionResult.NotFound
        if (quest.status != QuestStatus.CANCELED) return@write ActionResult.Rejected(RejectReason.NOT_CANCELED)
        if (quest.date.isBefore(now.toLocalDate())) return@write ActionResult.Rejected(RejectReason.PAST_DATE)
        applyChange(now) { db.questDao().update(quest.copy(status = QuestStatus.SCHEDULED)) }
    }

    /** 행동통제 답. 오늘 이후 날짜는 미리 답할 수 없다. */
    suspend fun answerSelfControl(date: LocalDate, value: Boolean): ActionResult = write { now ->
        if (date.isAfter(now.toLocalDate())) return@write ActionResult.Rejected(RejectReason.FUTURE_TIME)
        applyChange(now) {
            val current = db.dayDao().get(date) ?: DayRecordEntity(date = date)
            db.dayDao().upsert(current.copy(selfControl = value))
        }
    }

    suspend fun unseenCelebrations(): List<Celebration> =
        Celebrations.unseen(state(), db.celebrationDao().seenKeys().toSet())

    suspend fun markSeen(keys: Collection<String>) {
        val at = clock.nowLocal()
        db.celebrationDao().markSeen(keys.map { CelebrationSeenEntity(grantKey = it, seenAt = at) })
    }

    /** 홈 "오늘의 퀘스트"용 요약. */
    suspend fun today(): TodaySummary {
        val now = clock.nowLocal()
        val state = engine.compute(ledgers.loadLedger(), now)
        return Today.summarize(now.toLocalDate(), state, db.questDao().all(), db.recordDao().all(), db.dayDao().all())
    }

    /** 홈 "정리 필요" 카드 대상. */
    suspend fun needsCleanup(): List<QuestEntity> {
        val now = clock.nowLocal()
        return CleanupFilter.needsCleanup(engine.compute(ledgers.loadLedger(), now), db.questDao().all(), now)
    }

    /**
     * 홈 대표 칭호 이름. 저장된 것이 없거나 더는 갖고 있지 않으면 가장 최근 칭호를 골라 저장하고,
     * 칭호가 하나도 없으면 저장값을 지운다. 새 칭호를 얻었다고 저절로 바꾸지는 않는다.
     */
    suspend fun representativeTitle(): String = write { now ->
        val stored = ledgers.settings().representativeTitleId
        val choice = RepresentativeTitle.resolve(stored, stateAt(now), catalog)
        if (choice.titleId != stored) {
            db.settingsDao().upsert(ledgers.settings().copy(representativeTitleId = choice.titleId))
        }
        choice.name
    }

    /** 칭호 화면의 NEW 표시 대상: 가진 칭호 중 아직 보지 않은 것. */
    suspend fun newTitleIds(): Set<Int> =
        Celebrations.newTitleIds(state(), db.celebrationDao().seenKeys().toSet())

    /** 칭호 화면에서 본 칭호의 NEW 표시를 뗀다. */
    suspend fun markTitlesViewed(ids: Collection<Int>) {
        markSeen(ids.map { Celebrations.codexKey(it) })
    }

    /** 가진 칭호만 대표로 고를 수 있다. null이면 선택을 지운다. 고를 수 없으면 false. */
    suspend fun setRepresentativeTitle(titleId: Int?): Boolean = write { now ->
        if (titleId != null && (stateAt(now).inventory[titleId] ?: 0) == 0) return@write false
        db.settingsDao().upsert(ledgers.settings().copy(representativeTitleId = titleId))
        true
    }

    private suspend fun stateAt(now: LocalDateTime): GameState = engine.compute(ledgers.loadLedger(), now)

    /**
     * 쓰기 한 건. 인스턴스 안에서는 뮤텍스로, 인스턴스 사이에서는 DB 트랜잭션으로 직렬화한다.
     * 시각은 한 번만 읽어 판단과 전후 상태 계산에 같이 쓴다.
     */
    private suspend fun <T> write(action: suspend (now: LocalDateTime) -> T): T =
        mutex.withLock { db.withTransaction { action(clock.nowLocal()) } }

    private suspend fun recordAction(
        questId: String,
        now: LocalDateTime,
        decide: (QuestEntity, QuestRecordEntity?) -> RecordResult,
    ): ActionResult {
        val quest = db.questDao().get(questId) ?: return ActionResult.NotFound
        // 엔진은 휴가일의 출근을 판정에서 빼므로, 그날 출근은 기록 자체를 받지 않는다.
        if (quest.kind == QuestKind.WORK && db.vacationDao().isVacation(quest.date)) {
            return ActionResult.Rejected(RejectReason.ON_VACATION)
        }
        return when (val result = decide(quest, db.recordDao().get(questId))) {
            is RecordResult.Rejected -> ActionResult.Rejected(result.reason)
            is RecordResult.NeedsEarlyConfirmation -> ActionResult.ConfirmEarly(result.minutesEarly)
            is RecordResult.Ok -> applyChange(now) { db.recordDao().upsert(result.record) }
        }
    }

    private suspend fun applyChange(now: LocalDateTime, change: suspend () -> Unit): ActionResult.Done {
        val before = stateAt(now)
        change()
        val after = stateAt(now)
        val beforeKeys = Celebrations.all(before).mapTo(HashSet()) { it.key }
        val seen = db.celebrationDao().seenKeys().toSet()
        return ActionResult.Done(
            xpGained = after.xp - before.xp,
            newCelebrations = Celebrations.all(after).filter { it.key !in beforeKeys && it.key !in seen },
        )
    }

    /** 그날 첫 출발이고 행동통제를 아직 답하지 않았으면 물어본다(스펙 6.2). */
    private suspend fun shouldAskSelfControl(questId: String): Boolean {
        val quest = db.questDao().get(questId) ?: return false
        if (db.dayDao().get(quest.date)?.selfControl != null) return false
        return db.questDao().onDate(quest.date)
            .filter { it.id != questId }
            .none { db.recordDao().get(it.id)?.departedAt != null }
    }
}
