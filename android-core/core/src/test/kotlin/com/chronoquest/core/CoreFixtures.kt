package com.chronoquest.core

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.QuestSource
import com.chronoquest.core.data.ScheduleTemplateEntity
import com.chronoquest.core.domain.QuestIds
import com.chronoquest.rules.model.QuestKind
import com.chronoquest.rules.model.QuestStatus
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

/** 월~금 요일 마스크 (월 bit0 ~ 일 bit6). */
const val WEEKDAYS_MASK = 0b0011111

fun d(date: String): LocalDate = LocalDate.parse(date)

fun dt(date: String, time: String): LocalDateTime = LocalDateTime.parse("${date}T$time")

fun clockAt(date: String, time: String): Clock =
    Clock.fixed(dt(date, time).atZone(SEOUL).toInstant(), SEOUL)

fun questEntity(
    date: String,
    kind: QuestKind = QuestKind.WORK,
    due: String = "09:00",
    depart: String = "08:30",
    title: String = "출근",
    source: QuestSource = QuestSource.MANUAL,
    templateId: Long? = null,
    status: QuestStatus = QuestStatus.SCHEDULED,
): QuestEntity {
    val dueTime = LocalTime.parse(due)
    return QuestEntity(
        id = QuestIds.of(d(date), kind, dueTime),
        date = d(date),
        kind = kind,
        title = title,
        dueAt = d(date).atTime(dueTime),
        targetDepartAt = d(date).atTime(LocalTime.parse(depart)),
        source = source,
        status = status,
        templateId = templateId,
    )
}

fun weekdayTemplate(
    id: Long = 0,
    title: String = "출근",
    kind: QuestKind = QuestKind.WORK,
    due: String = "09:00",
    depart: String = "08:30",
    daysMask: Int = WEEKDAYS_MASK,
    active: Boolean = true,
): ScheduleTemplateEntity = ScheduleTemplateEntity(
    id = id,
    title = title,
    kind = kind,
    daysMask = daysMask,
    dueTime = LocalTime.parse(due),
    targetDepartTime = LocalTime.parse(depart),
    active = active,
)

/** Robolectric 테스트 전용 메모리 DB. */
fun inMemoryDb(): ChronoDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ChronoDatabase::class.java)
        .allowMainThreadQueries()
        .build()
