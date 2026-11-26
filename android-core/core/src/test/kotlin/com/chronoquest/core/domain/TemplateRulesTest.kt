package com.chronoquest.core.domain

import com.chronoquest.core.WEEKDAYS_MASK
import com.chronoquest.core.weekdayTemplate
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.assertEquals
import org.junit.Test

class TemplateRulesTest {
    @Test
    fun `요일 마스크는 월요일이 첫 비트다`() {
        val weekdays = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        assertEquals(WEEKDAYS_MASK, TemplateRules.maskOf(weekdays))
        assertEquals(weekdays, TemplateRules.daysOf(WEEKDAYS_MASK))
        assertEquals(1, TemplateRules.bit(DayOfWeek.MONDAY))
        assertEquals(64, TemplateRules.bit(DayOfWeek.SUNDAY))
    }

    @Test
    fun `제목이 비었거나 출발이 예정보다 늦으면 문제`() {
        assertEquals(emptyList(), TemplateRules.problems("출근", LocalTime.parse("09:00"), LocalTime.parse("09:00")))
        assertEquals(
            listOf(ScheduleProblem.BLANK_TITLE, ScheduleProblem.DEPART_AFTER_DUE),
            TemplateRules.problems("  ", LocalTime.parse("09:00"), LocalTime.parse("09:01")),
        )
    }

    @Test
    fun `요일이 하나도 없는 템플릿은 문제`() {
        assertEquals(emptyList(), TemplateRules.validate(weekdayTemplate()))
        assertEquals(listOf(ScheduleProblem.NO_DAYS), TemplateRules.validate(weekdayTemplate(daysMask = 0)))
        assertEquals(
            listOf(ScheduleProblem.DEPART_AFTER_DUE, ScheduleProblem.NO_DAYS),
            TemplateRules.validate(weekdayTemplate(depart = "09:30", daysMask = 0)),
        )
    }
}
