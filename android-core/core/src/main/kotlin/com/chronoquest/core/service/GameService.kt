package com.chronoquest.core.service

import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.CelebrationSeenEntity
import com.chronoquest.core.data.DayRecordEntity
import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.core.data.RecordSource
import com.chronoquest.core.domain.Celebration
import com.chronoquest.core.domain.Celebrations
import com.chronoquest.core.domain.CleanupFilter
import com.chronoquest.core.domain.RecordPolicy
import com.chronoquest.core.domain.RecordResult
import com.chronoquest.core.domain.RejectReason
import com.chronoquest.core.domain.RepresentativeTitle
import com.chronoquest.core.domain.Today
import com.chronoquest.core.domain.TodaySummary
import com.chronoquest.rules.engine.Engine
import com.chronoquest.rules.engine.GameState
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

    data object NotFound : ActionResult
}

/**
 * 기록 쓰기와 연출의 창구. 앱에서 하나만 만들어 재사용한다(엔진 인스턴스도 하나, 인계 메모 A2).
 * 쓰기는 직렬화하고, 쓰기 전후 상태를 비교해 새로 생긴 연출을 돌려준다.
 */
class GameService(
    private val db: ChronoDatabase,
    private val clock: Clock,
    private val catalog: TitleCatalog = TitleCatalog.load(),
    private val engine: Engine = Engine(catalog),
) {
    private val ledgers = LedgerRepository(db)
    private val mutex = Mutex()

    suspend fun state(): GameState = engine.compute(ledgers.loadLedger(), clock.nowLocal())

    suspend fun depart(
        questId: String,
        at: LocalDateTime? = null,
        source: RecordSource = RecordSource.BUTTON,
    ): ActionResult = mutex.withLock {
        val now = clock.nowLocal()
        val result = recordAction(questId) { quest, current ->
            RecordPolicy.depart(quest, current, at ?: now, now, source)
        }
        if (result is ActionResult.Done) result.copy(askSelfControl = shouldAskSelfControl(questId)) else result
    }

    suspend fun arrive(
        questId: String,
        safe: Boolean,
        at: LocalDateTime? = null,
        source: RecordSource = RecordSource.BUTTON,
    ): ActionResult = mutex.withLock {
        val now = clock.nowLocal()
        recordAction(questId) { quest, current -> RecordPolicy.arrive(quest, current, at ?: now, safe, now, source) }
    }

    suspend fun editRecord(
        questId: String,
        departedAt: LocalDateTime?,
        arrivedAt: LocalDateTime?,
        safe: Boolean?,
    ): ActionResult = mutex.withLock {
        val now = clock.nowLocal()
        recordAction(questId) { quest, current -> RecordPolicy.edit(quest, current, departedAt, arrivedAt, safe, now) }
    }

    suspend fun resolveNoRecord(questId: String): ActionResult = mutex.withLock {
        recordAction(questId) { quest, current -> RecordPolicy.resolveNoRecord(quest, current) }
    }

    /** 불가피한 취소(스펙 6.4). 판정에서 빠진다. */
    suspend fun cancelQuest(questId: String): ActionResult = mutex.withLock {
        val quest = db.questDao().get(questId) ?: return@withLock ActionResult.NotFound
        applyChange { db.questDao().update(quest.copy(status = QuestStatus.CANCELED)) }
    }

    suspend fun answerSelfControl(date: LocalDate, value: Boolean): ActionResult = mutex.withLock {
        applyChange {
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

    suspend fun representativeTitle(): String =
        RepresentativeTitle.resolve(ledgers.settings().representativeTitleId, state(), catalog)

    /** 가진 칭호만 대표로 고를 수 있다. null이면 선택을 지운다. 고를 수 없으면 false. */
    suspend fun setRepresentativeTitle(titleId: Int?): Boolean {
        if (titleId != null && (state().inventory[titleId] ?: 0) == 0) return false
        db.settingsDao().upsert(ledgers.settings().copy(representativeTitleId = titleId))
        return true
    }

    private suspend fun recordAction(
        questId: String,
        decide: (QuestEntity, QuestRecordEntity?) -> RecordResult,
    ): ActionResult {
        val quest = db.questDao().get(questId) ?: return ActionResult.NotFound
        return when (val result = decide(quest, db.recordDao().get(questId))) {
            is RecordResult.Rejected -> ActionResult.Rejected(result.reason)
            is RecordResult.Ok -> applyChange { db.recordDao().upsert(result.record) }
        }
    }

    private suspend fun applyChange(write: suspend () -> Unit): ActionResult.Done {
        val before = state()
        write()
        val after = state()
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
