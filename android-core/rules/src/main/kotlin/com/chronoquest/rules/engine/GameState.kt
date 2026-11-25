package com.chronoquest.rules.engine

import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.model.Tier
import java.time.LocalDateTime

enum class GrantType {
    XP_ARRIVE, XP_EARLY, XP_DEPART, XP_WAKE, XP_SELF_CONTROL,
    XP_PERFECT_WEEK, XP_THREE_WEEKS, XP_PERFECT_MONTH,
    TITLE, PET_EVOLVED, BOSS_RESULT,
}

/**
 * 지급 내역 한 줄. [key]는 같은 원장이면 항상 같은 값이라
 * 연출을 이미 보여줬는지(CelebrationSeen) 비교하는 데 쓴다.
 */
data class Grant(
    val key: String,
    val at: LocalDateTime,
    val type: GrantType,
    val xp: Int = 0,
    val titleId: Int? = null,
    val detail: String = "",
)

data class Streak(val current: Int = 0, val best: Int = 0) {
    fun advance(): Streak = Streak(current + 1, maxOf(best, current + 1))
    fun reset(): Streak = Streak(0, best)
}

data class Counters(
    val arrive: Int = 0,
    val depart: Int = 0,
    val early: Int = 0,
    val wake: Int = 0,
    val selfControl: Int = 0,
    val perfectWeeks: Int = 0,
    val comebacks: Int = 0,
    val perfectMonths: Int = 0,
)

data class QuestOutcome(
    val questId: String,
    val verdict: ArrivalVerdict?,
    val onTimeDeparture: Boolean,
)

data class GameState(
    val xp: Int,
    val tier: Tier,
    val nextTierXp: Int?,
    val counters: Counters,
    val arriveStreak: Streak,
    val perfectWeekStreak: Streak,
    val inventory: Map<Int, Int>,
    val codexCount: Int,
    val outcomes: Map<String, QuestOutcome>,
    val pendingQuestIds: List<String>,
    val pet: PetState,
    val grants: List<Grant>,
)
