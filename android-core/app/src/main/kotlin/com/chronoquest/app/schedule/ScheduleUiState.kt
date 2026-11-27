package com.chronoquest.app.schedule

import com.chronoquest.rules.model.QuestKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** 반복 일정 한 줄. */
data class TemplateRow(
    val id: Long,
    val title: String,
    val kindLabel: String,
    val daysLabel: String,
    val timeLabel: String,
)

/** 다가오는 일정 한 줄. 기록이 있으면 취소할 수 없다(스펙 D13). */
data class UpcomingQuestRow(
    val id: String,
    val title: String,
    val timeLabel: String,
    val canceled: Boolean,
    val canCancel: Boolean,
    val canRestore: Boolean,
)

/** 다가오는 하루. 그날 기록이 있으면 휴가로 켤 수 없다(이미 휴가면 끌 수는 있다). */
data class UpcomingDayRow(
    val date: LocalDate,
    val dateLabel: String,
    val vacation: Boolean,
    val canToggleVacation: Boolean,
    val quests: List<UpcomingQuestRow>,
)

/** 7일 뒤로 미리 잡아 둔 휴가. */
data class VacationRow(val date: LocalDate, val label: String)

/** 반복 일정 편집 값. [id]가 0이면 새로 만든다. */
data class TemplateForm(
    val id: Long = 0,
    val title: String = "",
    val kind: QuestKind = QuestKind.WORK,
    val days: Set<DayOfWeek> = WEEKDAYS,
    val due: LocalTime = LocalTime.of(9, 0),
    val depart: LocalTime = LocalTime.of(8, 30),
) {
    companion object {
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
    }
}

/** 단발 일정 추가 값. */
data class OneOffForm(
    val date: LocalDate,
    val title: String = "",
    val kind: QuestKind = QuestKind.OTHER,
    val due: LocalTime = LocalTime.of(14, 0),
    val depart: LocalTime = LocalTime.of(13, 30),
)

sealed interface ScheduleDialog {
    /** 반복 일정 추가·편집. [form]은 창을 열 때의 값이다. */
    data class EditTemplate(val form: TemplateForm) : ScheduleDialog
    data class ConfirmDeleteTemplate(val id: Long, val title: String) : ScheduleDialog
    data class AddOneOff(val form: OneOffForm) : ScheduleDialog

    /** 7일 뒤 휴가 날짜 고르기. 오늘 이전은 고를 수 없다. */
    data object PickVacationDate : ScheduleDialog
}

data class ScheduleUiState(
    val loading: Boolean = true,
    /** 기기 기준 오늘. 날짜 고르기의 최솟값이다. */
    val today: LocalDate? = null,
    val templates: List<TemplateRow> = emptyList(),
    val days: List<UpcomingDayRow> = emptyList(),
    val laterVacations: List<VacationRow> = emptyList(),
    val dialog: ScheduleDialog? = null,
    val message: String? = null,
)
