package com.chronoquest.core.domain

import com.chronoquest.rules.model.QuestKind
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import org.junit.Test

class QuestIdsTest {
    @Test
    fun `날짜 종류 예정 시각으로 퀘스트 ID를 만든다`() {
        assertEquals(
            "2026-11-25-WORK-09:00",
            QuestIds.of(LocalDate.parse("2026-11-25"), QuestKind.WORK, LocalTime.parse("09:00")),
        )
        assertEquals(
            "2026-12-01-OTHER-19:30",
            QuestIds.of(LocalDate.parse("2026-12-01"), QuestKind.OTHER, LocalTime.parse("19:30")),
        )
    }

    @Test
    fun `예정 시각의 초는 버린다`() {
        assertEquals(
            "2026-11-25-WORK-09:00",
            QuestIds.of(LocalDate.parse("2026-11-25"), QuestKind.WORK, LocalTime.parse("09:00:45")),
        )
    }
}
