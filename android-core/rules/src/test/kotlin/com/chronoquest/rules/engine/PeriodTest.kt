package com.chronoquest.rules.engine

import com.chronoquest.rules.arrived
import com.chronoquest.rules.at
import com.chronoquest.rules.day
import com.chronoquest.rules.ledger
import com.chronoquest.rules.onTimeAll
import com.chronoquest.rules.weekdays
import com.chronoquest.rules.work
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PeriodTest {
    private val engine = Engine()
    private val week1 = weekdays("2026-11-30", "2026-12-04").map { work(it) }
    private val week2 = weekdays("2026-12-07", "2026-12-11").map { work(it) }
    private val week3 = weekdays("2026-12-14", "2026-12-18").map { work(it) }

    @Test
    fun `출근 전부 정시인 주는 완벽 주간`() {
        val s = engine.compute(ledger(week1, onTimeAll(week1)), at("2026-12-07", "00:00"))
        assertEquals(1, s.counters.perfectWeeks)
        assertEquals(Streak(1, 1), s.perfectWeekStreak)
        assertEquals(5 * 20 + 100, s.xp)
        assertEquals(1, s.inventory[121])
        assertEquals(1, s.inventory[141])
        assertTrue(s.grants.any { it.key == "week:2026-11-30:xp" && it.xp == 100 })
    }

    @Test
    fun `주가 끝나기 전에는 주간 보너스가 없다`() {
        val s = engine.compute(ledger(week1, onTimeAll(week1)), at("2026-12-06", "23:59"))
        assertEquals(0, s.counters.perfectWeeks)
        assertEquals(5 * 20, s.xp)
    }

    @Test
    fun `시즌 첫 불완전 주는 보너스에서 뺀다`() {
        val first = weekdays("2026-11-25", "2026-11-27").map { work(it) }
        val s = engine.compute(ledger(first, onTimeAll(first)), at("2026-11-30", "00:00"))
        assertEquals(0, s.counters.perfectWeeks)
        assertEquals(3 * 20, s.xp)
    }

    @Test
    fun `아슬아슬이 낀 주는 보너스 없이 연속 완벽 주간을 유지한다`() {
        val records = onTimeAll(week1) +
            week2.mapIndexed { i, q -> if (i == 2) q.arrived("09:03") else q.arrived("08:55") } +
            onTimeAll(week3)
        val s = engine.compute(ledger(week1 + week2 + week3, records), at("2026-12-21", "00:00"))
        assertEquals(2, s.counters.perfectWeeks)
        assertEquals(Streak(2, 2), s.perfectWeekStreak)
        assertEquals(2, s.inventory[121])
        assertEquals(1, s.inventory[122])
        assertEquals(1, s.inventory[142])
    }

    @Test
    fun `지각이 낀 주는 연속 완벽 주간을 끊는다`() {
        val records = onTimeAll(week1) +
            week2.mapIndexed { i, q -> if (i == 2) q.arrived("09:20") else q.arrived("08:55") } +
            onTimeAll(week3)
        val s = engine.compute(ledger(week1 + week2 + week3, records), at("2026-12-21", "00:00"))
        assertEquals(Streak(1, 1), s.perfectWeekStreak)
        assertEquals(2, s.inventory[141])
        assertNull(s.inventory[142])
    }

    @Test
    fun `출근이 없는 휴가 주는 건너뛰고 연속을 유지한다`() {
        val vacations = week2.map { it.date }.toSet()
        val s = engine.compute(
            ledger(week1 + week2 + week3, onTimeAll(week1 + week3), vacations = vacations),
            at("2026-12-21", "00:00"),
        )
        assertEquals(Streak(2, 2), s.perfectWeekStreak)
    }

    @Test
    fun `연속 완벽 주간 3주마다 300 XP`() {
        val all = week1 + week2 + week3
        val s = engine.compute(ledger(all, onTimeAll(all)), at("2026-12-21", "00:00"))
        assertEquals(15 * 20 + 3 * 100 + 300, s.xp)
        assertTrue(s.grants.any { it.key == "week:2026-12-14:3w" && it.xp == 300 })
        assertEquals(1, s.inventory[143])
    }

    @Test
    fun `보류 일정이 있는 주에서 주간 판정이 멈춘다`() {
        val records = onTimeAll(week1.drop(1)) + onTimeAll(week2)
        val s = engine.compute(ledger(week1 + week2, records), at("2026-12-14", "00:00"))
        assertEquals(0, s.counters.perfectWeeks)
        assertEquals(listOf(week1[0].id), s.pendingQuestIds)
        assertEquals(9 * 20, s.xp)
    }

    @Test
    fun `출근 전부 정시인 달은 완벽 월`() {
        val month = weekdays("2026-11-30", "2026-12-31").map { work(it) }
        val s = engine.compute(ledger(month, onTimeAll(month)), at("2027-01-01", "00:00"))
        assertEquals(1, s.counters.perfectMonths)
        assertEquals(1, s.inventory[181])
        assertTrue(s.grants.any { it.key == "month:2026-12:xp" && it.xp == 500 })
    }

    @Test
    fun `아슬아슬이 낀 달은 완벽 월이 아니다`() {
        val month = weekdays("2026-11-30", "2026-12-31").map { work(it) }
        val records = month.map { if (it.date == day("2026-12-15")) it.arrived("09:03") else it.arrived("08:55") }
        val s = engine.compute(ledger(month, records), at("2027-01-01", "00:00"))
        assertEquals(0, s.counters.perfectMonths)
        assertNull(s.inventory[181])
    }
}
