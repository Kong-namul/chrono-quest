package com.chronoquest.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.QuestSource
import com.chronoquest.core.domain.QuestIds
import com.chronoquest.rules.model.QuestKind
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 갤럭시 Z 플립5 안쪽 화면(1080×2640 px, 약 425 ppi)에 맞춘 Robolectric qualifiers. */
const val FLIP5 = "w411dp-h1006dp-420dpi"

val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

fun d(date: String): LocalDate = LocalDate.parse(date)

fun dt(date: String, time: String): LocalDateTime = LocalDateTime.parse("${date}T$time")

/** 테스트에서 시각을 옮길 수 있는 시계. */
class MutableClock(var now: LocalDateTime) : Clock() {
    override fun getZone(): ZoneId = SEOUL

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now.atZone(SEOUL).toInstant()
}

fun questEntity(
    date: String,
    kind: QuestKind = QuestKind.WORK,
    due: String = "09:00",
    depart: String = "08:30",
    title: String = "출근",
): QuestEntity {
    val dueTime = LocalTime.parse(due)
    return QuestEntity(
        id = QuestIds.of(d(date), kind, dueTime),
        date = d(date),
        kind = kind,
        title = title,
        dueAt = d(date).atTime(dueTime),
        targetDepartAt = d(date).atTime(LocalTime.parse(depart)),
        source = QuestSource.MANUAL,
    )
}

fun inMemoryDb(): ChronoDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ChronoDatabase::class.java)
        .allowMainThreadQueries()
        .build()
