package com.chronoquest.rules.model

import java.time.LocalDate
import java.time.LocalDateTime

enum class QuestKind { WORK, OTHER }

enum class QuestStatus { SCHEDULED, CANCELED }

/** 도착 기록이 없을 때 사용자가 고른 정리 방식. NO_RECORD는 지각으로 판정한다. */
enum class Resolution { NONE, NO_RECORD }

/** 일정 인스턴스. id는 "날짜-kind-예정시각" 형식으로 유일하다. */
data class Quest(
    val id: String,
    val date: LocalDate,
    val kind: QuestKind,
    val title: String,
    val dueAt: LocalDateTime,
    val targetDepartAt: LocalDateTime,
    val status: QuestStatus = QuestStatus.SCHEDULED,
)

data class QuestRecord(
    val questId: String,
    val departedAt: LocalDateTime? = null,
    val arrivedAt: LocalDateTime? = null,
    val safe: Boolean? = null,
    val resolution: Resolution = Resolution.NONE,
)

data class DayRecord(
    val date: LocalDate,
    val alarmRangAt: LocalDateTime? = null,
    val quizSolvedAt: LocalDateTime? = null,
    val sleptEnough: Boolean? = null,
    val recheckPassed: Boolean? = null,
    val selfControl: Boolean? = null,
)

/** 판정 엔진 입력. 데이터 계층(Room)에서 이 형태로 매핑해서 넘긴다. */
data class Ledger(
    val seasonStart: LocalDate,
    val quests: List<Quest> = emptyList(),
    val records: List<QuestRecord> = emptyList(),
    val days: List<DayRecord> = emptyList(),
    val vacations: Set<LocalDate> = emptySet(),
)
