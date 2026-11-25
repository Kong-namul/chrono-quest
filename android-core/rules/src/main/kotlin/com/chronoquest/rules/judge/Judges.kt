package com.chronoquest.rules.judge

import com.chronoquest.rules.model.DayRecord
import com.chronoquest.rules.model.Resolution
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class ArrivalVerdict {
    EARLY, ON_TIME, CLOSE, LATE;

    val isOnTime: Boolean get() = this == EARLY || this == ON_TIME
}

/** 개별 기록 판정 (스펙 5.1~5.3). 모든 비교는 분 단위로 내린 시각으로 한다. */
object Judges {
    const val EARLY_MINUTES = 10L
    const val GRACE_MINUTES = 5L
    const val WAKE_LIMIT_MINUTES = 5L

    /** 도착 판정. NO_RECORD와 위험 이동(safe=false)은 도착 시각이 없어도 LATE, 그 외에 도착 시각이나 안전 여부가 없으면 null. */
    fun arrival(
        dueAt: LocalDateTime,
        arrivedAt: LocalDateTime?,
        safe: Boolean?,
        resolution: Resolution,
    ): ArrivalVerdict? {
        if (resolution == Resolution.NO_RECORD) return ArrivalVerdict.LATE
        if (safe == false) return ArrivalVerdict.LATE
        if (arrivedAt == null || safe == null) return null
        val due = dueAt.toMinute()
        val arr = arrivedAt.toMinute()
        return when {
            !arr.isAfter(due.minusMinutes(EARLY_MINUTES)) -> ArrivalVerdict.EARLY
            !arr.isAfter(due) -> ArrivalVerdict.ON_TIME
            !arr.isAfter(due.plusMinutes(GRACE_MINUTES)) -> ArrivalVerdict.CLOSE
            else -> ArrivalVerdict.LATE
        }
    }

    fun onTimeDeparture(targetDepartAt: LocalDateTime, departedAt: LocalDateTime?): Boolean =
        departedAt != null && !departedAt.toMinute().isAfter(targetDepartAt.toMinute())

    /** 알람 후 5분 안에 퀴즈를 다 풀고, 잠이 충분했고, 재확인을 통과했으면 성공. */
    fun wakeSuccess(day: DayRecord): Boolean {
        val rang = day.alarmRangAt?.toMinute() ?: return false
        val solved = day.quizSolvedAt?.toMinute() ?: return false
        if (solved.isBefore(rang)) return false
        return Duration.between(rang, solved) <= Duration.ofMinutes(WAKE_LIMIT_MINUTES) &&
            day.sleptEnough == true &&
            day.recheckPassed == true
    }
}

internal fun LocalDateTime.toMinute(): LocalDateTime = truncatedTo(ChronoUnit.MINUTES)
