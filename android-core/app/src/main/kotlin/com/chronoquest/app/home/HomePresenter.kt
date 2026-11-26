package com.chronoquest.app.home

import com.chronoquest.app.notify.SettlementNotifier
import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.RecordSource
import com.chronoquest.core.domain.RejectReason
import com.chronoquest.core.domain.ScheduleProblem
import com.chronoquest.core.domain.TodayQuest
import com.chronoquest.core.service.ActionResult
import com.chronoquest.core.service.GameService
import com.chronoquest.core.service.ScheduleResult
import com.chronoquest.core.service.ScheduleService
import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.model.QuestStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 홈 화면의 상태와 동작. 쓰기는 전부 GameService·ScheduleService를 거친다.
 * 엔진 계산이 있으므로 [compute] 디스패처(기본 Default)에서 돈다(인계 메모 A2b "스레드").
 */
class HomePresenter(
    game: () -> GameService,
    schedule: () -> ScheduleService,
    private val notifier: SettlementNotifier,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) {
    // 서비스(칭호표 로딩 포함)는 compute 디스패처 안에서 처음 만든다.
    private val game by lazy(game)
    private val schedule by lazy(schedule)
    private val refreshLock = Mutex()
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** 일정 생성 → 오늘 요약·정리 필요·대표 칭호·안 본 연출을 다시 읽는다. 앱 복귀와 1분 주기로 부른다. */
    suspend fun refresh() = withContext(compute) {
        refreshLock.withLock {
            schedule.generateQuests()
            val today = game.today()
            val gameState = game.state()
            val cleanup = game.needsCleanup()
            val title = game.representativeTitle()
            val celebrations = game.unseenCelebrations()
            // 행동통제는 답할 때까지 새로고침마다 다시 묻는다(창을 닫아도 그날 사라지지 않게).
            val askSelfControl = today.day?.selfControl == null && today.quests.any { q -> q.record?.departedAt != null }
            _state.update {
                it.copy(
                    loading = false,
                    tier = gameState.tier,
                    xp = gameState.xp,
                    nextTierXp = gameState.nextTierXp,
                    representativeTitle = title,
                    quests = today.quests.map(::toCard),
                    cleanup = cleanup.map(::toCleanup),
                    celebrations = celebrations,
                    dialog = if (askSelfControl && it.dialog == null) HomeDialog.AskSelfControl(today.date) else it.dialog,
                )
            }
        }
    }

    suspend fun onDepart(questId: String) = withContext(compute) {
        handle(questId, PendingAction.Depart, origin = null) { game.depart(questId) }
    }

    /** 도착은 먼저 안전 여부를 묻는다(스펙 6.2). */
    fun onArrive(questId: String) {
        _state.update { it.copy(dialog = HomeDialog.AskSafety(questId)) }
    }

    suspend fun onSafetyAnswered(questId: String, safe: Boolean) = withContext(compute) {
        handle(questId, PendingAction.Arrive(safe), origin = HomeDialog.AskSafety(questId)) { game.arrive(questId, safe) }
    }

    suspend fun onRecordAnyway(dialog: HomeDialog.ConfirmEarly) = withContext(compute) {
        handle(dialog.questId, dialog.action, origin = dialog) { perform(dialog.questId, dialog.action, allowEarly = true) }
    }

    suspend fun onEditScheduleRequested(dialog: HomeDialog.ConfirmEarly) = withContext(compute) {
        if (!dialog.canReschedule) return@withContext
        val quest = game.today().quests.firstOrNull { it.quest.id == dialog.questId }?.quest
        if (quest == null) {
            _state.update { it.copy(dialog = null, message = NOT_FOUND_TEXT) }
        } else {
            _state.update {
                it.copy(
                    dialog = HomeDialog.Reschedule(
                        questId = quest.id,
                        action = dialog.action,
                        due = quest.dueAt.toLocalTime(),
                        depart = quest.targetDepartAt.toLocalTime(),
                    ),
                )
            }
        }
    }

    suspend fun onRescheduleConfirmed(dialog: HomeDialog.Reschedule, due: LocalTime, depart: LocalTime) =
        withContext(compute) {
            when (val result = schedule.reschedule(dialog.questId, due, depart)) {
                is ScheduleResult.Saved ->
                    handle(result.id, dialog.action, origin = dialog) { perform(result.id, dialog.action, allowEarly = false) }
                is ScheduleResult.Rejected ->
                    _state.update { it.copy(message = problemText(result.problems)) }
            }
        }

    suspend fun onSelfControlAnswered(date: LocalDate, value: Boolean) = withContext(compute) {
        applyResult(game.answerSelfControl(date, value), HomeDialog.AskSelfControl(date))
    }

    suspend fun onCleanupLate(questId: String) = withContext(compute) {
        applyResult(game.resolveNoRecord(questId), origin = null)
    }

    suspend fun onCleanupCancel(questId: String) = withContext(compute) {
        applyResult(game.cancelQuest(questId), origin = null)
    }

    fun onCleanupAddArrival(questId: String) {
        val card = _state.value.cleanup.firstOrNull { it.id == questId } ?: return
        _state.update { it.copy(dialog = HomeDialog.AddArrival(questId, card.date)) }
    }

    suspend fun onArrivalEntered(dialog: HomeDialog.AddArrival, time: LocalTime, safe: Boolean) =
        withContext(compute) {
            val result = game.arrive(
                dialog.questId,
                safe,
                at = dialog.date.atTime(time),
                source = RecordSource.EDIT,
                allowEarly = true,
            )
            applyResult(result, dialog)
        }

    suspend fun onCelebrationsSeen() = withContext(compute) {
        game.markSeen(_state.value.celebrations.map { it.key })
        refresh()
    }

    fun dismissDialog() {
        _state.update { it.copy(dialog = null) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    private suspend fun perform(questId: String, action: PendingAction, allowEarly: Boolean): ActionResult =
        when (action) {
            PendingAction.Depart -> game.depart(questId, allowEarly = allowEarly)
            is PendingAction.Arrive -> game.arrive(questId, action.safe, allowEarly = allowEarly)
        }

    /**
     * 30분 이상 이르면 확인 창을 띄우고, 아니면 결과를 반영한다.
     * [origin]은 이 동작을 시작한 창(카드 버튼이면 null). 결과가 거부여도 다른 동작이 연 창은 닫지 않는다.
     */
    private suspend fun handle(
        questId: String,
        action: PendingAction,
        origin: HomeDialog?,
        block: suspend () -> ActionResult,
    ) {
        when (val result = block()) {
            is ActionResult.ConfirmEarly -> {
                // 기록이 있는 일정은 ScheduleService가 시각 변경을 거부하므로 [일정 수정]을 숨긴다.
                val canReschedule = game.today().quests.firstOrNull { it.quest.id == questId }?.let { it.record == null } ?: false
                _state.update {
                    it.copy(dialog = HomeDialog.ConfirmEarly(questId, action, result.minutesEarly, canReschedule))
                }
            }
            else -> applyResult(result, origin)
        }
    }

    private suspend fun applyResult(result: ActionResult, origin: HomeDialog?) {
        when (result) {
            is ActionResult.Done -> {
                if (result.xpGained != 0 || result.newCelebrations.isNotEmpty()) {
                    notifier.notify(result.xpGained, result.newCelebrations)
                }
                _state.update { it.copy(dialog = closeIfOrigin(it.dialog, origin)) }
            }
            is ActionResult.Rejected -> _state.update {
                it.copy(dialog = closeIfOrigin(it.dialog, origin), message = rejectText(result.reason))
            }
            is ActionResult.ConfirmEarly -> _state.update { it.copy(dialog = closeIfOrigin(it.dialog, origin)) }
            ActionResult.NotFound -> _state.update {
                it.copy(dialog = closeIfOrigin(it.dialog, origin), message = NOT_FOUND_TEXT)
            }
        }
        refresh()
    }

    private fun closeIfOrigin(current: HomeDialog?, origin: HomeDialog?): HomeDialog? =
        if (current == origin) null else current

    private fun toCard(item: TodayQuest): QuestCard {
        val status = when {
            item.quest.status == QuestStatus.CANCELED -> CardStatus.CANCELED
            item.outcome?.verdict == ArrivalVerdict.EARLY -> CardStatus.EARLY
            item.outcome?.verdict == ArrivalVerdict.ON_TIME -> CardStatus.ON_TIME
            item.outcome?.verdict == ArrivalVerdict.CLOSE -> CardStatus.CLOSE
            item.outcome?.verdict == ArrivalVerdict.LATE -> CardStatus.LATE
            item.record?.departedAt != null -> CardStatus.DEPARTED
            else -> CardStatus.WAITING
        }
        return QuestCard(
            id = item.quest.id,
            title = item.quest.title,
            departLabel = item.quest.targetDepartAt.format(TIME),
            dueLabel = item.quest.dueAt.format(TIME),
            status = status,
            canDepart = status == CardStatus.WAITING,
            canArrive = status == CardStatus.WAITING || status == CardStatus.DEPARTED,
        )
    }

    private fun toCleanup(quest: QuestEntity) = CleanupCard(
        id = quest.id,
        title = quest.title,
        whenLabel = quest.dueAt.format(CLEANUP_WHEN),
        date = quest.date,
    )

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val CLEANUP_WHEN: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d (E) HH:mm", Locale.KOREAN)
        const val NOT_FOUND_TEXT = "일정을 찾을 수 없어."

        fun rejectText(reason: RejectReason): String = when (reason) {
            RejectReason.QUEST_CANCELED -> "취소한 일정이야."
            RejectReason.FUTURE_TIME -> "아직 오지 않은 시각은 기록할 수 없어."
            RejectReason.ALREADY_RECORDED -> "이미 기록했어."
            RejectReason.ARRIVAL_BEFORE_DEPARTURE -> "도착이 출발보다 빠를 수는 없어."
            RejectReason.SAFE_WITHOUT_ARRIVAL -> "도착 시각 없이 안전 여부만 넣을 수는 없어."
            RejectReason.SAFE_REQUIRED -> "안전하게 왔는지 알려줘."
            RejectReason.WRONG_DAY -> "그 일정 날짜에만 기록할 수 있어."
            RejectReason.NOT_DUE_YET -> "아직 예정 시각이 안 됐어."
        }

        fun problemText(problems: List<ScheduleProblem>): String = when (problems.firstOrNull()) {
            ScheduleProblem.DEPART_AFTER_DUE -> "출발 시각이 도착 시각보다 늦어."
            ScheduleProblem.DUPLICATE -> "같은 시각에 같은 종류 일정이 이미 있어."
            ScheduleProblem.HAS_RECORD -> "이미 기록이 있는 일정은 시각을 바꿀 수 없어."
            ScheduleProblem.NOT_FOUND -> NOT_FOUND_TEXT
            ScheduleProblem.BLANK_TITLE -> "일정 이름이 비어 있어."
            ScheduleProblem.BEFORE_SEASON -> "시즌 시작 전 날짜야."
            ScheduleProblem.NO_DAYS -> "요일을 하나 이상 골라줘."
            null -> "일정을 바꾸지 못했어."
        }
    }
}
