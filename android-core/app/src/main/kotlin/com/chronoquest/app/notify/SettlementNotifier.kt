package com.chronoquest.app.notify

import com.chronoquest.core.domain.Celebration

/** 기록을 정산한 결과를 알린다(스펙 6.3 "+30 XP · 새 칭호: 최초의 각성"). */
fun interface SettlementNotifier {
    fun notify(xpGained: Int, celebrations: List<Celebration>)

    companion object {
        val None = SettlementNotifier { _, _ -> }
    }
}
