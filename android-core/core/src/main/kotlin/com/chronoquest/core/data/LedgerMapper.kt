package com.chronoquest.core.data

import com.chronoquest.rules.model.DayRecord
import com.chronoquest.rules.model.Ledger
import com.chronoquest.rules.model.Quest
import com.chronoquest.rules.model.QuestRecord

/** Room 엔터티를 판정 엔진 입력(Ledger)으로 옮긴다. 출처·수정 표시처럼 판정에 안 쓰는 필드는 버린다. */
object LedgerMapper {
    fun toLedger(
        settings: AppSettingsEntity,
        quests: List<QuestEntity>,
        records: List<QuestRecordEntity>,
        days: List<DayRecordEntity>,
        vacations: List<VacationEntity>,
    ): Ledger = Ledger(
        seasonStart = settings.seasonStart,
        quests = quests.map { it.toRules() },
        records = records.map { it.toRules() },
        days = days.map { it.toRules() },
        vacations = vacations.mapTo(HashSet()) { it.date },
    )

    private fun QuestEntity.toRules() = Quest(
        id = id,
        date = date,
        kind = kind,
        title = title,
        dueAt = dueAt,
        targetDepartAt = targetDepartAt,
        status = status,
    )

    private fun QuestRecordEntity.toRules() = QuestRecord(
        questId = questId,
        departedAt = departedAt,
        arrivedAt = arrivedAt,
        safe = safe,
        resolution = resolution,
    )

    private fun DayRecordEntity.toRules() = DayRecord(
        date = date,
        alarmRangAt = alarmRangAt,
        quizSolvedAt = quizSolvedAt,
        sleptEnough = sleptEnough,
        recheckPassed = recheckPassed,
        selfControl = selfControl,
    )
}
