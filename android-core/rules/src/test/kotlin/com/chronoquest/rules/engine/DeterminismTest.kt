package com.chronoquest.rules.engine

import com.chronoquest.rules.at
import com.chronoquest.rules.close
import com.chronoquest.rules.dailyOthers
import com.chronoquest.rules.day
import com.chronoquest.rules.early
import com.chronoquest.rules.late
import com.chronoquest.rules.ledger
import com.chronoquest.rules.onTime
import com.chronoquest.rules.wakeDay
import com.chronoquest.rules.weekdays
import com.chronoquest.rules.work
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeterminismTest {
    private val engine = Engine()
    private val now = at("2027-01-01", "00:00")
    private val quests = weekdays("2026-11-25", "2026-12-31").map { work(it) } +
        dailyOthers("2026-11-25", 30, due = "19:00")
    private val records = quests.mapIndexed { i, q ->
        when (i % 7) {
            3 -> q.close()
            5 -> q.late()
            else -> if (i % 2 == 0) q.onTime() else q.early()
        }
    }
    private val days = (0L until 30L).map {
        wakeDay(day("2026-11-25").plusDays(it).toString(), selfControl = it % 3 == 0L)
    }

    @Test
    fun `입력 순서를 섞어도 결과가 같다`() {
        val base = engine.compute(ledger(quests, records, days), now)
        val random = Random(42)
        repeat(5) {
            val shuffled = ledger(quests.shuffled(random), records.shuffled(random), days.shuffled(random))
            assertEquals(base, engine.compute(shuffled, now))
        }
    }

    @Test
    fun `기록을 지우면 그 기록으로 받은 지급이 사라진다`() {
        val q = work("2026-11-25")
        val with = engine.compute(ledger(listOf(q), listOf(q.onTime())), now)
        val without = engine.compute(ledger(listOf(q), emptyList()), now)
        assertEquals(1, with.inventory[1])
        assertNull(without.inventory[1])
        assertEquals(0, without.xp)
        assertEquals(listOf(q.id), without.pendingQuestIds)
    }
}
