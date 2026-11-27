package com.chronoquest.core.domain

import com.chronoquest.core.data.DayRecordEntity
import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.rules.engine.GameState
import com.chronoquest.rules.engine.QuestOutcome
import java.time.LocalDate

data class TodayQuest(
    val quest: QuestEntity,
    val record: QuestRecordEntity?,
    val outcome: QuestOutcome?,
)

data class TodaySummary(
    val date: LocalDate,
    val quests: List<TodayQuest>,
    val day: DayRecordEntity?,
    /** 오늘이 휴가면 출근 일정은 판정에서 빠진다. 다른 일정은 그대로 판정한다. */
    val vacation: Boolean = false,
)

/** 홈 "오늘의 퀘스트"용 요약. 취소·휴가로 판정에서 빠진 일정은 outcome이 null이다. */
object Today {
    fun summarize(
        date: LocalDate,
        state: GameState,
        quests: List<QuestEntity>,
        records: List<QuestRecordEntity>,
        days: List<DayRecordEntity>,
        vacations: Set<LocalDate> = emptySet(),
    ): TodaySummary {
        val recordByQuest = records.associateBy { it.questId }
        val items = quests
            .filter { it.date == date }
            .sortedWith(compareBy<QuestEntity>({ it.dueAt }, { it.id }))
            .map { TodayQuest(it, recordByQuest[it.id], state.outcomes[it.id]) }
        return TodaySummary(
            date = date,
            quests = items,
            day = days.firstOrNull { it.date == date },
            vacation = date in vacations,
        )
    }
}
