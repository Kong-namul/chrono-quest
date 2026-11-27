package com.chronoquest.app.schedule

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** DatePicker는 UTC 자정 기준 밀리초를 쓴다. */
internal fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** 오늘부터만 고를 수 있다. 오늘 이전은 고를 수 없다(스펙 D13). */
@OptIn(ExperimentalMaterial3Api::class)
internal class FromToday(private val today: LocalDate) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= today.toUtcMillis()

    override fun isSelectableYear(year: Int): Boolean = year >= today.year
}
