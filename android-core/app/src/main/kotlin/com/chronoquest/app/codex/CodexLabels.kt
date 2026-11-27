package com.chronoquest.app.codex

import com.chronoquest.rules.engine.GameState
import com.chronoquest.rules.titles.Series
import com.chronoquest.rules.titles.SeriesKind

/** 계열 이름, 조건 문구, 남은 횟수의 단위. 계열 이름은 스펙 5.4 표를 따른다. */
data class SeriesInfo(val name: String, val condition: (Int) -> String, val unit: String)

/** 칭호 탭 문구(스펙 5.4). */
object CodexLabels {
    fun info(series: Series): SeriesInfo = when (series) {
        Series.ARRIVE_TOTAL -> SeriesInfo("누적 도착", { "도착 ${it}회마다" }, "회")
        Series.ARRIVE_STREAK -> SeriesInfo("연속 도착", { "연속 도착 ${it}회" }, "회")
        Series.DEPART_TOTAL -> SeriesInfo("정시 출발", { "정시 출발 ${it}회마다" }, "회")
        Series.EARLY_TOTAL -> SeriesInfo("조기 도착", { "조기 도착 ${it}회마다" }, "회")
        Series.WAKE_TOTAL -> SeriesInfo("기상 성공", { "기상 성공 ${it}회마다" }, "회")
        Series.SELF_CONTROL_TOTAL -> SeriesInfo("행동통제", { "행동통제 ${it}회마다" }, "회")
        Series.PERFECT_WEEK_TOTAL -> SeriesInfo("누적 완벽 주간", { "완벽 주간 ${it}회마다" }, "회")
        Series.PERFECT_WEEK_STREAK -> SeriesInfo("연속 완벽 주간", { "연속 완벽 주간 ${it}주" }, "주")
        Series.COMEBACK_TOTAL -> SeriesInfo("복귀", { "복귀 ${it}회마다" }, "회")
        Series.PERFECT_MONTH_TOTAL -> SeriesInfo("월간 정복", { "완벽한 달 ${it}회마다" }, "회")
    }

    /** 계열의 지금 카운터 값. 연속 계열은 지금 이어지는 연속 값이다. */
    fun counter(series: Series, state: GameState): Int = when (series) {
        Series.ARRIVE_TOTAL -> state.counters.arrive
        Series.ARRIVE_STREAK -> state.arriveStreak.current
        Series.DEPART_TOTAL -> state.counters.depart
        Series.EARLY_TOTAL -> state.counters.early
        Series.WAKE_TOTAL -> state.counters.wake
        Series.SELF_CONTROL_TOTAL -> state.counters.selfControl
        Series.PERFECT_WEEK_TOTAL -> state.counters.perfectWeeks
        Series.PERFECT_WEEK_STREAK -> state.perfectWeekStreak.current
        Series.COMEBACK_TOTAL -> state.counters.comebacks
        Series.PERFECT_MONTH_TOTAL -> state.counters.perfectMonths
    }

    /**
     * 보유 수와 다음 획득까지 남은 정도.
     * - 누적 계열은 다음 배수까지 남은 횟수를 쓴다.
     * - 연속 계열은 문턱값까지 남은 횟수를 쓴다. 이번 연속에서 이미 받았으면 그렇게 쓴다.
     */
    fun progress(kind: SeriesKind, threshold: Int, value: Int, owned: Int, unit: String): String {
        val next = when (kind) {
            SeriesKind.CUMULATIVE -> "${threshold - value % threshold}$unit"
            SeriesKind.STREAK -> if (value < threshold) "${threshold - value}$unit" else null
        }
        return when {
            next == null -> if (owned > 0) "보유 ${owned}개 · 이번 연속에서 받음" else "이번 연속에서 받음"
            owned > 0 -> "보유 ${owned}개 · 다음까지 $next"
            else -> "앞으로 $next"
        }
    }
}
