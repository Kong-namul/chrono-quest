package com.chronoquest.rules.engine

import com.chronoquest.rules.arrived
import com.chronoquest.rules.at
import com.chronoquest.rules.dailyOthers
import com.chronoquest.rules.early
import com.chronoquest.rules.ledger
import com.chronoquest.rules.onTime
import com.chronoquest.rules.onTimeAll
import com.chronoquest.rules.wakeDay
import com.chronoquest.rules.weekdays
import com.chronoquest.rules.work
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrantKeyTest {
    private val engine = Engine()

    @Test
    fun `지급 키와 출처 형식`() {
        val month = weekdays("2026-11-30", "2026-12-31").map { work(it) }
        val first = month.first()
        val second = month[1]
        val records = listOf(first.arrived("08:45", departed = "08:25")) +
            listOf(second.early()) + onTimeAll(month.drop(2))
        val days = listOf(wakeDay("2026-11-30", selfControl = true))
        val s = engine.compute(ledger(month, records, days), at("2027-01-01", "00:00"))

        fun has(key: String, source: String) =
            assertTrue(s.grants.any { it.key == key && it.source == source }, "missing $key / $source in ${s.grants.map { it.key to it.source }}")

        has("title:1:1", "arr:${first.id}")
        has("arr:${first.id}:xp", "arr:${first.id}")
        has("arr:${second.id}:early", "arr:${second.id}")
        has("dep:${first.id}:xp", "dep:${first.id}")
        has("wake:2026-11-30:xp", "wake:2026-11-30")
        has("self:2026-11-30:xp", "self:2026-11-30")
        has("week:2026-11-30:xp", "week:2026-11-30")
        has("bonus:3w:1", "week:2026-12-14")
        has("month:2026-12:xp", "month:2026-12")
        has("pet:evolve:BABY", "pet")
        s.grants.first { it.type == GrantType.BOSS_RESULT }.let {
            assertEquals("boss:2026-11-30:result", it.key)
            assertEquals("boss:2026-11-30", it.source)
        }
        assertEquals(s.grants.size, s.grants.map { it.key }.toSet().size)
    }

    @Test
    fun `앞 기록이 끼어들어도 새로 생긴 칭호 키만 늘어난다`() {
        val qs = dailyOthers("2026-11-25", 6)
        val others = qs.drop(1).map { it.onTime() }
        val now = at("2026-12-31", "23:00")
        val before = engine.compute(ledger(qs, others), now)
        val after = engine.compute(ledger(qs, listOf(qs.first().onTime()) + others), now)

        fun titleKeys(s: GameState) = s.grants.filter { it.type == GrantType.TITLE }.map { it.key }.toSet()
        val expectedNew = after.inventory.flatMap { (id, qty) ->
            ((before.inventory[id] ?: 0) + 1..qty).map { "title:$id:$it" }
        }.toSet()

        assertTrue(expectedNew.isNotEmpty())
        assertEquals(expectedNew, titleKeys(after) - titleKeys(before))
        assertTrue((titleKeys(before) - titleKeys(after)).isEmpty())
    }
}
