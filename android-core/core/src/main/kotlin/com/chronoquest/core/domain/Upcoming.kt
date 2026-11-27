package com.chronoquest.core.domain

import com.chronoquest.core.data.DayRecordEntity
import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.QuestRecordEntity
import java.time.LocalDate

/** 일정 탭 "다가오는 일정"의 한 줄. */
data class UpcomingQuest(val quest: QuestEntity, val hasRecord: Boolean)

/** 일정 탭의 하루. [hasActivity]면 그날은 휴가로 바꿀 수 없다(스펙 D13). */
data class UpcomingDay(
    val date: LocalDate,
    val vacation: Boolean,
    val hasActivity: Boolean,
    val quests: List<UpcomingQuest>,
)

/** 오늘부터 [build]의 days일 동안 일정을 날짜별로 묶는다. 일정이 없는 날도 넣는다(휴가 지정용). */
object Upcoming {
    fun build(
        from: LocalDate,
        days: Int,
        quests: List<QuestEntity>,
        records: List<QuestRecordEntity>,
        dayRecords: List<DayRecordEntity>,
        vacations: Set<LocalDate>,
    ): List<UpcomingDay> {
        val recordByQuest = records.associateBy { it.questId }
        val dayByDate = dayRecords.associateBy { it.date }
        val questsByDate = quests.groupBy { it.date }
        return (0 until days).map { offset ->
            val date = from.plusDays(offset.toLong())
            val items = questsByDate[date].orEmpty()
                .sortedWith(compareBy<QuestEntity>({ it.dueAt }, { it.id }))
                .map { UpcomingQuest(it, DayActivity.hasRecord(recordByQuest[it.id])) }
            UpcomingDay(
                date = date,
                vacation = date in vacations,
                hasActivity = DayActivity.hasDayRecord(dayByDate[date]) || items.any { it.hasRecord },
                quests = items,
            )
        }
    }
}
