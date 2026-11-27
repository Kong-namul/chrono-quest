package com.chronoquest.app.codex

import com.chronoquest.rules.titles.Series
import com.chronoquest.rules.titles.SeriesKind
import kotlin.test.assertEquals
import org.junit.Test

class CodexLabelsTest {
    @Test
    fun `계열 이름과 조건 문구`() {
        assertEquals(
            listOf("누적 도착", "연속 도착", "정시 출발", "조기 도착", "기상 성공", "행동통제", "누적 완벽 주간", "연속 완벽 주간", "복귀", "월간 정복"),
            Series.entries.map { CodexLabels.info(it).name },
        )
        assertEquals("도착 5회마다", CodexLabels.info(Series.ARRIVE_TOTAL).condition(5))
        assertEquals("연속 도착 3회", CodexLabels.info(Series.ARRIVE_STREAK).condition(3))
        assertEquals("연속 완벽 주간 4주", CodexLabels.info(Series.PERFECT_WEEK_STREAK).condition(4))
        assertEquals("완벽한 달 2회마다", CodexLabels.info(Series.PERFECT_MONTH_TOTAL).condition(2))
    }

    @Test
    fun `누적 계열은 다음 배수까지 남은 횟수를 보여준다`() {
        assertEquals("앞으로 5회", CodexLabels.progress(SeriesKind.CUMULATIVE, threshold = 5, value = 0, owned = 0, unit = "회"))
        assertEquals("보유 1개 · 다음까지 3회", CodexLabels.progress(SeriesKind.CUMULATIVE, threshold = 5, value = 7, owned = 1, unit = "회"))
        assertEquals("보유 1개 · 다음까지 1회", CodexLabels.progress(SeriesKind.CUMULATIVE, threshold = 1, value = 1, owned = 1, unit = "회"))
    }

    @Test
    fun `연속 계열은 이번 연속에서 받았으면 그렇게 보여준다`() {
        assertEquals("앞으로 3회", CodexLabels.progress(SeriesKind.STREAK, threshold = 5, value = 2, owned = 0, unit = "회"))
        assertEquals("보유 2개 · 다음까지 3주", CodexLabels.progress(SeriesKind.STREAK, threshold = 5, value = 2, owned = 2, unit = "주"))
        assertEquals("보유 1개 · 이번 연속에서 받음", CodexLabels.progress(SeriesKind.STREAK, threshold = 5, value = 6, owned = 1, unit = "회"))
    }
}
