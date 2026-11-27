package com.chronoquest.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chronoquest.app.AppContainer
import com.chronoquest.app.ui.launchSafely
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.StateFlow

/** presenter를 viewModelScope에 연결만 한다. 로직은 HomePresenter에 있다. 예외는 presenter.onFailure로 넘긴다. */
class HomeViewModel(private val presenter: HomePresenter) : ViewModel() {
    val state: StateFlow<HomeUiState> = presenter.state

    fun refresh() = act { presenter.refresh() }
    fun onDepart(questId: String) = act { presenter.onDepart(questId) }
    fun onArrive(questId: String) = presenter.onArrive(questId)
    fun onSafetyAnswered(questId: String, safe: Boolean) = act { presenter.onSafetyAnswered(questId, safe) }
    fun onRecordAnyway(dialog: HomeDialog.ConfirmEarly) = act { presenter.onRecordAnyway(dialog) }
    fun onEditSchedule(dialog: HomeDialog.ConfirmEarly) = act { presenter.onEditScheduleRequested(dialog) }
    fun onRescheduleConfirmed(dialog: HomeDialog.Reschedule, due: LocalTime, depart: LocalTime) =
        act { presenter.onRescheduleConfirmed(dialog, due, depart) }
    fun onSelfControlAnswered(date: LocalDate, value: Boolean) = act { presenter.onSelfControlAnswered(date, value) }
    fun onCleanupLate(questId: String) = act { presenter.onCleanupLate(questId) }
    fun onCleanupCancel(questId: String) = act { presenter.onCleanupCancel(questId) }
    fun onCleanupAddArrival(questId: String) = presenter.onCleanupAddArrival(questId)
    fun onArrivalEntered(dialog: HomeDialog.AddArrival, time: LocalTime, safe: Boolean) =
        act { presenter.onArrivalEntered(dialog, time, safe) }
    fun onCleanupVacation(questId: String) = presenter.onCleanupVacation(questId)
    fun onVacationConfirmed(dialog: HomeDialog.ConfirmVacation) = act { presenter.onVacationConfirmed(dialog) }
    fun onCelebrationsSeen(keys: List<String>) = act { presenter.onCelebrationsSeen(keys) }
    fun dismissDialog() = presenter.dismissDialog()
    fun dismissMessage() = presenter.dismissMessage()

    private fun act(block: suspend () -> Unit) {
        launchSafely(presenter::onFailure, block)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    HomePresenter({ container.gameService }, { container.scheduleService }, container.notifier),
                )
            }
        }
    }
}
