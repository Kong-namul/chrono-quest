package com.chronoquest.core.domain

import com.chronoquest.rules.model.QuestKind
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 퀘스트 ID = "날짜-종류-예정시각(HH:mm)". 룰북 03-01: 날짜·종류·예정 시각이 같으면 같은 일정이다. */
object QuestIds {
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun of(date: LocalDate, kind: QuestKind, dueTime: LocalTime): String =
        "$date-$kind-${dueTime.format(TIME)}"
}
