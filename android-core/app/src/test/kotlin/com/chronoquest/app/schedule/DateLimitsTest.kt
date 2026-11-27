package com.chronoquest.app.schedule

import androidx.compose.material3.ExperimentalMaterial3Api
import com.chronoquest.app.d
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class DateLimitsTest {
    private val today = d("2026-11-27")
    private val limit = FromToday(today)

    @Test
    fun `오늘은 고를 수 있다`() {
        assertTrue(limit.isSelectableDate(today.toUtcMillis()))
    }

    @Test
    fun `어제는 고를 수 없다`() {
        assertFalse(limit.isSelectableDate(today.minusDays(1).toUtcMillis()))
    }

    @Test
    fun `내일은 고를 수 있다`() {
        assertTrue(limit.isSelectableDate(today.plusDays(1).toUtcMillis()))
    }

    @Test
    fun `작년은 고를 수 없고 올해는 고를 수 있다`() {
        assertFalse(limit.isSelectableYear(today.year - 1))
        assertTrue(limit.isSelectableYear(today.year))
    }

    @Test
    fun `밀리초와 날짜가 서로 되돌아온다`() {
        for (date in listOf(d("2026-11-30"), d("2026-12-24"))) {
            assertEquals(date, date.toUtcMillis().toUtcDate())
        }
    }

    @Test
    fun `2026-11-30의 UTC 자정 밀리초`() {
        assertEquals(1795996800000L, d("2026-11-30").toUtcMillis())
    }
}
