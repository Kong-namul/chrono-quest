package com.chronoquest.rules.engine

import com.chronoquest.rules.arrived
import com.chronoquest.rules.at
import com.chronoquest.rules.day
import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.ledger
import com.chronoquest.rules.model.QuestStatus
import com.chronoquest.rules.model.Tier
import com.chronoquest.rules.noRecord
import com.chronoquest.rules.onTime
import com.chronoquest.rules.other
import com.chronoquest.rules.wakeDay
import com.chronoquest.rules.work
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineCoreTest {
    private val engine = Engine()
    private val now = at("2026-11-29", "23:00")

    @Test
    fun `첫 미션 안전한 정시 도착 1회면 최초의 각성과 운명의 첫걸음`() {
        val q = work("2026-11-25")
        val s = engine.compute(ledger(listOf(q), listOf(q.arrived("08:55"))), now)
        assertEquals(20, s.xp)
        assertEquals(Tier.BRONZE, s.tier)
        assertEquals(100, s.nextTierXp)
        assertEquals(1, s.inventory[1])
        assertEquals(1, s.inventory[21])
        assertEquals(2, s.codexCount)
        assertEquals(Streak(1, 1), s.arriveStreak)
        assertEquals(ArrivalVerdict.ON_TIME, s.outcomes.getValue(q.id).verdict)
        assertTrue(s.grants.any { it.key == "title:1:1" && it.source == "arr:${q.id}" && it.titleId == 1 })
    }

    @Test
    fun `10분 이상 일찍 오면 조기 보너스와 조기 칭호`() {
        val q = work("2026-11-25")
        val s = engine.compute(ledger(listOf(q), listOf(q.arrived("08:50"))), now)
        assertEquals(30, s.xp)
        assertEquals(1, s.counters.early)
        assertEquals(1, s.inventory[61])
    }

    @Test
    fun `아슬아슬 도착은 XP 없이 연속을 유지한다`() {
        val qs = listOf(work("2026-11-25"), work("2026-11-26"), work("2026-11-27"))
        val rs = listOf(qs[0].arrived("08:55"), qs[1].arrived("09:03"), qs[2].arrived("08:55"))
        val s = engine.compute(ledger(qs, rs), now)
        assertEquals(40, s.xp)
        assertEquals(Streak(2, 2), s.arriveStreak)
        assertEquals(1, s.inventory[22])
        assertEquals(ArrivalVerdict.CLOSE, s.outcomes.getValue(qs[1].id).verdict)
    }

    @Test
    fun `지각하면 연속이 끊기고 다시 쌓으면 연속 칭호를 또 준다`() {
        val qs = listOf(work("2026-11-25"), work("2026-11-26"), work("2026-11-27"))
        val rs = listOf(qs[0].arrived("08:55"), qs[1].arrived("09:10"), qs[2].arrived("08:55"))
        val s = engine.compute(ledger(qs, rs), now)
        assertEquals(Streak(1, 1), s.arriveStreak)
        assertEquals(2, s.inventory[21])
        assertNull(s.inventory[22])
    }

    @Test
    fun `위험하게 왔으면 제시간이어도 지각이다`() {
        val q = work("2026-11-25")
        val s = engine.compute(ledger(listOf(q), listOf(q.arrived("08:55", safe = false))), now)
        assertEquals(ArrivalVerdict.LATE, s.outcomes.getValue(q.id).verdict)
        assertEquals(0, s.xp)
        assertTrue(s.inventory.isEmpty())
    }

    @Test
    fun `기록 없음으로 정리하면 지각이고 보류가 아니다`() {
        val q = work("2026-11-25")
        val s = engine.compute(ledger(listOf(q), listOf(q.noRecord())), now)
        assertEquals(ArrivalVerdict.LATE, s.outcomes.getValue(q.id).verdict)
        assertTrue(s.pendingQuestIds.isEmpty())
    }

    @Test
    fun `예정이 지났는데 기록이 없으면 보류이고 미래 일정은 보류가 아니다`() {
        val past = work("2026-11-25")
        val future = work("2026-11-30")
        val s = engine.compute(ledger(listOf(past, future)), now)
        assertEquals(listOf(past.id), s.pendingQuestIds)
        assertNull(s.outcomes.getValue(future.id).verdict)
    }

    @Test
    fun `누적 칭호는 배수마다 지급되고 연속 칭호는 단계마다 한 번`() {
        val qs = listOf("2026-11-25", "2026-11-26", "2026-11-27", "2026-11-28", "2026-11-29")
            .flatMap { d -> listOf(other(d, "10:00"), other(d, "15:00")) }
        val s = engine.compute(ledger(qs, qs.map { it.onTime() }), now)
        assertEquals(200, s.xp)
        assertEquals(Tier.SILVER, s.tier)
        assertEquals(
            mapOf(1 to 10, 2 to 5, 3 to 3, 4 to 2, 5 to 2, 6 to 1, 7 to 1, 8 to 1, 9 to 1, 10 to 1),
            s.inventory.filterKeys { it in 1..20 },
        )
        assertEquals((21..30).associateWith { 1 }, s.inventory.filterKeys { it in 21..40 })
    }

    @Test
    fun `목표 시각까지 출발하면 정시 출발 보너스`() {
        val q1 = work("2026-11-25")
        val q2 = work("2026-11-26")
        val rs = listOf(q1.arrived("08:55", departed = "08:30"), q2.arrived("08:55", departed = "08:31"))
        val s = engine.compute(ledger(listOf(q1, q2), rs), now)
        assertEquals(20 + 10 + 20, s.xp)
        assertEquals(1, s.counters.depart)
        assertEquals(1, s.inventory[41])
        assertTrue(s.outcomes.getValue(q1.id).onTimeDeparture)
        assertFalse(s.outcomes.getValue(q2.id).onTimeDeparture)
    }

    @Test
    fun `알람 후 5분 안에 풀고 재확인까지 통과한 날만 기상 성공`() {
        val days = listOf(
            wakeDay("2026-11-25"),
            wakeDay("2026-11-26", solved = "07:06"),
            wakeDay("2026-11-27", recheck = false),
        )
        val s = engine.compute(ledger(emptyList(), days = days), now)
        assertEquals(5, s.xp)
        assertEquals(1, s.counters.wake)
        assertEquals(1, s.inventory[81])
    }

    @Test
    fun `행동통제는 기상과 별개로 하루 한 번 5 XP`() {
        val days = listOf(wakeDay("2026-11-25", solved = "07:30", selfControl = true))
        val s = engine.compute(ledger(emptyList(), days = days), now)
        assertEquals(5, s.xp)
        assertEquals(0, s.counters.wake)
        assertEquals(1, s.counters.selfControl)
        assertEquals(1, s.inventory[101])
    }

    @Test
    fun `취소 휴가 시즌 전 일정은 판정에서 빠진다`() {
        val canceled = work("2026-11-25").copy(status = QuestStatus.CANCELED)
        val onVacation = work("2026-11-26")
        val otherOnVacation = other("2026-11-26")
        val beforeSeason = work("2026-11-24")
        val quests = listOf(canceled, onVacation, otherOnVacation, beforeSeason)
        val s = engine.compute(
            ledger(quests, quests.map { it.onTime() }, vacations = setOf(day("2026-11-26"))),
            now,
        )
        assertEquals(20, s.xp)
        assertEquals(setOf(otherOnVacation.id), s.outcomes.keys)
    }
}
