package com.chronoquest.app.ui

import androidx.lifecycle.ViewModel
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SafeLaunchTest {
    @Test
    fun `예외가 나면 onFailure로 넘기고 앱은 멈추지 않는다`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = object : ViewModel() {}
            var caught: Throwable? = null
            vm.launchSafely({ caught = it }) { error("고장") }
            advanceUntilIdle()
            assertEquals("고장", caught?.message)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
