package com.chronoquest.core.domain

import com.chronoquest.core.d
import com.chronoquest.core.data.DayRecordEntity
import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.core.dt
import com.chronoquest.core.questEntity
import com.chronoquest.rules.model.QuestKind
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class UpcomingTest {
    private val work = questEntity("2026-11-30")
    private val other = questEntity("2026-11-30", kind = QuestKind.OTHER, due = "19:00", depart = "18:30", title = "약속")
    private val next = questEntity("2026-12-01")
    private val old = questEntity("2026-11-29")

    @Test
    fun `오늘부터 7일을 날짜별로 묶고 기록과 휴가를 표시한다`() {
        val days = Upcoming.build(
            from = d("2026-11-30"),
            days = 7,
            quests = listOf(other, next, work, old),
            records = listOf(QuestRecordEntity(questId = work.id, departedAt = dt("2026-11-30", "08:20"))),
            dayRecords = emptyList(),
            vacations = setOf(d("2026-12-01")),
        )
        assertEquals(7, days.size)
        assertEquals(d("2026-11-30"), days[0].date)
        assertEquals(d("2026-12-06"), days[6].date)
        assertEquals(listOf(work.id, other.id), days[0].quests.map { it.quest.id })
        assertEquals(listOf(true, false), days[0].quests.map { it.hasRecord })
        assertTrue(days[0].hasActivity)
        assertTrue(days[0].hasWorkRecord)
        assertFalse(days[0].vacation)
        assertTrue(days[1].vacation)
        assertFalse(days[1].hasActivity)
        assertFalse(days[1].hasWorkRecord)
        assertTrue(days[6].quests.isEmpty())
        assertTrue(days.none { day -> day.quests.any { it.quest.id == old.id } })
    }

    @Test
    fun `출근 기록은 없고 다른 일정 기록만 있으면 활동은 있어도 출근 기록은 없다`() {
        val days = Upcoming.build(
            from = d("2026-11-30"),
            days = 1,
            quests = listOf(work, other),
            records = listOf(QuestRecordEntity(questId = other.id, arrivedAt = dt("2026-11-30", "18:55"), safe = true)),
            dayRecords = emptyList(),
            vacations = emptySet(),
        )
        assertTrue(days.single().hasActivity)
        assertFalse(days.single().hasWorkRecord)
    }

    @Test
    fun `하루 기록만 있어도 활동한 날이다`() {
        val days = Upcoming.build(
            from = d("2026-11-30"),
            days = 1,
            quests = emptyList(),
            records = emptyList(),
            dayRecords = listOf(DayRecordEntity(date = d("2026-11-30"), selfControl = true)),
            vacations = emptySet(),
        )
        assertTrue(days.single().hasActivity)
    }
}
