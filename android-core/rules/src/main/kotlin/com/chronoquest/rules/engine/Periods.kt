package com.chronoquest.rules.engine

import com.chronoquest.rules.judge.ArrivalVerdict
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** 주(월~일)·월(달력) 계산 (스펙 5.5). */
internal object Periods {
    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** 시즌 시작이 월요일이 아니면 그다음 월요일부터 보너스 대상이다. */
    fun firstFullWeek(seasonStart: LocalDate): LocalDate =
        if (seasonStart.dayOfWeek == DayOfWeek.MONDAY) seasonStart
        else seasonStart.with(TemporalAdjusters.next(DayOfWeek.MONDAY))

    /** 시즌 시작이 1일이 아니면 그다음 달부터 보너스 대상이다. */
    fun firstFullMonth(seasonStart: LocalDate): YearMonth {
        val month = YearMonth.from(seasonStart)
        return if (seasonStart.dayOfMonth == 1) month else month.plusMonths(1)
    }

    /** [now] 시점에 이미 끝난 보너스 대상 주의 시작일. */
    fun closedWeeks(seasonStart: LocalDate, now: LocalDateTime): List<LocalDate> =
        generateSequence(firstFullWeek(seasonStart)) { it.plusWeeks(1) }
            .takeWhile { !it.plusDays(7).atStartOfDay().isAfter(now) }
            .toList()

    /** [now] 시점에 이미 끝난 보너스 대상 달. */
    fun closedMonths(seasonStart: LocalDate, now: LocalDateTime): List<YearMonth> =
        generateSequence(firstFullMonth(seasonStart)) { it.plusMonths(1) }
            .takeWhile { !it.plusMonths(1).atDay(1).atStartOfDay().isAfter(now) }
            .toList()
}

internal enum class PeriodResult { NO_WORK, PERFECT, CLOSE_ONLY, HAS_LATE, PENDING }

/** 한 기간의 출근 판정 목록으로 기간 결과를 정한다. null은 보류다. */
internal fun judgePeriod(verdicts: List<ArrivalVerdict?>): PeriodResult = when {
    verdicts.isEmpty() -> PeriodResult.NO_WORK
    verdicts.any { it == null } -> PeriodResult.PENDING
    verdicts.all { it!!.isOnTime } -> PeriodResult.PERFECT
    verdicts.any { it == ArrivalVerdict.LATE } -> PeriodResult.HAS_LATE
    else -> PeriodResult.CLOSE_ONLY
}
