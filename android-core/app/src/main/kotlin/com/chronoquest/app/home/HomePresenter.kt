package com.chronoquest.app.home

import com.chronoquest.app.notify.SettlementNotifier
import com.chronoquest.app.ui.ActionGate
import com.chronoquest.app.ui.Messages
import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.RecordSource
import com.chronoquest.core.domain.TodayQuest
import com.chronoquest.core.service.ActionResult
import com.chronoquest.core.service.GameService
import com.chronoquest.core.service.ScheduleResult
import com.chronoquest.core.service.ScheduleService
import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.model.QuestKind
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
 * 쓰기 동작은 [gate]로 한 번에 하나만 돈다. 연타한 두 번째 동작은 조용히 버린다.
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
    private val gate = ActionGate()
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** 일정 생성 → 오늘 요약·정리 필요·펫·보스·대표 칭호·안 본 연출을 다시 읽는다. 앱 복귀와 1분 주기로 부른다. */
    suspend fun refresh() = withContext(compute) {
        refreshLock.withLock {
            schedule.generateQuests()
            val today = game.today()
            val gameState = game.state()
            val cleanup = game.needsCleanup()
            // 정리 필요한 지난 출근 중 그날 활동이 전혀 없는 날만 [휴가였어]를 보여준다(스펙 D13).
            val vacationDates = cleanup.filter { it.kind == QuestKind.WORK }.map { it.date }.distinct()
                .filterTo(HashSet()) { schedule.canMarkMissedVacation(it) }
            val title = game.representativeTitle()
            val celebrations = game.unseenCelebrations()
            // 행동통제는 답할 때까지 새로고침마다 다시 묻는다(창을 닫아도 그날 사라지지 않게).
            val askSelfControl = today.day?.selfControl == null && today.quests.any { q -> q.record?.departedAt != null }
            val selfControlDialog = HomeDialog.AskSelfControl(today.date)
            _state.update {
                it.copy(
                    loading = false,
                    tier = gameState.tier,
                    xp = gameState.xp,
                    nextTierXp = gameState.nextTierXp,
                    representativeTitle = title,
                    pet = CompanionCards.pet(gameState.pet, gameState.xp),
                    boss = CompanionCards.boss(gameState.boss),
                    quests = today.quests.map { q -> toCard(q, today.vacation) },
                    cleanup = cleanup.map { q -> toCleanup(q, canBeVacation = q.kind == QuestKind.WORK && q.date in vacationDates) },
                    celebrations = celebrations,
                    dialog = when {
                        askSelfControl && it.dialog == null -> selfControlDialog
                        // 다른 곳에서 이미 답했으면 남아 있는 질문 창을 닫는다(인계 메모 A2b-2).
                        !askSelfControl && it.dialog == selfControlDialog -> null
                        else -> it.dialog
                    },
                )
            }
        }
    }

    suspend fun onDepart(questId: String) {
        gate.run { withContext(compute) { handle(questId, PendingAction.Depart, origin = null) { game.depart(questId) } } }
    }

    /** 도착은 먼저 안전 여부를 묻는다(스펙 6.2). */
    fun onArrive(questId: String) {
        _state.update { it.copy(dialog = HomeDialog.AskSafety(questId)) }
    }

    suspend fun onSafetyAnswered(questId: String, safe: Boolean) {
        gate.run {
            withContext(compute) {
                handle(questId, PendingAction.Arrive(safe), origin = HomeDialog.AskSafety(questId)) { game.arrive(questId, safe) }
            }
        }
    }

    suspend fun onRecordAnyway(dialog: HomeDialog.ConfirmEarly) {
        gate.run {
            withContext(compute) {
                handle(dialog.questId, dialog.action, origin = dialog) { perform(dialog.questId, dialog.action, allowEarly = true) }
            }
        }
    }

    /** 창만 바꾸고 쓰지 않으므로 gate를 쓰지 않는다. */
    suspend fun onEditScheduleRequested(dialog: HomeDialog.ConfirmEarly) = withContext(compute) {
        if (!dialog.canReschedule) return@withContext
        val quest = game.today().quests.firstOrNull { it.quest.id == dialog.questId }?.quest
        if (quest == null) {
            _state.update { it.copy(dialog = null, message = Messages.NOT_FOUND) }
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

    suspend fun onRescheduleConfirmed(dialog: HomeDialog.Reschedule, due: LocalTime, depart: LocalTime) {
        gate.run {
            withContext(compute) {
                when (val result = schedule.reschedule(dialog.questId, due, depart)) {
                    is ScheduleResult.Saved ->
                        handle(result.id, dialog.action, origin = dialog) { perform(result.id, dialog.action, allowEarly = false) }
                    is ScheduleResult.Rejected ->
                        _state.update { it.copy(message = Messages.problem(result.problems)) }
                }
            }
        }
    }

    suspend fun onSelfControlAnswered(date: LocalDate, value: Boolean) {
        gate.run { withContext(compute) { applyResult(game.answerSelfControl(date, value), HomeDialog.AskSelfControl(date)) } }
    }

    suspend fun onCleanupLate(questId: String) {
        gate.run { withContext(compute) { applyResult(game.resolveNoRecord(questId), origin = null) } }
    }

    suspend fun onCleanupCancel(questId: String) {
        gate.run { withContext(compute) { applyResult(game.cancelQuest(questId), origin = null) } }
    }

    fun onCleanupAddArrival(questId: String) {
        val card = _state.value.cleanup.firstOrNull { it.id == questId } ?: return
        _state.update { it.copy(dialog = HomeDialog.AddArrival(questId, card.date, card.due)) }
    }

    suspend fun onArrivalEntered(dialog: HomeDialog.AddArrival, time: LocalTime, safe: Boolean) {
        gate.run {
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
        }
    }

    /** 정리 필요 카드의 [휴가였어]. 그날 활동이 없는 지난 출근에만 창을 연다. */
    fun onCleanupVacation(questId: String) {
        val card = _state.value.cleanup.firstOrNull { it.id == questId && it.canBeVacation } ?: return
        _state.update { it.copy(dialog = HomeDialog.ConfirmVacation(card.date)) }
    }

    suspend fun onVacationConfirmed(dialog: HomeDialog.ConfirmVacation) {
        gate.run {
            withContext(compute) {
                val result = schedule.setVacation(dialog.date, true)
                _state.update {
                    it.copy(
                        dialog = closeIfOrigin(it.dialog, dialog),
                        message = if (result is ScheduleResult.Rejected) Messages.problem(result.problems) else it.message,
                    )
                }
                refresh()
            }
        }
    }

    /** 화면에 보인 연출만 본 것으로 표시한다. 그사이 새로 생긴 연출은 다음에 보여준다(인계 메모 A2b-2). */
    suspend fun onCelebrationsSeen(keys: List<String>) {
        gate.run {
            withContext(compute) {
                game.markSeen(keys)
                refresh()
            }
        }
    }

    fun dismissDialog() {
        _state.update { it.copy(dialog = null) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    /** 동작 중 예외. 로딩을 풀고 알려준다. 자세한 오류는 화면에 보이지 않는다. */
    fun onFailure(error: Throwable) {
        _state.update { it.copy(loading = false, message = Messages.FAILURE) }
    }

    private suspend fun perform(questId: String, action: PendingAction, allowEarly: Boolean): ActionResult =
        when (action) {
            PendingAction.Depart -> game.depart(questId, allowEarly = allowEarly)
            is PendingAction.Arrive -> game.arrive(questId, action.safe, allowEarly = allowEarly)
        }

    /**
     * 30분 이상 이르면 확인 창을 띄우고, 아니면 결과를 반영한다.
     * [origin]은 이 동작을 시작한 창이다(카드 버튼이면 null). 결과가 거부여도 다른 동작이 연 창은 닫지 않는다.
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
                it.copy(dialog = closeIfOrigin(it.dialog, origin), message = Messages.reject(result.reason))
            }
            is ActionResult.ConfirmEarly -> _state.update { it.copy(dialog = closeIfOrigin(it.dialog, origin)) }
            ActionResult.NotFound -> _state.update {
                it.copy(dialog = closeIfOrigin(it.dialog, origin), message = Messages.NOT_FOUND)
            }
        }
        refresh()
    }

    private fun closeIfOrigin(current: HomeDialog?, origin: HomeDialog?): HomeDialog? =
        if (current == origin) null else current

    private fun toCard(item: TodayQuest, vacation: Boolean): QuestCard {
        val status = when {
            item.quest.status == QuestStatus.CANCELED -> CardStatus.CANCELED
            vacation && item.quest.kind == QuestKind.WORK -> CardStatus.VACATION
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

    private fun toCleanup(quest: QuestEntity, canBeVacation: Boolean) = CleanupCard(
        id = quest.id,
        title = quest.title,
        whenLabel = quest.dueAt.format(CLEANUP_WHEN),
        date = quest.date,
        due = quest.dueAt.toLocalTime(),
        canBeVacation = canBeVacation,
    )

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val CLEANUP_WHEN: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d (E) HH:mm", Locale.KOREAN)
    }
}
