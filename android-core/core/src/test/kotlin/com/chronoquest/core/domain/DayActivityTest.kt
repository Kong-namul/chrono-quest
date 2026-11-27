package com.chronoquest.core.domain

import com.chronoquest.core.d
import com.chronoquest.core.data.DayRecordEntity
import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.core.dt
import com.chronoquest.rules.model.Resolution
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class DayActivityTest {
    @Test
    fun `출발·도착·기록 없음 정리 중 하나라도 있으면 기록이 있다`() {
        assertFalse(DayActivity.hasRecord(null))
        assertFalse(DayActivity.hasRecord(QuestRecordEntity(questId = "q")))
        assertTrue(DayActivity.hasRecord(QuestRecordEntity(questId = "q", departedAt = dt("2026-11-30", "08:20"))))
        assertTrue(DayActivity.hasRecord(QuestRecordEntity(questId = "q", arrivedAt = dt("2026-11-30", "08:55"), safe = true)))
        assertTrue(DayActivity.hasRecord(QuestRecordEntity(questId = "q", resolution = Resolution.NO_RECORD)))
    }

    @Test
    fun `알람만 울린 날은 활동한 날이 아니다`() {
        assertFalse(DayActivity.hasDayRecord(null))
        assertFalse(DayActivity.hasDayRecord(DayRecordEntity(date = d("2026-11-30"), alarmRangAt = dt("2026-11-30", "07:00"))))
        assertTrue(DayActivity.hasDayRecord(DayRecordEntity(date = d("2026-11-30"), selfControl = false)))
        assertTrue(DayActivity.hasDayRecord(DayRecordEntity(date = d("2026-11-30"), quizSolvedAt = dt("2026-11-30", "07:03"))))
        assertTrue(DayActivity.hasDayRecord(DayRecordEntity(date = d("2026-11-30"), sleptEnough = true)))
        assertTrue(DayActivity.hasDayRecord(DayRecordEntity(date = d("2026-11-30"), recheckPassed = false)))
    }
}
