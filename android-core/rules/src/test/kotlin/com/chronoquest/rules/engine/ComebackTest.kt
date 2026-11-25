package com.chronoquest.rules.engine

import com.chronoquest.rules.at
import com.chronoquest.rules.judge.ArrivalVerdict.CLOSE
import com.chronoquest.rules.judge.ArrivalVerdict.EARLY
import com.chronoquest.rules.judge.ArrivalVerdict.LATE
import com.chronoquest.rules.judge.ArrivalVerdict.ON_TIME
import com.chronoquest.rules.late
import com.chronoquest.rules.ledger
import com.chronoquest.rules.onTime
import com.chronoquest.rules.other
import kotlin.test.Test
import kotlin.test.assertEquals

class ComebackTest {
    private fun run(vararg verdicts: com.chronoquest.rules.judge.ArrivalVerdict): List<Boolean> {
        val tracker = ComebackTracker()
        return verdicts.map { tracker.onArrival(it) }
    }

    @Test
    fun `지각 후 정시 3연속이면 복귀`() {
        assertEquals(listOf(false, false, false, true), run(LATE, ON_TIME, EARLY, ON_TIME))
    }

    @Test
    fun `아슬아슬은 복귀 카운트를 늘리지도 끊지도 않는다`() {
        assertEquals(listOf(false, false, false, false, true), run(LATE, ON_TIME, CLOSE, ON_TIME, ON_TIME))
    }

    @Test
    fun `복귀 대기 중 다시 지각하면 처음부터 센다`() {
        assertEquals(
            listOf(false, false, false, false, false, true),
            run(LATE, ON_TIME, LATE, ON_TIME, ON_TIME, ON_TIME),
        )
    }

    @Test
    fun `지각 없이 정시만 하면 복귀가 없다`() {
        assertEquals(listOf(false, false, false), run(ON_TIME, ON_TIME, ON_TIME))
    }

    @Test
    fun `지각 하나당 복귀는 한 번뿐이다`() {
        val results = run(LATE, ON_TIME, ON_TIME, ON_TIME, ON_TIME, ON_TIME, ON_TIME)
        assertEquals(1, results.count { it })
    }

    @Test
    fun `엔진이 복귀 횟수와 복귀 칭호를 지급한다`() {
        val qs = (0 until 4).map { other("2026-11-25", "%02d:00".format(10 + it)) }
        val rs = listOf(qs[0].late()) + qs.drop(1).map { it.onTime() }
        val s = Engine().compute(ledger(qs, rs), at("2026-11-29", "23:00"))
        assertEquals(1, s.counters.comebacks)
        assertEquals(1, s.inventory[161])
    }
}
