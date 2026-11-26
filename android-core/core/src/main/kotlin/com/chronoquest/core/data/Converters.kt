package com.chronoquest.core.data

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 시각은 로컬 벽시계 시각으로 저장한다(타임존 변환 없음, 인계 메모 A2).
 * 고정 길이 형식이라 문자열 비교 순서와 시간 순서가 같다.
 */
class Converters {
    @TypeConverter
    fun fromDate(value: LocalDate?): String? = value?.format(DATE)

    @TypeConverter
    fun toDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it, DATE) }

    @TypeConverter
    fun fromDateTime(value: LocalDateTime?): String? = value?.truncatedTo(ChronoUnit.SECONDS)?.format(DATE_TIME)

    @TypeConverter
    fun toDateTime(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it, DATE_TIME) }

    @TypeConverter
    fun fromTime(value: LocalTime?): String? = value?.truncatedTo(ChronoUnit.MINUTES)?.format(TIME)

    @TypeConverter
    fun toTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it, TIME) }

    companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
