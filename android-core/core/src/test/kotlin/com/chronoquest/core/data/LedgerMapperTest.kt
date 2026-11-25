package com.chronoquest.core.data

import com.chronoquest.core.d
import com.chronoquest.core.dt
import com.chronoquest.core.questEntity
import com.chronoquest.rules.model.DayRecord
import com.chronoquest.rules.model.Quest
import com.chronoquest.rules.model.QuestRecord
import com.chronoquest.rules.model.QuestStatus
import com.chronoquest.rules.model.Resolution
import kotlin.test.assertEquals
import org.junit.Test

class LedgerMapperTest {
    @Test
    fun `엔터티를 판정 엔진 원장으로 옮긴다`() {
        val q = questEntity("2026-11-30", status = QuestStatus.CANCELED)
        val r = QuestRecordEntity(
            questId = q.id,
            departedAt = dt("2026-11-30", "08:20"),
            departSource = RecordSource.BUTTON,
            arrivedAt = dt("2026-11-30", "08:55"),
            arriveSource = RecordSource.EDIT,
            safe = true,
            edited = true,
            resolution = Resolution.NONE,
        )
        val day = DayRecordEntity(
            date = d("2026-11-30"),
            alarmRangAt = dt("2026-11-30", "07:00"),
            quizSolvedAt = dt("2026-11-30", "07:03"),
            sleptEnough = true,
            recheckPassed = true,
            selfControl = false,
        )
        val ledger = LedgerMapper.toLedger(
            settings = AppSettingsEntity(seasonStart = d("2026-11-25")),
            quests = listOf(q),
            records = listOf(r),
            days = listOf(day),
            vacations = listOf(VacationEntity(d("2026-12-02"))),
        )
        assertEquals(d("2026-11-25"), ledger.seasonStart)
        assertEquals(
            listOf(Quest(q.id, q.date, q.kind, q.title, q.dueAt, q.targetDepartAt, QuestStatus.CANCELED)),
            ledger.quests,
        )
        assertEquals(
            listOf(QuestRecord(q.id, dt("2026-11-30", "08:20"), dt("2026-11-30", "08:55"), true, Resolution.NONE)),
            ledger.records,
        )
        assertEquals(
            listOf(DayRecord(d("2026-11-30"), dt("2026-11-30", "07:00"), dt("2026-11-30", "07:03"), true, true, false)),
            ledger.days,
        )
        assertEquals(setOf(d("2026-12-02")), ledger.vacations)
    }
}
