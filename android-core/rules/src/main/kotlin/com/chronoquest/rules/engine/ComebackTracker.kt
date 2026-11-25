package com.chronoquest.rules.engine

import com.chronoquest.rules.judge.ArrivalVerdict

/**
 * 복귀 (스펙 5.6): 지각 후 정시 도착 3연속이면 1회.
 * 지각 하나당 최대 1회, CLOSE는 중립, 대기 중 재지각이면 처음부터 센다.
 */
internal class ComebackTracker {
    private var armed = false
    private var run = 0

    /** 이번 도착으로 복귀가 성립하면 true. */
    fun onArrival(verdict: ArrivalVerdict): Boolean {
        when (verdict) {
            ArrivalVerdict.LATE -> {
                armed = true
                run = 0
            }
            ArrivalVerdict.CLOSE -> Unit
            ArrivalVerdict.EARLY, ArrivalVerdict.ON_TIME -> if (armed) {
                run += 1
                if (run == REQUIRED) {
                    armed = false
                    run = 0
                    return true
                }
            }
        }
        return false
    }

    companion object {
        const val REQUIRED = 3
    }
}
