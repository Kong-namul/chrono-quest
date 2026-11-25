package com.chronoquest.rules.engine

import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.titles.Series
import com.chronoquest.rules.titles.Title
import com.chronoquest.rules.titles.TitleCatalog
import java.time.LocalDateTime

/** 시간순 이벤트를 하나씩 받아 XP·카운터·연속 기록·지급 내역을 쌓는다. */
internal class Accumulator(private val catalog: TitleCatalog) {
    var xp: Int = 0
        private set
    var counters: Counters = Counters()
        private set
    var arriveStreak: Streak = Streak()
        private set
    var perfectWeekStreak: Streak = Streak()
        private set
    private val grants = mutableListOf<Grant>()
    /** 보류된 주가 나오면 그 뒤 주들은 판정하지 않는다(스펙 5.2 보류 규칙). */
    private var weeksBlocked = false
    private var monthsBlocked = false
    private val comeback = ComebackTracker()

    fun grants(): List<Grant> = grants.toList()

    fun handle(event: Event) {
        when (event) {
            is Event.Depart -> onDepart(event)
            is Event.Arrive -> onArrive(event)
            is Event.Wake -> onWake(event)
            is Event.SelfControl -> onSelfControl(event)
            is Event.WeekClose -> onWeekClose(event)
            is Event.MonthClose -> onMonthClose(event)
        }
    }

    private fun onDepart(e: Event.Depart) {
        val key = "dep:${e.quest.id}"
        counters = counters.copy(depart = counters.depart + 1)
        addXp("$key:xp", e.at, GrantType.XP_DEPART, XP_DEPART)
        award(key, e.at, catalog.cumulativeAwards(Series.DEPART_TOTAL, counters.depart))
    }

    private fun onArrive(e: Event.Arrive) {
        val key = "arr:${e.quest.id}"
        when (e.verdict) {
            ArrivalVerdict.EARLY, ArrivalVerdict.ON_TIME -> {
                val early = e.verdict == ArrivalVerdict.EARLY
                counters = counters.copy(
                    arrive = counters.arrive + 1,
                    early = counters.early + if (early) 1 else 0,
                )
                arriveStreak = arriveStreak.advance()
                addXp("$key:xp", e.at, GrantType.XP_ARRIVE, XP_ARRIVE)
                if (early) addXp("$key:early", e.at, GrantType.XP_EARLY, XP_EARLY_BONUS)
                award(key, e.at, catalog.cumulativeAwards(Series.ARRIVE_TOTAL, counters.arrive))
                if (early) award(key, e.at, catalog.cumulativeAwards(Series.EARLY_TOTAL, counters.early))
                award(key, e.at, catalog.streakAwards(Series.ARRIVE_STREAK, arriveStreak.current))
            }
            ArrivalVerdict.CLOSE -> Unit
            ArrivalVerdict.LATE -> arriveStreak = arriveStreak.reset()
        }
        if (comeback.onArrival(e.verdict)) {
            counters = counters.copy(comebacks = counters.comebacks + 1)
            award(key, e.at, catalog.cumulativeAwards(Series.COMEBACK_TOTAL, counters.comebacks))
        }
    }

    private fun onWake(e: Event.Wake) {
        val key = "wake:${e.date}"
        counters = counters.copy(wake = counters.wake + 1)
        addXp("$key:xp", e.at, GrantType.XP_WAKE, XP_WAKE)
        award(key, e.at, catalog.cumulativeAwards(Series.WAKE_TOTAL, counters.wake))
    }

    private fun onSelfControl(e: Event.SelfControl) {
        val key = "self:${e.date}"
        counters = counters.copy(selfControl = counters.selfControl + 1)
        addXp("$key:xp", e.at, GrantType.XP_SELF_CONTROL, XP_SELF_CONTROL)
        award(key, e.at, catalog.cumulativeAwards(Series.SELF_CONTROL_TOTAL, counters.selfControl))
    }

    private fun onWeekClose(e: Event.WeekClose) {
        if (weeksBlocked) return
        val key = "week:${e.weekStart}"
        when (e.result) {
            PeriodResult.NO_WORK, PeriodResult.CLOSE_ONLY -> Unit
            PeriodResult.PENDING -> weeksBlocked = true
            PeriodResult.HAS_LATE -> perfectWeekStreak = perfectWeekStreak.reset()
            PeriodResult.PERFECT -> {
                counters = counters.copy(perfectWeeks = counters.perfectWeeks + 1)
                perfectWeekStreak = perfectWeekStreak.advance()
                addXp("$key:xp", e.at, GrantType.XP_PERFECT_WEEK, XP_PERFECT_WEEK)
                if (perfectWeekStreak.current % 3 == 0) {
                    addXp("$key:3w", e.at, GrantType.XP_THREE_WEEKS, XP_THREE_WEEKS)
                }
                award(key, e.at, catalog.cumulativeAwards(Series.PERFECT_WEEK_TOTAL, counters.perfectWeeks))
                award(key, e.at, catalog.streakAwards(Series.PERFECT_WEEK_STREAK, perfectWeekStreak.current))
            }
        }
    }

    private fun onMonthClose(e: Event.MonthClose) {
        if (monthsBlocked) return
        val key = "month:${e.month}"
        when (e.result) {
            PeriodResult.PENDING -> monthsBlocked = true
            PeriodResult.PERFECT -> {
                counters = counters.copy(perfectMonths = counters.perfectMonths + 1)
                addXp("$key:xp", e.at, GrantType.XP_PERFECT_MONTH, XP_PERFECT_MONTH)
                award(key, e.at, catalog.cumulativeAwards(Series.PERFECT_MONTH_TOTAL, counters.perfectMonths))
            }
            PeriodResult.NO_WORK, PeriodResult.CLOSE_ONLY, PeriodResult.HAS_LATE -> Unit
        }
    }

    private fun addXp(key: String, at: LocalDateTime, type: GrantType, amount: Int) {
        xp += amount
        grants += Grant(key = key, at = at, type = type, xp = amount)
    }

    private fun award(eventKey: String, at: LocalDateTime, titles: List<Title>) {
        titles.forEach { t ->
            grants += Grant(
                key = "$eventKey:title:${t.id}",
                at = at,
                type = GrantType.TITLE,
                titleId = t.id,
                detail = t.name,
            )
        }
    }

    companion object {
        const val XP_ARRIVE = 20
        const val XP_EARLY_BONUS = 10
        const val XP_DEPART = 10
        const val XP_WAKE = 5
        const val XP_SELF_CONTROL = 5
        const val XP_PERFECT_WEEK = 100
        const val XP_THREE_WEEKS = 300
        const val XP_PERFECT_MONTH = 500
    }
}
