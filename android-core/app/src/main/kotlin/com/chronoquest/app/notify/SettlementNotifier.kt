package com.chronoquest.app.notify

import com.chronoquest.core.domain.Celebration

/**
 * 기록을 정산한 결과를 알린다(스펙 6.3 "+30 XP · 새 칭호: 최초의 각성").
 * [xpGained]는 음수일 수 있다(기록한 일정을 취소하면 -20 등).
 * presenter의 compute 디스패처(백그라운드 스레드)에서 불리므로 UI는 직접 건드리지 말 것.
 */
fun interface SettlementNotifier {
    fun notify(xpGained: Int, celebrations: List<Celebration>)

    companion object {
        val None = SettlementNotifier { _, _ -> }
    }
}
