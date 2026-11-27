package com.chronoquest.app.schedule

import com.chronoquest.app.notify.SettlementNotifier
import com.chronoquest.app.ui.ActionGate
import com.chronoquest.app.ui.Messages
import com.chronoquest.core.data.ScheduleTemplateEntity
import com.chronoquest.core.domain.TemplateRules
import com.chronoquest.core.domain.UpcomingDay
import com.chronoquest.core.service.ActionResult
import com.chronoquest.core.service.GameService
import com.chronoquest.core.service.ScheduleResult
import com.chronoquest.core.service.ScheduleService
import com.chronoquest.rules.model.QuestKind
import com.chronoquest.rules.model.QuestStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
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
 * 일정 탭(스펙 6.4·7.2): 반복 일정, 오늘부터 7일 일정의 취소·되돌리기, 단발 일정, 휴가.
 * 취소·휴가는 오늘 이후만 바꾼다(스펙 D13). 쓰기는 GameService·ScheduleService만 거친다.
 * 저장이 거부되면 창을 그대로 두고 이유만 알려준다.
 */
class SchedulePresenter(
    game: () -> GameService,
    schedule: () -> ScheduleService,
    private val notifier: SettlementNotifier,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) {
    private val game by lazy(game)
    private val schedule by lazy(schedule)
    private val refreshLock = Mutex()
    private val gate = ActionGate()
    private val _state = MutableStateFlow(ScheduleUiState())
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()

    suspend fun refresh() = withContext(compute) {
        refreshLock.withLock {
            schedule.generateQuests()
            val templates = schedule.templates()
            val days = schedule.upcoming()
            val lastShown = days.last().date
            val later = schedule.vacations().filter { it.isAfter(lastShown) }
            _state.update {
                it.copy(
                    loading = false,
                    today = days.first().date,
                    templates = templates.map(::toTemplateRow),
                    days = days.mapIndexed(::toDayRow),
                    laterVacations = later.map { date -> VacationRow(date, date.format(DATE)) },
                )
            }
        }
    }

    fun onAddTemplate() {
        _state.update { it.copy(dialog = ScheduleDialog.EditTemplate(TemplateForm())) }
    }

    suspend fun onEditTemplate(id: Long) = withContext(compute) {
        val template = schedule.templates().firstOrNull { it.id == id }
        _state.update {
            if (template == null) {
                it.copy(message = Messages.NOT_FOUND)
            } else {
                it.copy(dialog = ScheduleDialog.EditTemplate(template.toForm()))
            }
        }
    }

    suspend fun onTemplateSaved(dialog: ScheduleDialog.EditTemplate, form: TemplateForm) {
        gate.run {
            withContext(compute) {
                // 고칠 때는 장소·활성 같은 다른 필드를 그대로 둔다.
                val existing = if (form.id == 0L) null else schedule.templates().firstOrNull { it.id == form.id }
                val base = existing ?: ScheduleTemplateEntity(
                    title = form.title,
                    kind = form.kind,
                    daysMask = 0,
                    dueTime = form.due,
                    targetDepartTime = form.depart,
                )
                val entity = base.copy(
                    title = form.title.trim(),
                    kind = form.kind,
                    daysMask = TemplateRules.maskOf(form.days),
                    dueTime = form.due,
                    targetDepartTime = form.depart,
                )
                when (val result = schedule.saveTemplate(entity)) {
                    is ScheduleResult.Saved -> _state.update { it.copy(dialog = closeIfOrigin(it.dialog, dialog)) }
                    is ScheduleResult.Rejected -> _state.update { it.copy(message = Messages.problem(result.problems)) }
                }
                refresh()
            }
        }
    }

    fun onDeleteTemplateRequested(id: Long, title: String) {
        _state.update { it.copy(dialog = ScheduleDialog.ConfirmDeleteTemplate(id, title)) }
    }

    suspend fun onDeleteTemplateConfirmed(dialog: ScheduleDialog.ConfirmDeleteTemplate) {
        gate.run {
            withContext(compute) {
                schedule.deleteTemplate(dialog.id)
                _state.update { it.copy(dialog = closeIfOrigin(it.dialog, dialog)) }
                refresh()
            }
        }
    }

    fun onAddOneOff() {
        val today = _state.value.today ?: return
        _state.update { it.copy(dialog = ScheduleDialog.AddOneOff(OneOffForm(date = today))) }
    }

    suspend fun onOneOffSaved(dialog: ScheduleDialog.AddOneOff, form: OneOffForm) {
        gate.run {
            withContext(compute) {
                val result = schedule.addOneOffQuest(form.date, form.kind, form.title.trim(), form.due, form.depart)
                when (result) {
                    is ScheduleResult.Saved -> _state.update { it.copy(dialog = closeIfOrigin(it.dialog, dialog)) }
                    is ScheduleResult.Rejected -> _state.update { it.copy(message = Messages.problem(result.problems)) }
                }
                refresh()
            }
        }
    }

    suspend fun onCancelQuest(questId: String) {
        gate.run { withContext(compute) { applyResult(game.cancelQuest(questId)) } }
    }

    suspend fun onRestoreQuest(questId: String) {
        gate.run { withContext(compute) { applyResult(game.restoreQuest(questId)) } }
    }

    suspend fun onVacationToggled(date: LocalDate, on: Boolean) {
        gate.run {
            withContext(compute) {
                val result = schedule.setVacation(date, on)
                if (result is ScheduleResult.Rejected) {
                    _state.update { it.copy(message = Messages.problem(result.problems)) }
                }
                refresh()
            }
        }
    }

    fun onPickVacationDate() {
        _state.update { it.copy(dialog = ScheduleDialog.PickVacationDate) }
    }

    suspend fun onVacationDatePicked(date: LocalDate) {
        gate.run {
            withContext(compute) {
                val result = schedule.setVacation(date, true)
                _state.update {
                    it.copy(
                        dialog = closeIfOrigin(it.dialog, ScheduleDialog.PickVacationDate),
                        message = if (result is ScheduleResult.Rejected) Messages.problem(result.problems) else it.message,
                    )
                }
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

    /** 동작 중 예외. 로딩을 풀고 알려준다. */
    fun onFailure(error: Throwable) {
        _state.update { it.copy(loading = false, message = Messages.FAILURE) }
    }

    private suspend fun applyResult(result: ActionResult) {
        when (result) {
            is ActionResult.Done ->
                if (result.xpGained != 0 || result.newCelebrations.isNotEmpty()) {
                    notifier.notify(result.xpGained, result.newCelebrations)
                }
            is ActionResult.Rejected -> _state.update { it.copy(message = Messages.reject(result.reason)) }
            ActionResult.NotFound -> _state.update { it.copy(message = Messages.NOT_FOUND) }
            // 취소·되돌리기는 이른 기록 확인을 돌려주지 않는다.
            is ActionResult.ConfirmEarly -> Unit
        }
        refresh()
    }

    private fun closeIfOrigin(current: ScheduleDialog?, origin: ScheduleDialog): ScheduleDialog? =
        if (current == origin) null else current

    private fun toTemplateRow(t: ScheduleTemplateEntity) = TemplateRow(
        id = t.id,
        title = t.title,
        kindLabel = kindLabel(t.kind),
        daysLabel = daysLabel(TemplateRules.daysOf(t.daysMask)),
        timeLabel = "출발 ${t.targetDepartTime.format(TIME)} · 도착 ${t.dueTime.format(TIME)}",
    )

    private fun toDayRow(index: Int, day: UpcomingDay) = UpcomingDayRow(
        date = day.date,
        dateLabel = when (index) {
            0 -> "오늘 · "
            1 -> "내일 · "
            else -> ""
        } + day.date.format(DATE),
        vacation = day.vacation,
        canToggleVacation = day.vacation || !day.hasActivity,
        quests = day.quests.map { item ->
            val canceled = item.quest.status == QuestStatus.CANCELED
            UpcomingQuestRow(
                id = item.quest.id,
                title = item.quest.title,
                timeLabel = "출발 ${item.quest.targetDepartAt.format(TIME)} · 도착 ${item.quest.dueAt.format(TIME)}",
                canceled = canceled,
                canCancel = !canceled && !item.hasRecord,
                canRestore = canceled,
            )
        },
    )

    private fun ScheduleTemplateEntity.toForm() = TemplateForm(
        id = id,
        title = title,
        kind = kind,
        days = TemplateRules.daysOf(daysMask),
        due = dueTime,
        depart = targetDepartTime,
    )

    companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN)
        private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        fun kindLabel(kind: QuestKind): String = when (kind) {
            QuestKind.WORK -> "출근"
            QuestKind.OTHER -> "약속"
        }

        fun dayLabel(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

        fun daysLabel(days: Set<DayOfWeek>): String = when (days) {
            DayOfWeek.values().toSet() -> "매일"
            TemplateForm.WEEKDAYS -> "평일"
            WEEKEND -> "주말"
            else -> days.sorted().joinToString(" ") { dayLabel(it) }
        }
    }
}
