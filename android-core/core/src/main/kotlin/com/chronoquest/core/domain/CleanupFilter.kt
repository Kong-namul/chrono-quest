package com.chronoquest.core.domain

import com.chronoquest.core.data.QuestEntity
import com.chronoquest.rules.engine.GameState
import java.time.LocalDateTime

/**
 * 홈의 "정리 필요" 카드 대상 (스펙 6.2). 엔진의 pendingQuestIds에는 이동 중인 일정도 들어 있으므로,
 * 예정 +3시간이 지난 것만 거른다(인계 메모 A2).
 */
object CleanupFilter {
    const val GRACE_HOURS = 3L

    fun needsCleanup(state: GameState, quests: List<QuestEntity>, now: LocalDateTime): List<QuestEntity> {
        val pending = state.pendingQuestIds.toSet()
        return quests
            .filter { it.id in pending && !it.dueAt.plusHours(GRACE_HOURS).isAfter(now) }
            .sortedWith(compareBy<QuestEntity>({ it.dueAt }, { it.id }))
    }
}
