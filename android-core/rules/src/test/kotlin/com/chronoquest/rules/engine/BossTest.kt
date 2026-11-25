package com.chronoquest.rules.engine

import com.chronoquest.rules.arrived
import com.chronoquest.rules.at
import com.chronoquest.rules.day
import com.chronoquest.rules.ledger
import com.chronoquest.rules.onTime
import com.chronoquest.rules.onTimeAll
import com.chronoquest.rules.other
import com.chronoquest.rules.wakeDay
import com.chronoquest.rules.weekdays
import com.chronoquest.rules.work
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BossTest {
    private val engine = Engine()
    private val week1 = weekdays("2026-11-30", "2026-12-04").map { work(it) }
    private val afterWeek1 = at("2026-12-07", "00:00")

    @Test
    fun `출근 정시와 정시 출발로 HP 이상 때리면 처치`() {
        val s = engine.compute(
            ledger(week1, week1.map { it.arrived("08:55", departed = "08:15") }),
            afterWeek1,
        )
        val w = s.boss.history.single()
        assertEquals("snooze_slime", w.boss.id)
        assertEquals(500, w.maxHp)
        assertEquals(600, w.damage)
        assertEquals(BossStatus.DEFEATED, w.status)
        assertEquals(mapOf("snooze_slime" to 1), s.boss.defeats)
        assertNull(s.boss.current)
        assertTrue(s.grants.any { it.key == "boss:2026-11-30:result" && it.detail == "DEFEATED:snooze_slime" })
    }

    @Test
    fun `지각이 하나 있어도 다른 퀘스트로 만회할 수 있다`() {
        val withDeparture = week1.mapIndexed { i, q ->
            if (i == 0) q.arrived("09:30", departed = "08:15") else q.arrived("08:55", departed = "08:15")
        }
        val withoutDeparture = week1.mapIndexed { i, q ->
            if (i == 0) q.arrived("09:30") else q.arrived("08:55")
        }
        assertEquals(
            BossStatus.DEFEATED,
            engine.compute(ledger(week1, withDeparture), afterWeek1).boss.history.single().status,
        )
        assertEquals(
            BossStatus.ESCAPED,
            engine.compute(ledger(week1, withoutDeparture), afterWeek1).boss.history.single().status,
        )
    }

    @Test
    fun `주가 진행 중이면 현재 보스가 활성 상태`() {
        val rs = listOf(week1[0].arrived("08:55"), week1[1].arrived("08:55"))
        val s = engine.compute(ledger(week1, rs), at("2026-12-02", "12:00"))
        val current = assertNotNull(s.boss.current)
        assertEquals(BossStatus.ACTIVE, current.status)
        assertEquals(200, current.damage)
    }

    @Test
    fun `끝난 주에 보류가 있으면 결과도 보류`() {
        val s = engine.compute(ledger(week1, onTimeAll(week1.drop(1))), afterWeek1)
        assertEquals(BossStatus.PENDING, s.boss.history.single().status)
        assertTrue(s.grants.none { it.type == GrantType.BOSS_RESULT })
    }

    @Test
    fun `휴가로 빠진 출근만큼 HP가 줄어든다`() {
        val s = engine.compute(
            ledger(week1, onTimeAll(week1), vacations = setOf(day("2026-12-02"))),
            afterWeek1,
        )
        assertEquals(400, s.boss.history.single().maxHp)
    }

    @Test
    fun `다른 일정 조기 기상 행동통제도 데미지를 준다`() {
        val w = work("2026-11-30")
        val o = other("2026-11-30")
        val s = engine.compute(
            ledger(
                listOf(w, o),
                listOf(w.arrived("08:45"), o.onTime()),
                days = listOf(wakeDay("2026-11-30", selfControl = true)),
            ),
            afterWeek1,
        )
        val b = s.boss.history.single()
        assertEquals(100, b.maxHp)
        assertEquals(100 + 30 + 50 + 10 + 10, b.damage)
    }

    @Test
    fun `출근 없는 주는 보스가 없고 순서도 넘어가지 않는다`() {
        val o = other("2026-11-30")
        val w = work("2026-12-07")
        val s = engine.compute(ledger(listOf(o, w), listOf(o.onTime(), w.onTime())), at("2026-12-14", "00:00"))
        assertEquals(listOf("snooze_slime"), s.boss.history.map { it.boss.id })
        assertEquals(day("2026-12-07"), s.boss.history.single().weekStart)
    }

    @Test
    fun `보스는 로스터 순서대로 돈다`() {
        val qs = listOf(work("2026-11-30"), work("2026-12-07"))
        val s = engine.compute(ledger(qs, onTimeAll(qs)), at("2026-12-14", "00:00"))
        assertEquals(listOf("snooze_slime", "five_more_golem"), s.boss.history.map { it.boss.id })
    }

    @Test
    fun `시즌 첫 주에도 남은 출근으로 보스가 나온다`() {
        val q = work("2026-11-26")
        val s = engine.compute(ledger(listOf(q), listOf(q.onTime())), at("2026-11-30", "00:00"))
        val b = s.boss.history.single()
        assertEquals(day("2026-11-23"), b.weekStart)
        assertEquals(100, b.maxHp)
        assertEquals(BossStatus.DEFEATED, b.status)
    }
}
