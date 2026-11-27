package com.chronoquest.app.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.app.notify.SettlementNotifier
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {
    @Test
    fun `첫 새로고침이 실패해도 로딩에 갇히지 않고 알려준다`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val presenter = HomePresenter(
                { error("DB 고장") },
                { error("DB 고장") },
                SettlementNotifier { _, _ -> },
                Dispatchers.Unconfined,
            )
            val vm = HomeViewModel(presenter)
            vm.refresh()
            advanceUntilIdle()
            assertFalse(vm.state.value.loading)
            assertEquals("문제가 생겼어. 잠시 뒤에 다시 해줘.", vm.state.value.message)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
