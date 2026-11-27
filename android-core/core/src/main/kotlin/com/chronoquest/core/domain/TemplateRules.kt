package com.chronoquest.core.domain

import com.chronoquest.core.data.ScheduleTemplateEntity
import java.time.DayOfWeek
import java.time.LocalTime

enum class ScheduleProblem {
    BLANK_TITLE,
    NO_DAYS,
    DEPART_AFTER_DUE,
    DUPLICATE,
    BEFORE_SEASON,
    NOT_FOUND,
    HAS_RECORD,
    /** 지난 날짜라 바꿀 수 없다(스펙 D13). */
    PAST_DATE,
    /** 그날 기록이 있어서 휴가로 바꿀 수 없다(스펙 D13). */
    DAY_HAS_RECORD,
}

/** 반복 템플릿과 단발 일정의 입력 검증, 요일 마스크(월 bit0 ~ 일 bit6). */
object TemplateRules {
    const val ALL_DAYS_MASK = 0b1111111

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun maskOf(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or bit(day) }

    fun daysOf(mask: Int): Set<DayOfWeek> =
        DayOfWeek.values().filterTo(LinkedHashSet()) { (mask and bit(it)) != 0 }

    fun problems(title: String, dueTime: LocalTime, departTime: LocalTime): List<ScheduleProblem> = buildList {
        if (title.isBlank()) add(ScheduleProblem.BLANK_TITLE)
        if (departTime.isAfter(dueTime)) add(ScheduleProblem.DEPART_AFTER_DUE)
    }

    fun validate(template: ScheduleTemplateEntity): List<ScheduleProblem> = buildList {
        addAll(problems(template.title, template.dueTime, template.targetDepartTime))
        if ((template.daysMask and ALL_DAYS_MASK) == 0) add(ScheduleProblem.NO_DAYS)
    }
}
