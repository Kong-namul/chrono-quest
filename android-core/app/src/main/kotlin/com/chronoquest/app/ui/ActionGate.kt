package com.chronoquest.app.ui

import kotlinx.coroutines.sync.Mutex

/**
 * 버튼 연타 막기(인계 메모 A2b-2). 동작 하나가 끝나기 전에 들어온 동작은 버린다.
 * 버린 동작에는 아무 메시지도 띄우지 않는다.
 */
class ActionGate {
    private val mutex = Mutex()

    /** 다른 동작이 진행 중이면 [block]을 부르지 않고 false를 돌려준다. */
    suspend fun run(block: suspend () -> Unit): Boolean {
        if (!mutex.tryLock()) return false
        try {
            block()
        } finally {
            mutex.unlock()
        }
        return true
    }
}
