package com.chronoquest.rules.judge

import com.chronoquest.rules.at
import com.chronoquest.rules.day
import com.chronoquest.rules.model.DayRecord
import com.chronoquest.rules.model.Resolution
import com.chronoquest.rules.wakeDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JudgesTest {
    private val due = at("2026-11-25", "09:00")

    private fun judge(time: String, safe: Boolean? = true): ArrivalVerdict? =
        Judges.arrival(due, at("2026-11-25", time), safe, Resolution.NONE)

    @Test
    fun `예정 10분 전 이하는 조기`() {
        assertEquals(ArrivalVerdict.EARLY, judge("07:00"))
        assertEquals(ArrivalVerdict.EARLY, judge("08:50"))
        assertEquals(ArrivalVerdict.EARLY, judge("08:50:59"))
    }

    @Test
    fun `예정 시각까지는 정시이고 초는 버린다`() {
        assertEquals(ArrivalVerdict.ON_TIME, judge("08:51"))
        assertEquals(ArrivalVerdict.ON_TIME, judge("09:00"))
        assertEquals(ArrivalVerdict.ON_TIME, judge("09:00:59"))
    }

    @Test
    fun `5분 유예 안은 아슬아슬`() {
        assertEquals(ArrivalVerdict.CLOSE, judge("09:01"))
        assertEquals(ArrivalVerdict.CLOSE, judge("09:05:59"))
    }

    @Test
    fun `유예를 넘기면 지각`() {
        assertEquals(ArrivalVerdict.LATE, judge("09:06"))
    }

    @Test
    fun `위험 이동은 시간과 관계없이 지각`() {
        assertEquals(ArrivalVerdict.LATE, judge("08:30", safe = false))
    }

    @Test
    fun `안전 여부나 도착 시각이 없으면 판정 보류`() {
        assertNull(judge("08:55", safe = null))
        assertNull(Judges.arrival(due, null, true, Resolution.NONE))
    }

    @Test
    fun `기록 없음으로 정리하면 지각`() {
        assertEquals(ArrivalVerdict.LATE, Judges.arrival(due, null, null, Resolution.NO_RECORD))
    }

    @Test
    fun `조기와 정시만 정시로 친다`() {
        assertTrue(ArrivalVerdict.EARLY.isOnTime)
        assertTrue(ArrivalVerdict.ON_TIME.isOnTime)
        assertFalse(ArrivalVerdict.CLOSE.isOnTime)
        assertFalse(ArrivalVerdict.LATE.isOnTime)
    }

    @Test
    fun `목표 출발 시각까지 나가면 정시 출발`() {
        val target = at("2026-11-25", "08:20")
        assertTrue(Judges.onTimeDeparture(target, at("2026-11-25", "08:20:59")))
        assertFalse(Judges.onTimeDeparture(target, at("2026-11-25", "08:21")))
        assertFalse(Judges.onTimeDeparture(target, null))
    }

    @Test
    fun `알람 후 5분 안에 풀고 잠 충분 재확인 통과면 기상 성공`() {
        assertTrue(Judges.wakeSuccess(wakeDay("2026-11-25", solved = "07:05:59")))
        assertFalse(Judges.wakeSuccess(wakeDay("2026-11-25", solved = "07:06")))
        assertFalse(Judges.wakeSuccess(wakeDay("2026-11-25", slept = false)))
        assertFalse(Judges.wakeSuccess(wakeDay("2026-11-25", recheck = false)))
        assertFalse(Judges.wakeSuccess(DayRecord(date = day("2026-11-25"))))
    }
}
