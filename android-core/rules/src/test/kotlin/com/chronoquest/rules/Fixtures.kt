package com.chronoquest.rules

import com.chronoquest.rules.model.DayRecord
import com.chronoquest.rules.model.Ledger
import com.chronoquest.rules.model.Quest
import com.chronoquest.rules.model.QuestKind
import com.chronoquest.rules.model.QuestRecord
import com.chronoquest.rules.model.Resolution
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

val SEASON_START: LocalDate = LocalDate.parse("2026-11-25")

fun day(date: String): LocalDate = LocalDate.parse(date)

fun at(date: String, time: String): LocalDateTime = LocalDateTime.parse("${date}T$time")

/** 출근 일정. 목표 출발 기본값은 예정 30분 전. */
fun work(date: String, due: String = "09:00", depart: String? = null): Quest =
    quest(date, QuestKind.WORK, "출근", due, depart)

/** 출근 외 일정. 목표 출발 기본값은 예정 30분 전. */
fun other(date: String, due: String = "19:00", depart: String? = null): Quest =
    quest(date, QuestKind.OTHER, "약속", due, depart)

private fun quest(date: String, kind: QuestKind, title: String, due: String, depart: String?): Quest {
    val dueAt = at(date, due)
    return Quest(
        id = "$date-$kind-$due",
        date = day(date),
        kind = kind,
        title = title,
        dueAt = dueAt,
        targetDepartAt = depart?.let { at(date, it) } ?: dueAt.minusMinutes(30),
    )
}

fun Quest.arrived(time: String, safe: Boolean = true, departed: String? = null): QuestRecord =
    QuestRecord(
        questId = id,
        departedAt = departed?.let { at(date.toString(), it) },
        arrivedAt = at(date.toString(), time),
        safe = safe,
    )

private fun Quest.arrivedOffset(minutes: Long): QuestRecord =
    QuestRecord(questId = id, arrivedAt = dueAt.plusMinutes(minutes), safe = true)

/** 예정 2분 전 안전 도착 (ON_TIME) */
fun Quest.onTime(): QuestRecord = arrivedOffset(-2)

/** 예정 15분 전 안전 도착 (EARLY) */
fun Quest.early(): QuestRecord = arrivedOffset(-15)

/** 예정 3분 후 안전 도착 (CLOSE) */
fun Quest.close(): QuestRecord = arrivedOffset(3)

/** 예정 20분 후 안전 도착 (LATE) */
fun Quest.late(): QuestRecord = arrivedOffset(20)

fun Quest.noRecord(): QuestRecord = QuestRecord(questId = id, resolution = Resolution.NO_RECORD)

fun wakeDay(
    date: String,
    rang: String = "07:00",
    solved: String = "07:03",
    slept: Boolean = true,
    recheck: Boolean = true,
    selfControl: Boolean? = null,
): DayRecord = DayRecord(
    date = day(date),
    alarmRangAt = at(date, rang),
    quizSolvedAt = at(date, solved),
    sleptEnough = slept,
    recheckPassed = recheck,
    selfControl = selfControl,
)

/** [from]부터 [toInclusive]까지의 평일(월~금) 날짜 문자열. */
fun weekdays(from: String, toInclusive: String): List<String> =
    generateSequence(day(from)) { it.plusDays(1) }
        .takeWhile { !it.isAfter(day(toInclusive)) }
        .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
        .map { it.toString() }
        .toList()

/** [from]부터 하루에 하나씩 [count]개의 출근 외 일정. */
fun dailyOthers(from: String, count: Int, due: String = "10:00"): List<Quest> =
    (0 until count).map { other(day(from).plusDays(it.toLong()).toString(), due) }

fun onTimeAll(quests: List<Quest>): List<QuestRecord> = quests.map { it.onTime() }

fun ledger(
    quests: List<Quest>,
    records: List<QuestRecord> = emptyList(),
    days: List<DayRecord> = emptyList(),
    vacations: Set<LocalDate> = emptySet(),
): Ledger = Ledger(
    seasonStart = SEASON_START,
    quests = quests,
    records = records,
    days = days,
    vacations = vacations,
)
