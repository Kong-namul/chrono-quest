package com.chronoquest.rules.model

/** 룰북 01 티어 표. [title]은 티어 대표 칭호다. */
enum class Tier(val minXp: Int, val title: String) {
    BRONZE(0, "시간의 개척자"),
    SILVER(100, "시간의 수련자"),
    GOLD(300, "시간의 정복자"),
    PLATINUM(700, "시간의 설계자"),
    DIAMOND(1500, "시간의 초월자"),
    MASTER(3000, "시간의 지배자"),
    LEGEND(6000, "시간의 전설"),
    MYTHIC(10000, "시간의 신");

    val next: Tier? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun of(xp: Int): Tier = entries.last { xp >= it.minXp }
    }
}
