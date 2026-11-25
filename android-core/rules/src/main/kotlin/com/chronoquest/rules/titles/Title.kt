package com.chronoquest.rules.titles

enum class SeriesKind { CUMULATIVE, STREAK }

/** 칭호 계열 10종. 이름은 titles.json의 key와 같다. */
enum class Series(val kind: SeriesKind) {
    ARRIVE_TOTAL(SeriesKind.CUMULATIVE),
    ARRIVE_STREAK(SeriesKind.STREAK),
    DEPART_TOTAL(SeriesKind.CUMULATIVE),
    EARLY_TOTAL(SeriesKind.CUMULATIVE),
    WAKE_TOTAL(SeriesKind.CUMULATIVE),
    SELF_CONTROL_TOTAL(SeriesKind.CUMULATIVE),
    PERFECT_WEEK_TOTAL(SeriesKind.CUMULATIVE),
    PERFECT_WEEK_STREAK(SeriesKind.STREAK),
    COMEBACK_TOTAL(SeriesKind.CUMULATIVE),
    PERFECT_MONTH_TOTAL(SeriesKind.CUMULATIVE),
}

data class Title(
    val id: Int,
    val series: Series,
    val threshold: Int,
    val name: String,
)
