package com.chronoquest.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chronoquest.rules.model.QuestKind
import com.chronoquest.rules.model.QuestStatus
import com.chronoquest.rules.model.Resolution
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class QuestSource { TEMPLATE, CALENDAR, MANUAL }

enum class RecordSource { GEOFENCE, BUTTON, EDIT }

/** 반복 일정 템플릿 (스펙 4장). daysMask는 월(bit0)~일(bit6). */
@Entity(tableName = "schedule_template")
data class ScheduleTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: QuestKind,
    val daysMask: Int,
    val dueTime: LocalTime,
    val targetDepartTime: LocalTime,
    val fromPlaceId: Long? = null,
    val toPlaceId: Long? = null,
    val active: Boolean = true,
)

/** 일정 인스턴스. id는 QuestIds.of(date, kind, dueTime), date는 항상 dueAt의 날짜다. */
@Entity(tableName = "quest", indices = [Index("date"), Index("templateId")])
data class QuestEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val kind: QuestKind,
    val title: String,
    val dueAt: LocalDateTime,
    val targetDepartAt: LocalDateTime,
    val source: QuestSource,
    val status: QuestStatus = QuestStatus.SCHEDULED,
    val templateId: Long? = null,
    val calendarEventId: Long? = null,
    val fromPlaceId: Long? = null,
    val toPlaceId: Long? = null,
)

@Entity(tableName = "quest_record")
data class QuestRecordEntity(
    @PrimaryKey val questId: String,
    val departedAt: LocalDateTime? = null,
    val departSource: RecordSource? = null,
    val arrivedAt: LocalDateTime? = null,
    val arriveSource: RecordSource? = null,
    val safe: Boolean? = null,
    val edited: Boolean = false,
    val resolution: Resolution = Resolution.NONE,
)

@Entity(tableName = "day_record")
data class DayRecordEntity(
    @PrimaryKey val date: LocalDate,
    val alarmRangAt: LocalDateTime? = null,
    val quizSolvedAt: LocalDateTime? = null,
    val sleptEnough: Boolean? = null,
    val recheckPassed: Boolean? = null,
    val selfControl: Boolean? = null,
)

@Entity(tableName = "vacation")
data class VacationEntity(@PrimaryKey val date: LocalDate)

@Entity(tableName = "celebration_seen")
data class CelebrationSeenEntity(
    @PrimaryKey val grantKey: String,
    val seenAt: LocalDateTime,
)

/** 설정은 행 하나(id = 0)다. */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 0,
    val seasonStart: LocalDate,
    val representativeTitleId: Int? = null,
)
