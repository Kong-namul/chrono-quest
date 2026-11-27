package com.chronoquest.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 화면 동작을 viewModelScope에서 실행한다. 예외가 나면 [onFailure]로 넘겨서 화면이 멈추거나
 * 로딩에 갇히지 않게 한다(인계 메모 A2b-2). 취소(CancellationException)는 그대로 던진다.
 */
fun ViewModel.launchSafely(onFailure: (Throwable) -> Unit, block: suspend () -> Unit): Job =
    viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(e)
        }
    }
