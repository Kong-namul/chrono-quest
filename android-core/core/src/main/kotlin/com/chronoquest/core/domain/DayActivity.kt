package com.chronoquest.core.domain

import com.chronoquest.core.data.DayRecordEntity
import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.rules.model.Resolution

/**
 * 사용자가 남긴 기록이 있는지 판단한다(스펙 D13, 2026-11-27 사용자 결정).
 * - 기록이 있는 일정은 취소할 수 없다.
 * - 기록이 있는 날은 휴가로 바꿀 수 없다.
 * - 지난 날은 활동이 전혀 없을 때만 휴가로 넣는다.
 */
object DayActivity {
    /** 출발·도착·기록 없음 정리 중 하나라도 있으면 기록이 있는 일정이다. */
    fun hasRecord(record: QuestRecordEntity?): Boolean =
        record != null &&
            (record.departedAt != null || record.arrivedAt != null || record.resolution != Resolution.NONE)

    /**
     * 퀴즈·기상 확인·행동통제 답이 하나라도 있으면 활동한 날이다.
     * 알람이 울린 것은 자동으로 일어난 일이라서 활동으로 치지 않는다.
     */
    fun hasDayRecord(day: DayRecordEntity?): Boolean =
        day != null &&
            (day.quizSolvedAt != null || day.sleptEnough != null || day.recheckPassed != null || day.selfControl != null)
}
