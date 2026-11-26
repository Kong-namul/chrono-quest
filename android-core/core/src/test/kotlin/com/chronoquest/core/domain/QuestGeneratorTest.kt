package com.chronoquest.core.domain

import com.chronoquest.core.d
import com.chronoquest.core.dt
import com.chronoquest.core.data.QuestSource
import com.chronoquest.core.weekdayTemplate
import com.chronoquest.rules.model.QuestKind
import com.chronoquest.rules.model.QuestStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class QuestGeneratorTest {
    private val from = d("2026-11-25") // 수요일

    @Test
    fun `평일 템플릿은 7일 중 평일에만 일정을 만든다`() {
        val quests = QuestGenerator.generate(listOf(weekdayTemplate(id = 1)), from)
        assertEquals(
            listOf(
                "2026-11-25-WORK-09:00",
                "2026-11-26-WORK-09:00",
                "2026-11-27-WORK-09:00",
                "2026-11-30-WORK-09:00",
                "2026-12-01-WORK-09:00",
            ),
            quests.map { it.id },
        )
    }

    @Test
    fun `만든 일정은 템플릿 시각과 출처를 그대로 가진다`() {
        val q = QuestGenerator.generate(listOf(weekdayTemplate(id = 7, title = "회사")), from, days = 1).single()
        assertEquals(d("2026-11-25"), q.date)
        assertEquals(QuestKind.WORK, q.kind)
        assertEquals("회사", q.title)
        assertEquals(dt("2026-11-25", "09:00"), q.dueAt)
        assertEquals(dt("2026-11-25", "08:30"), q.targetDepartAt)
        assertEquals(QuestSource.TEMPLATE, q.source)
        assertEquals(7L, q.templateId)
        assertEquals(QuestStatus.SCHEDULED, q.status)
    }

    @Test
    fun `비활성이거나 잘못된 템플릿은 건너뛴다`() {
        val templates = listOf(
            weekdayTemplate(id = 1, active = false),
            weekdayTemplate(id = 2, depart = "09:30"),
        )
        assertTrue(QuestGenerator.generate(templates, from).isEmpty())
    }

    @Test
    fun `같은 날 같은 종류 같은 시각이면 하나만 남는다`() {
        val templates = listOf(weekdayTemplate(id = 1), weekdayTemplate(id = 2, title = "다른 이름"))
        val quests = QuestGenerator.generate(templates, from)
        assertEquals(5, quests.size)
        assertTrue(quests.all { it.templateId == 1L })
    }
}
