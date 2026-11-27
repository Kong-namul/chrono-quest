package com.chronoquest.app.ui

import com.chronoquest.core.domain.RejectReason
import com.chronoquest.core.domain.ScheduleProblem

/** 화면 공통 안내 문구(반말). 거부 사유와 일정 문제를 사람이 읽는 말로 바꾼다. */
object Messages {
    const val NOT_FOUND = "일정을 찾을 수 없어."
    const val FAILURE = "문제가 생겼어. 잠시 뒤에 다시 해줘."

    fun reject(reason: RejectReason): String = when (reason) {
        RejectReason.QUEST_CANCELED -> "취소한 일정이야."
        RejectReason.FUTURE_TIME -> "아직 오지 않은 시각은 기록할 수 없어."
        RejectReason.ALREADY_RECORDED -> "이미 기록했어."
        RejectReason.ARRIVAL_BEFORE_DEPARTURE -> "도착이 출발보다 빠를 수는 없어."
        RejectReason.SAFE_WITHOUT_ARRIVAL -> "도착 시각 없이 안전 여부만 넣을 수는 없어."
        RejectReason.SAFE_REQUIRED -> "안전하게 왔는지 알려줘."
        RejectReason.WRONG_DAY -> "그 일정 날짜에만 기록할 수 있어."
        RejectReason.NOT_DUE_YET -> "아직 예정 시각이 안 됐어."
        RejectReason.HAS_RECORD -> "기록이 있는 일정은 취소할 수 없어."
        RejectReason.PAST_DATE -> "지난 날짜는 바꿀 수 없어."
        RejectReason.ON_VACATION -> "휴가인 날은 출근을 기록하지 않아."
        RejectReason.NOT_CANCELED -> "취소한 일정이 아니야."
    }

    fun problem(problems: List<ScheduleProblem>): String = when (problems.firstOrNull()) {
        ScheduleProblem.DEPART_AFTER_DUE -> "출발 시각이 도착 시각보다 늦어."
        ScheduleProblem.DUPLICATE -> "같은 시각에 같은 종류 일정이 이미 있어."
        ScheduleProblem.HAS_RECORD -> "이미 기록이 있는 일정은 시각을 바꿀 수 없어."
        ScheduleProblem.NOT_FOUND -> NOT_FOUND
        ScheduleProblem.BLANK_TITLE -> "일정 이름이 비어 있어."
        ScheduleProblem.BEFORE_SEASON -> "시즌 시작 전 날짜야."
        ScheduleProblem.NO_DAYS -> "요일을 하나 이상 골라줘."
        ScheduleProblem.PAST_DATE -> "지난 날짜는 바꿀 수 없어."
        ScheduleProblem.DAY_HAS_RECORD -> "그날 기록이 있어서 휴가로 바꿀 수 없어."
        null -> "일정을 바꾸지 못했어."
    }
}
