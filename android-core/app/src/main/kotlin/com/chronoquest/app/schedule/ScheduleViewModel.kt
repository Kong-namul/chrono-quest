package com.chronoquest.app.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chronoquest.app.AppContainer
import com.chronoquest.app.ui.launchSafely
import java.time.LocalDate
import kotlinx.coroutines.flow.StateFlow

/** presenter를 viewModelScope에 연결만 한다. 로직은 SchedulePresenter에 있다. */
class ScheduleViewModel(private val presenter: SchedulePresenter) : ViewModel() {
    val state: StateFlow<ScheduleUiState> = presenter.state

    fun refresh() = act { presenter.refresh() }
    fun onAddTemplate() = presenter.onAddTemplate()
    fun onEditTemplate(id: Long) = act { presenter.onEditTemplate(id) }
    fun onTemplateSaved(dialog: ScheduleDialog.EditTemplate, form: TemplateForm) = act { presenter.onTemplateSaved(dialog, form) }
    fun onDeleteTemplateRequested(id: Long, title: String) = presenter.onDeleteTemplateRequested(id, title)
    fun onDeleteTemplateConfirmed(dialog: ScheduleDialog.ConfirmDeleteTemplate) = act { presenter.onDeleteTemplateConfirmed(dialog) }
    fun onAddOneOff() = presenter.onAddOneOff()
    fun onOneOffSaved(dialog: ScheduleDialog.AddOneOff, form: OneOffForm) = act { presenter.onOneOffSaved(dialog, form) }
    fun onCancelQuest(questId: String) = act { presenter.onCancelQuest(questId) }
    fun onRestoreQuest(questId: String) = act { presenter.onRestoreQuest(questId) }
    fun onVacationToggled(date: LocalDate, on: Boolean) = act { presenter.onVacationToggled(date, on) }
    fun onPickVacationDate() = presenter.onPickVacationDate()
    fun onVacationDatePicked(date: LocalDate) = act { presenter.onVacationDatePicked(date) }
    fun dismissDialog() = presenter.dismissDialog()
    fun dismissMessage() = presenter.dismissMessage()

    private fun act(block: suspend () -> Unit) {
        launchSafely(presenter::onFailure, block)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ScheduleViewModel(
                    SchedulePresenter({ container.gameService }, { container.scheduleService }, container.notifier),
                )
            }
        }
    }
}
