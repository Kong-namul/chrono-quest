package com.chronoquest.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chronoquest.app.AppContainer
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** presenter를 viewModelScope에 연결만 한다. 로직은 HomePresenter에 있다. */
class HomeViewModel(private val presenter: HomePresenter) : ViewModel() {
    val state: StateFlow<HomeUiState> = presenter.state

    fun refresh() = launch { presenter.refresh() }
    fun onDepart(questId: String) = launch { presenter.onDepart(questId) }
    fun onArrive(questId: String) = presenter.onArrive(questId)
    fun onSafetyAnswered(questId: String, safe: Boolean) = launch { presenter.onSafetyAnswered(questId, safe) }
    fun onRecordAnyway(dialog: HomeDialog.ConfirmEarly) = launch { presenter.onRecordAnyway(dialog) }
    fun onEditSchedule(dialog: HomeDialog.ConfirmEarly) = launch { presenter.onEditScheduleRequested(dialog) }
    fun onRescheduleConfirmed(dialog: HomeDialog.Reschedule, due: LocalTime, depart: LocalTime) =
        launch { presenter.onRescheduleConfirmed(dialog, due, depart) }
    fun onSelfControlAnswered(date: LocalDate, value: Boolean) = launch { presenter.onSelfControlAnswered(date, value) }
    fun onCleanupLate(questId: String) = launch { presenter.onCleanupLate(questId) }
    fun onCleanupCancel(questId: String) = launch { presenter.onCleanupCancel(questId) }
    fun onCleanupAddArrival(questId: String) = presenter.onCleanupAddArrival(questId)
    fun onArrivalEntered(dialog: HomeDialog.AddArrival, time: LocalTime, safe: Boolean) =
        launch { presenter.onArrivalEntered(dialog, time, safe) }

    fun onCleanupVacation(questId: String) = presenter.onCleanupVacation(questId)
    fun onVacationConfirmed(dialog: HomeDialog.ConfirmVacation) = launch { presenter.onVacationConfirmed(dialog) }
    fun onCelebrationsSeen() = launch { presenter.onCelebrationsSeen() }
    fun dismissDialog() = presenter.dismissDialog()
    fun dismissMessage() = presenter.dismissMessage()

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
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
