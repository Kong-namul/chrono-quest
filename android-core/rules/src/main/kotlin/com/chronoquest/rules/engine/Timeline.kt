package com.chronoquest.rules.engine

import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.model.Quest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** 원장에서 뽑은 시간순 이벤트. 같은 시각이면 [order], 그다음 [tieKey] 순으로 처리한다. */
internal sealed class Event(val at: LocalDateTime, val order: Int, val tieKey: String) {
    class Depart(at: LocalDateTime, val quest: Quest) : Event(at, 0, quest.id)
    class Arrive(at: LocalDateTime, val quest: Quest, val verdict: ArrivalVerdict) : Event(at, 1, quest.id)
    class Wake(at: LocalDateTime, val date: LocalDate) : Event(at, 2, date.toString())
    class SelfControl(at: LocalDateTime, val date: LocalDate) : Event(at, 3, date.toString())
    class WeekClose(val weekStart: LocalDate, val result: PeriodResult) :
        Event(weekStart.plusDays(7).atStartOfDay(), 4, weekStart.toString())
    class MonthClose(val month: YearMonth, val result: PeriodResult) :
        Event(month.plusMonths(1).atDay(1).atStartOfDay(), 5, month.toString())

    companion object {
        val ORDER: Comparator<Event> = compareBy<Event>({ it.at }, { it.order }, { it.tieKey })
    }
}
