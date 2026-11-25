package com.chronoquest.rules.engine

import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.model.Quest
import com.chronoquest.rules.model.QuestKind
import java.time.LocalDate
import java.time.LocalDateTime

data class BossDef(val id: String, val name: String)

enum class BossStatus { ACTIVE, DEFEATED, ESCAPED, PENDING }

data class BossWeek(
    val weekStart: LocalDate,
    val boss: BossDef,
    val maxHp: Int,
    val damage: Int,
    val status: BossStatus,
)

data class BossState(
    val current: BossWeek?,
    val history: List<BossWeek>,
    val defeats: Map<String, Int>,
)

/** 주간 보스 (스펙 5.9). XP를 주지 않는 수집용 레이어다. */
internal object Bosses {
    val ROSTER = listOf(
        BossDef("snooze_slime", "스누즈 슬라임"),
        BossDef("five_more_golem", "5분만 더 골렘"),
        BossDef("blanket_dragon", "이불 드래곤"),
        BossDef("traffic_worm", "교통체증 지렁이"),
        BossDef("elevator_ghost", "엘리베이터 유령"),
        BossDef("sock_fairy", "양말 실종 요정"),
        BossDef("signal_octopus", "신호등 문어"),
        BossDef("tardy_king", "지각마왕"),
    )

    const val HP_PER_WORK = 100
    const val DMG_WORK = 100
    const val DMG_OTHER = 50
    const val DMG_EARLY = 30
    const val DMG_DEPART = 20
    const val DMG_WAKE = 10
    const val DMG_SELF_CONTROL = 10

    fun compute(
        quests: List<Quest>,
        outcomes: Map<String, QuestOutcome>,
        wakeDays: Set<LocalDate>,
        selfControlDays: Set<LocalDate>,
        seasonStart: LocalDate,
        now: LocalDateTime,
    ): BossState {
        val nowWeek = Periods.weekStart(now.toLocalDate())
        val byWeek = quests.groupBy { Periods.weekStart(it.date) }
        val history = mutableListOf<BossWeek>()
        var index = 0
        val weeks = generateSequence(Periods.weekStart(seasonStart)) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(nowWeek) }
        for (week in weeks) {
            val weekQuests = byWeek[week].orEmpty()
            val workCount = weekQuests.count { it.kind == QuestKind.WORK }
            if (workCount == 0) continue
            val boss = ROSTER[index % ROSTER.size]
            index += 1
            val maxHp = workCount * HP_PER_WORK
            var damage = 0
            for (q in weekQuests) {
                val o = outcomes.getValue(q.id)
                if (o.verdict?.isOnTime == true) {
                    damage += if (q.kind == QuestKind.WORK) DMG_WORK else DMG_OTHER
                    if (o.verdict == ArrivalVerdict.EARLY) damage += DMG_EARLY
                }
                if (o.onTimeDeparture) damage += DMG_DEPART
            }
            val days = (0L..6L).map { week.plusDays(it) }
            damage += days.count { it in wakeDays } * DMG_WAKE
            damage += days.count { it in selfControlDays } * DMG_SELF_CONTROL
            val ended = !week.plusDays(7).atStartOfDay().isAfter(now)
            val status = when {
                !ended -> BossStatus.ACTIVE
                weekQuests.any { outcomes.getValue(it.id).verdict == null } -> BossStatus.PENDING
                damage >= maxHp -> BossStatus.DEFEATED
                else -> BossStatus.ESCAPED
            }
            history += BossWeek(week, boss, maxHp, damage, status)
        }
        val current = history.lastOrNull()?.takeIf { it.weekStart == nowWeek }
        val defeats = history
            .filter { it.status == BossStatus.DEFEATED }
            .groupingBy { it.boss.id }
            .eachCount()
        return BossState(current = current, history = history.toList(), defeats = defeats)
    }

    fun resultGrants(state: BossState): List<Grant> =
        state.history
            .filter { it.status == BossStatus.DEFEATED || it.status == BossStatus.ESCAPED }
            .map {
                Grant(
                    key = "boss:${it.weekStart}:result",
                    source = "boss:${it.weekStart}",
                    at = it.weekStart.plusDays(7).atStartOfDay(),
                    type = GrantType.BOSS_RESULT,
                    detail = "${it.status}:${it.boss.id}",
                )
            }
}
