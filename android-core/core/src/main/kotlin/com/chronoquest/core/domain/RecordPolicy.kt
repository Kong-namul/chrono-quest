package com.chronoquest.core.domain

import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.core.data.RecordSource
import com.chronoquest.rules.model.QuestStatus
import com.chronoquest.rules.model.Resolution
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class RejectReason {
    QUEST_CANCELED,
    FUTURE_TIME,
    ALREADY_RECORDED,
    ARRIVAL_BEFORE_DEPARTURE,
    SAFE_WITHOUT_ARRIVAL,
    SAFE_REQUIRED,
    WRONG_DAY,
    NOT_DUE_YET,
    /** 기록(출발·도착·기록 없음 정리)이 있어서 취소할 수 없다(스펙 D13). */
    HAS_RECORD,
    /** 지난 날짜라 바꿀 수 없다(스펙 D13). */
    PAST_DATE,
    /** 휴가인 날의 출근 일정은 기록하지 않는다. 엔진도 판정에서 뺀다. */
    ON_VACATION,
    /** 취소한 일정이 아니라서 되돌릴 게 없다. */
    NOT_CANCELED,
}

sealed interface RecordResult {
    data class Ok(val record: QuestRecordEntity) : RecordResult
    data class Rejected(val reason: RejectReason) : RecordResult

    /** 기준 시각보다 [minutesEarly]분 이르다. 기록하지 않고 일정 변경 여부를 묻는다. */
    data class NeedsEarlyConfirmation(val minutesEarly: Long) : RecordResult
}

/**
 * 출발·도착 기록 규칙 (스펙 6.2, 인계 메모 A2 원장 계약).
 * - 미래 시각은 받지 않는다.
 * - 기록 시각은 일정 날짜 안이어야 한다.
 * - 출발·도착은 각각 한 번만 기록하고 먼저 확정된 것이 이긴다. 바꾸려면 [edit]를 쓴다.
 * - 안전 여부는 도착과 함께만 저장한다.
 * - 출발이 목표 출발보다, 도착이 예정보다 [EARLY_CONFIRM_MINUTES]분 이상 이르면 기록하지 않고 확인을 받는다.
 *   거부 사유를 모두 본 뒤 마지막에 확인하고, `allowEarly`면 그대로 기록한다.
 */
object RecordPolicy {
    const val EARLY_CONFIRM_MINUTES = 30L

    fun depart(
        quest: QuestEntity,
        current: QuestRecordEntity?,
        at: LocalDateTime,
        now: LocalDateTime,
        source: RecordSource,
        allowEarly: Boolean = false,
    ): RecordResult {
        val time = at.truncatedTo(ChronoUnit.SECONDS)
        basicRejection(quest, time, now)?.let { return it }
        if (current?.departedAt != null) return RecordResult.Rejected(RejectReason.ALREADY_RECORDED)
        val arrivedAt = current?.arrivedAt
        if (arrivedAt != null && time.isAfter(arrivedAt)) {
            return RecordResult.Rejected(RejectReason.ARRIVAL_BEFORE_DEPARTURE)
        }
        earlyConfirmation(time, quest.targetDepartAt, allowEarly)?.let { return it }
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
        allowEarly: Boolean = false,
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
        earlyConfirmation(time, quest.dueAt, allowEarly)?.let { return it }
        val base = current ?: QuestRecordEntity(questId = quest.id)
        return RecordResult.Ok(base.copy(arrivedAt = time, arriveSource = source, safe = safe))
    }

    /**
     * 기록 화면의 수정. 바뀐 시각의 출처만 EDIT로 바꾸고, 도착을 넣으면 기록 없음 정리를 되돌린다.
     * 사용자가 직접 고친 시각이므로 이른 기록 확인은 하지 않는다.
     */
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
        if (listOfNotNull(dep, arr).any { it.toLocalDate() != quest.date }) {
            return RecordResult.Rejected(RejectReason.WRONG_DAY)
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
                resolution = if (arr != null) Resolution.NONE else base.resolution,
            ),
        )
    }

    /** "정리 필요" 카드의 [기록 없음 → 지각 처리]. 예정 시각 전이거나 도착 기록이 있으면 할 수 없다. */
    fun resolveNoRecord(quest: QuestEntity, current: QuestRecordEntity?, now: LocalDateTime): RecordResult {
        if (quest.status == QuestStatus.CANCELED) return RecordResult.Rejected(RejectReason.QUEST_CANCELED)
        if (now.isBefore(quest.dueAt)) return RecordResult.Rejected(RejectReason.NOT_DUE_YET)
        if (current?.arrivedAt != null) return RecordResult.Rejected(RejectReason.ALREADY_RECORDED)
        val base = current ?: QuestRecordEntity(questId = quest.id)
        return RecordResult.Ok(base.copy(resolution = Resolution.NO_RECORD))
    }

    private fun basicRejection(quest: QuestEntity, time: LocalDateTime, now: LocalDateTime): RecordResult.Rejected? =
        when {
            quest.status == QuestStatus.CANCELED -> RecordResult.Rejected(RejectReason.QUEST_CANCELED)
            time.isAfter(now) -> RecordResult.Rejected(RejectReason.FUTURE_TIME)
            time.toLocalDate() != quest.date -> RecordResult.Rejected(RejectReason.WRONG_DAY)
            else -> null
        }

    private fun earlyConfirmation(
        time: LocalDateTime,
        reference: LocalDateTime,
        allowEarly: Boolean,
    ): RecordResult.NeedsEarlyConfirmation? {
        val minutesEarly = Duration.between(time, reference).toMinutes()
        return if (minutesEarly >= EARLY_CONFIRM_MINUTES && !allowEarly) {
            RecordResult.NeedsEarlyConfirmation(minutesEarly)
        } else {
            null
        }
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
