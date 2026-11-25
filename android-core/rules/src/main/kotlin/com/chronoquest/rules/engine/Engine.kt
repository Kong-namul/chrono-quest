package com.chronoquest.rules.engine

import com.chronoquest.rules.judge.Judges
import com.chronoquest.rules.model.DayRecord
import com.chronoquest.rules.model.Ledger
import com.chronoquest.rules.model.Quest
import com.chronoquest.rules.model.QuestKind
import com.chronoquest.rules.model.QuestRecord
import com.chronoquest.rules.model.QuestStatus
import com.chronoquest.rules.model.Resolution
import com.chronoquest.rules.model.Tier
import com.chronoquest.rules.titles.TitleCatalog
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * 판정 엔진 진입점. 원장 전체를 매번 처음부터 다시 계산한다(스펙 D9).
 * 같은 (ledger, now)면 항상 같은 [GameState]를 돌려준다.
 */
class Engine(private val catalog: TitleCatalog = TitleCatalog.load()) {

    fun compute(ledger: Ledger, now: LocalDateTime): GameState {
        val quests = activeQuests(ledger)
        val records = ledger.records.distinctBy { it.questId }.associateBy { it.questId }
        val days = ledger.days.distinctBy { it.date }.filter { !it.date.isBefore(ledger.seasonStart) }
        val outcomes = judgeAll(quests, records)

        val acc = Accumulator(catalog)
        buildEvents(quests, records, days, outcomes, ledger.seasonStart, now)
            .sortedWith(Event.ORDER)
            .forEach(acc::handle)

        val pending = quests
            .filter { outcomes.getValue(it.id).verdict == null && !it.dueAt.isAfter(now) }
            .sortedWith(compareBy<Quest>({ it.dueAt }, { it.id }))
            .map { it.id }
        val grants = acc.grants()
        val inventory = grants.mapNotNull { it.titleId }.groupingBy { it }.eachCount().toSortedMap()
        val tier = Tier.of(acc.xp)
        return GameState(
            xp = acc.xp,
            tier = tier,
            nextTierXp = tier.next?.minXp,
            counters = acc.counters,
            arriveStreak = acc.arriveStreak,
            perfectWeekStreak = acc.perfectWeekStreak,
            inventory = inventory,
            codexCount = inventory.size,
            outcomes = outcomes,
            pendingQuestIds = pending,
            grants = grants,
        )
    }

    /** 취소, 시즌 전, 휴가일 출근을 뺀 판정 대상 일정. */
    private fun activeQuests(ledger: Ledger): List<Quest> =
        ledger.quests
            .distinctBy { it.id }
            .filter { it.status == QuestStatus.SCHEDULED }
            .filter { !it.date.isBefore(ledger.seasonStart) }
            .filter { !(it.kind == QuestKind.WORK && it.date in ledger.vacations) }

    private fun judgeAll(quests: List<Quest>, records: Map<String, QuestRecord>): Map<String, QuestOutcome> =
        quests.associate { q ->
            val r = records[q.id]
            q.id to QuestOutcome(
                questId = q.id,
                verdict = Judges.arrival(q.dueAt, r?.arrivedAt, r?.safe, r?.resolution ?: Resolution.NONE),
                onTimeDeparture = Judges.onTimeDeparture(q.targetDepartAt, r?.departedAt),
            )
        }

    private fun buildEvents(
        quests: List<Quest>,
        records: Map<String, QuestRecord>,
        days: List<DayRecord>,
        outcomes: Map<String, QuestOutcome>,
        seasonStart: java.time.LocalDate,
        now: LocalDateTime,
    ): List<Event> {
        val events = mutableListOf<Event>()
        for (q in quests) {
            val record = records[q.id]
            val outcome = outcomes.getValue(q.id)
            val departedAt = record?.departedAt
            if (departedAt != null && outcome.onTimeDeparture) events += Event.Depart(departedAt, q)
            val verdict = outcome.verdict
            if (verdict != null) events += Event.Arrive(record?.arrivedAt ?: q.dueAt, q, verdict)
        }
        for (d in days) {
            val solvedAt = d.quizSolvedAt
            if (solvedAt != null && Judges.wakeSuccess(d)) events += Event.Wake(solvedAt, d.date)
            if (d.selfControl == true) {
                val firstDeparture = quests
                    .filter { it.date == d.date }
                    .mapNotNull { records[it.id]?.departedAt }
                    .minOrNull()
                events += Event.SelfControl(firstDeparture ?: d.date.atTime(23, 59), d.date)
            }
        }
        val work = quests.filter { it.kind == QuestKind.WORK }
        val workByWeek = work.groupBy { Periods.weekStart(it.date) }
        for (week in Periods.closedWeeks(seasonStart, now)) {
            val verdicts = workByWeek[week].orEmpty().map { outcomes.getValue(it.id).verdict }
            events += Event.WeekClose(week, judgePeriod(verdicts))
        }
        val workByMonth = work.groupBy { YearMonth.from(it.date) }
        for (month in Periods.closedMonths(seasonStart, now)) {
            val verdicts = workByMonth[month].orEmpty().map { outcomes.getValue(it.id).verdict }
            events += Event.MonthClose(month, judgePeriod(verdicts))
        }
        return events
    }
}
