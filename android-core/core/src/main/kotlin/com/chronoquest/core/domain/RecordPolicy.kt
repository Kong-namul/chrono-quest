package com.chronoquest.core.domain

import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.core.data.RecordSource
import com.chronoquest.rules.model.QuestStatus
import com.chronoquest.rules.model.Resolution
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class RejectReason {
    QUEST_CANCELED,
    FUTURE_TIME,
    ALREADY_RECORDED,
    ARRIVAL_BEFORE_DEPARTURE,
    SAFE_WITHOUT_ARRIVAL,
    SAFE_REQUIRED,
}

sealed interface RecordResult {
    data class Ok(val record: QuestRecordEntity) : RecordResult
    data class Rejected(val reason: RejectReason) : RecordResult
}

/**
 * 출발·도착 기록 규칙 (스펙 6.2, 인계 메모 A2 원장 계약).
 * - 미래 시각은 받지 않는다.
 * - 출발·도착은 각각 한 번만 기록하고 먼저 확정된 것이 이긴다. 바꾸려면 [edit]를 쓴다.
 * - 안전 여부는 도착과 함께만 저장한다.
 */
object RecordPolicy {
    fun depart(
        quest: QuestEntity,
        current: QuestRecordEntity?,
        at: LocalDateTime,
        now: LocalDateTime,
        source: RecordSource,
    ): RecordResult {
        val time = at.truncatedTo(ChronoUnit.SECONDS)
        basicRejection(quest, time, now)?.let { return it }
        if (current?.departedAt != null) return RecordResult.Rejected(RejectReason.ALREADY_RECORDED)
        val arrivedAt = current?.arrivedAt
        if (arrivedAt != null && time.isAfter(arrivedAt)) {
            return RecordResult.Rejected(RejectReason.ARRIVAL_BEFORE_DEPARTURE)
        }
        val base = current ?: QuestRecordEntity(questId = quest.id)
        return RecordResult.Ok(base.copy(departedAt = time, departSource = source))
    }

    fun arrive(
        quest: QuestEntity,
        current: QuestRecordEntity?,
        at: LocalDateTime,
        safe: Boolean,
        now: LocalDateTime,
        source: RecordSource,
    ): RecordResult {
        val time = at.truncatedTo(ChronoUnit.SECONDS)
        basicRejection(quest, time, now)?.let { return it }
        if (current?.arrivedAt != null || current?.resolution == Resolution.NO_RECORD) {
            return RecordResult.Rejected(RejectReason.ALREADY_RECORDED)
        }
        val departedAt = current?.departedAt
        if (departedAt != null && time.isBefore(departedAt)) {
            return RecordResult.Rejected(RejectReason.ARRIVAL_BEFORE_DEPARTURE)
        }
        val base = current ?: QuestRecordEntity(questId = quest.id)
        return RecordResult.Ok(base.copy(arrivedAt = time, arriveSource = source, safe = safe))
    }

    /** 기록 화면의 수정. 바뀐 시각의 출처만 EDIT로 바꾸고, 기록 없음 정리는 되돌린다. */
    fun edit(
        quest: QuestEntity,
        current: QuestRecordEntity?,
        departedAt: LocalDateTime?,
        arrivedAt: LocalDateTime?,
        safe: Boolean?,
        now: LocalDateTime,
    ): RecordResult {
        if (quest.status == QuestStatus.CANCELED) return RecordResult.Rejected(RejectReason.QUEST_CANCELED)
        val dep = departedAt?.truncatedTo(ChronoUnit.SECONDS)
        val arr = arrivedAt?.truncatedTo(ChronoUnit.SECONDS)
        if ((dep != null && dep.isAfter(now)) || (arr != null && arr.isAfter(now))) {
            return RecordResult.Rejected(RejectReason.FUTURE_TIME)
        }
        if (dep != null && arr != null && arr.isBefore(dep)) {
            return RecordResult.Rejected(RejectReason.ARRIVAL_BEFORE_DEPARTURE)
        }
        if (arr == null && safe != null) return RecordResult.Rejected(RejectReason.SAFE_WITHOUT_ARRIVAL)
        if (arr != null && safe == null) return RecordResult.Rejected(RejectReason.SAFE_REQUIRED)
        val base = current ?: QuestRecordEntity(questId = quest.id)
        return RecordResult.Ok(
            base.copy(
                departedAt = dep,
                departSource = sourceAfterEdit(dep, base.departedAt, base.departSource),
                arrivedAt = arr,
                arriveSource = sourceAfterEdit(arr, base.arrivedAt, base.arriveSource),
                safe = safe,
                edited = true,
                resolution = Resolution.NONE,
            ),
        )
    }

    /** "정리 필요" 카드의 [기록 없음 → 지각 처리]. 도착 기록이 있으면 할 수 없다. */
    fun resolveNoRecord(quest: QuestEntity, current: QuestRecordEntity?): RecordResult {
        if (quest.status == QuestStatus.CANCELED) return RecordResult.Rejected(RejectReason.QUEST_CANCELED)
        if (current?.arrivedAt != null) return RecordResult.Rejected(RejectReason.ALREADY_RECORDED)
        val base = current ?: QuestRecordEntity(questId = quest.id)
        return RecordResult.Ok(base.copy(resolution = Resolution.NO_RECORD))
    }

    private fun basicRejection(quest: QuestEntity, time: LocalDateTime, now: LocalDateTime): RecordResult.Rejected? =
        when {
            quest.status == QuestStatus.CANCELED -> RecordResult.Rejected(RejectReason.QUEST_CANCELED)
            time.isAfter(now) -> RecordResult.Rejected(RejectReason.FUTURE_TIME)
            else -> null
        }

    private fun sourceAfterEdit(
        value: LocalDateTime?,
        previous: LocalDateTime?,
        previousSource: RecordSource?,
    ): RecordSource? = when {
        value == null -> null
        value == previous -> previousSource
        else -> RecordSource.EDIT
    }
}
