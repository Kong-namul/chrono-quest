package com.chronoquest.core.domain

import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.core.data.RecordSource
import com.chronoquest.core.dt
import com.chronoquest.core.questEntity
import com.chronoquest.rules.model.QuestStatus
import com.chronoquest.rules.model.Resolution
import java.time.LocalDateTime
import kotlin.test.assertEquals
import org.junit.Test

class RecordPolicyTest {
    private val quest = questEntity("2026-11-30")
    private val canceled = quest.copy(status = QuestStatus.CANCELED)
    private val now = dt("2026-11-30", "12:00")
    private fun at(time: String): LocalDateTime = dt("2026-11-30", time)

    private fun ok(result: RecordResult): QuestRecordEntity = (result as RecordResult.Ok).record

    @Test
    fun `출발을 초 단위로 기록한다`() {
        val record = ok(RecordPolicy.depart(quest, null, LocalDateTime.parse("2026-11-30T08:20:15.5"), now, RecordSource.BUTTON))
        assertEquals(QuestRecordEntity(questId = quest.id, departedAt = at("08:20:15"), departSource = RecordSource.BUTTON), record)
    }

    @Test
    fun `출발은 한 번만이고 미래 시각과 취소된 일정은 거부한다`() {
        val departed = QuestRecordEntity(questId = quest.id, departedAt = at("08:20"))
        assertEquals(
            RecordResult.Rejected(RejectReason.ALREADY_RECORDED),
            RecordPolicy.depart(quest, departed, at("08:25"), now, RecordSource.BUTTON),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.FUTURE_TIME),
            RecordPolicy.depart(quest, null, at("12:01"), now, RecordSource.BUTTON),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.QUEST_CANCELED),
            RecordPolicy.depart(canceled, null, at("08:20"), now, RecordSource.BUTTON),
        )
    }

    @Test
    fun `도착보다 늦은 출발은 거부한다`() {
        val arrived = QuestRecordEntity(questId = quest.id, arrivedAt = at("08:55"), safe = true)
        assertEquals(
            RecordResult.Rejected(RejectReason.ARRIVAL_BEFORE_DEPARTURE),
            RecordPolicy.depart(quest, arrived, at("09:00"), now, RecordSource.BUTTON),
        )
    }

    @Test
    fun `도착은 안전 여부와 함께 기록하고 출발 기록을 유지한다`() {
        val departed = QuestRecordEntity(questId = quest.id, departedAt = at("08:20"), departSource = RecordSource.GEOFENCE)
        val record = ok(RecordPolicy.arrive(quest, departed, at("08:55"), safe = false, now = now, source = RecordSource.BUTTON))
        assertEquals(
            departed.copy(arrivedAt = at("08:55"), arriveSource = RecordSource.BUTTON, safe = false),
            record,
        )
    }

    @Test
    fun `도착은 한 번만이고 기록 없음으로 정리된 뒤나 출발보다 이르면 거부한다`() {
        val arrived = QuestRecordEntity(questId = quest.id, arrivedAt = at("08:55"), safe = true)
        val noRecord = QuestRecordEntity(questId = quest.id, resolution = Resolution.NO_RECORD)
        val departed = QuestRecordEntity(questId = quest.id, departedAt = at("08:20"))
        assertEquals(
            RecordResult.Rejected(RejectReason.ALREADY_RECORDED),
            RecordPolicy.arrive(quest, arrived, at("09:00"), true, now, RecordSource.BUTTON),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.ALREADY_RECORDED),
            RecordPolicy.arrive(quest, noRecord, at("09:00"), true, now, RecordSource.BUTTON),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.ARRIVAL_BEFORE_DEPARTURE),
            RecordPolicy.arrive(quest, departed, at("08:10"), true, now, RecordSource.BUTTON),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.FUTURE_TIME),
            RecordPolicy.arrive(quest, null, at("12:30"), true, now, RecordSource.BUTTON),
        )
    }

    @Test
    fun `수정은 바뀐 시각만 EDIT로 표시하고 edited를 남긴다`() {
        val current = QuestRecordEntity(
            questId = quest.id,
            departedAt = at("08:20"),
            departSource = RecordSource.GEOFENCE,
            arrivedAt = at("09:10"),
            arriveSource = RecordSource.BUTTON,
            safe = true,
        )
        val record = ok(RecordPolicy.edit(quest, current, at("08:20"), at("08:58"), true, now))
        assertEquals(
            current.copy(arrivedAt = at("08:58"), arriveSource = RecordSource.EDIT, edited = true),
            record,
        )
    }

    @Test
    fun `수정 검증`() {
        assertEquals(
            RecordResult.Rejected(RejectReason.FUTURE_TIME),
            RecordPolicy.edit(quest, null, null, at("12:30"), true, now),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.ARRIVAL_BEFORE_DEPARTURE),
            RecordPolicy.edit(quest, null, at("09:00"), at("08:50"), true, now),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.SAFE_WITHOUT_ARRIVAL),
            RecordPolicy.edit(quest, null, at("08:20"), null, false, now),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.SAFE_REQUIRED),
            RecordPolicy.edit(quest, null, null, at("08:50"), null, now),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.QUEST_CANCELED),
            RecordPolicy.edit(canceled, null, null, at("08:50"), true, now),
        )
    }

    @Test
    fun `수정하면 기록 없음 정리를 되돌린다`() {
        val noRecord = QuestRecordEntity(questId = quest.id, resolution = Resolution.NO_RECORD)
        val record = ok(RecordPolicy.edit(quest, noRecord, null, at("08:58"), true, now))
        assertEquals(Resolution.NONE, record.resolution)
        assertEquals(at("08:58"), record.arrivedAt)
    }

    @Test
    fun `도착 없이 고치면 기록 없음 정리를 유지한다`() {
        val noRecord = QuestRecordEntity(questId = quest.id, resolution = Resolution.NO_RECORD)
        val record = ok(RecordPolicy.edit(quest, noRecord, at("08:20"), null, null, now))
        assertEquals(Resolution.NO_RECORD, record.resolution)
        assertEquals(at("08:20"), record.departedAt)
    }

    @Test
    fun `기록 없음 정리는 도착 기록이 없을 때만 된다`() {
        val record = ok(RecordPolicy.resolveNoRecord(quest, null, now))
        assertEquals(QuestRecordEntity(questId = quest.id, resolution = Resolution.NO_RECORD), record)
        assertEquals(
            RecordResult.Rejected(RejectReason.ALREADY_RECORDED),
            RecordPolicy.resolveNoRecord(quest, QuestRecordEntity(questId = quest.id, arrivedAt = at("08:55"), safe = true), now),
        )
        assertEquals(RecordResult.Rejected(RejectReason.QUEST_CANCELED), RecordPolicy.resolveNoRecord(canceled, null, now))
    }

    @Test
    fun `기록 없음 정리는 예정 시각부터 된다`() {
        assertEquals(RecordResult.Rejected(RejectReason.NOT_DUE_YET), RecordPolicy.resolveNoRecord(quest, null, at("08:59")))
        assertEquals(Resolution.NO_RECORD, ok(RecordPolicy.resolveNoRecord(quest, null, at("09:00"))).resolution)
        assertEquals(Resolution.NO_RECORD, ok(RecordPolicy.resolveNoRecord(quest, null, at("09:01"))).resolution)
    }

    @Test
    fun `일정 날짜가 아닌 시각은 출발 도착 수정 모두 거부한다`() {
        val yesterday = dt("2026-11-29", "23:50")
        assertEquals(
            RecordResult.Rejected(RejectReason.WRONG_DAY),
            RecordPolicy.depart(quest, null, yesterday, now, RecordSource.BUTTON),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.WRONG_DAY),
            RecordPolicy.arrive(quest, null, yesterday, true, now, RecordSource.BUTTON),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.WRONG_DAY),
            RecordPolicy.edit(quest, null, yesterday, at("08:55"), true, now),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.WRONG_DAY),
            RecordPolicy.edit(quest, null, null, yesterday, true, now),
        )
    }

    @Test
    fun `목표 출발보다 30분 이상 이른 출발은 확인을 묻는다`() {
        assertEquals(
            RecordResult.NeedsEarlyConfirmation(30),
            RecordPolicy.depart(quest, null, at("08:00"), now, RecordSource.BUTTON),
        )
        assertEquals(at("08:01"), ok(RecordPolicy.depart(quest, null, at("08:01"), now, RecordSource.BUTTON)).departedAt)
        val allowed = ok(RecordPolicy.depart(quest, null, at("08:00"), now, RecordSource.BUTTON, allowEarly = true))
        assertEquals(at("08:00"), allowed.departedAt)
    }

    @Test
    fun `예정보다 30분 이상 이른 도착은 확인을 묻는다`() {
        assertEquals(
            RecordResult.NeedsEarlyConfirmation(30),
            RecordPolicy.arrive(quest, null, at("08:30"), true, now, RecordSource.BUTTON),
        )
        assertEquals(at("08:31"), ok(RecordPolicy.arrive(quest, null, at("08:31"), true, now, RecordSource.BUTTON)).arrivedAt)
        val allowed = ok(RecordPolicy.arrive(quest, null, at("08:30"), true, now, RecordSource.BUTTON, allowEarly = true))
        assertEquals(at("08:30"), allowed.arrivedAt)
    }

    @Test
    fun `이른 기록 확인보다 거부 사유를 먼저 본다`() {
        val departed = QuestRecordEntity(questId = quest.id, departedAt = at("08:20"))
        val arrived = QuestRecordEntity(questId = quest.id, arrivedAt = at("07:50"), safe = true)
        assertEquals(
            RecordResult.Rejected(RejectReason.ARRIVAL_BEFORE_DEPARTURE),
            RecordPolicy.arrive(quest, departed, at("08:10"), true, now, RecordSource.BUTTON),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.ARRIVAL_BEFORE_DEPARTURE),
            RecordPolicy.depart(quest, arrived, at("07:55"), now, RecordSource.BUTTON),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.ALREADY_RECORDED),
            RecordPolicy.depart(quest, departed, at("07:00"), now, RecordSource.BUTTON),
        )
    }

    @Test
    fun `수정은 이른 기록 확인 없이 저장한다`() {
        val record = ok(RecordPolicy.edit(quest, null, at("07:00"), at("07:40"), true, now))
        assertEquals(at("07:00"), record.departedAt)
        assertEquals(at("07:40"), record.arrivedAt)
    }
}
