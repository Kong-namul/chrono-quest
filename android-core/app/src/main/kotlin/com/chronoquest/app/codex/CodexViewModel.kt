package com.chronoquest.app.codex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chronoquest.app.AppContainer
import com.chronoquest.app.ui.launchSafely
import kotlinx.coroutines.flow.StateFlow

/** presenter를 viewModelScope에 연결만 한다. 로직은 CodexPresenter에 있다. */
class CodexViewModel(private val presenter: CodexPresenter) : ViewModel() {
    val state: StateFlow<CodexUiState> = presenter.state

    fun onEnter() = act { presenter.onEnter() }
    fun refresh() = act { presenter.refresh() }
    fun onTitleSelected(titleId: Int) = presenter.onTitleSelected(titleId)
    fun onSetRepresentative(titleId: Int) = act { presenter.onSetRepresentative(titleId) }
    fun dismissDetail() = presenter.dismissDetail()
    fun dismissMessage() = presenter.dismissMessage()

    private fun act(block: suspend () -> Unit) {
        launchSafely(presenter::onFailure, block)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { CodexViewModel(CodexPresenter({ container.gameService }, { container.catalog })) }
        }
    }
}
