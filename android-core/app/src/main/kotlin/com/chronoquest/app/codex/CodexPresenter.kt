package com.chronoquest.app.codex

import com.chronoquest.app.ui.ActionGate
import com.chronoquest.app.ui.Messages
import com.chronoquest.core.service.GameService
import com.chronoquest.rules.engine.GameState
import com.chronoquest.rules.titles.Series
import com.chronoquest.rules.titles.TitleCatalog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 칭호 탭(스펙 5.4·5.7): 10계열 × 20종, 보유 수, 다음 획득까지 남은 정도, NEW, 대표 칭호 고르기.
 * NEW는 탭을 열 때 정하고 바로 본 것으로 표시한다. 같은 방문 동안은 남고, 다음에 열면 사라진다.
 */
class CodexPresenter(
    game: () -> GameService,
    catalog: () -> TitleCatalog,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) {
    private val game by lazy(game)
    private val catalog by lazy(catalog)
    private val refreshLock = Mutex()
    private val gate = ActionGate()

    /** 이번 방문에서 NEW로 보여줄 칭호. [onEnter]에서만 바꾼다. */
    private var newBadges: Set<Int> = emptySet()
    private val _state = MutableStateFlow(CodexUiState())
    val state: StateFlow<CodexUiState> = _state.asStateFlow()

    /** 칭호 탭을 열 때 부른다. */
    suspend fun onEnter() = withContext(compute) {
        refreshLock.withLock {
            newBadges = game.newTitleIds()
            game.markTitlesViewed(newBadges)
        }
        refresh()
    }

    suspend fun refresh() = withContext(compute) {
        refreshLock.withLock {
            val gameState = game.state()
            val representative = game.representative()
            val sections = Series.entries.map { series -> section(series, gameState, representative.titleId) }
            _state.update {
                it.copy(
                    loading = false,
                    collected = gameState.codexCount,
                    total = catalog.titles.size,
                    representative = representative.name,
                    sections = sections,
                    detail = it.detail?.let { shown -> sections.flatMap { s -> s.titles }.firstOrNull { row -> row.id == shown.id } },
                )
            }
        }
    }

    fun onTitleSelected(titleId: Int) {
        _state.update { it.copy(detail = it.sections.flatMap { s -> s.titles }.firstOrNull { row -> row.id == titleId }) }
    }

    suspend fun onSetRepresentative(titleId: Int) {
        gate.run {
            withContext(compute) {
                if (game.setRepresentativeTitle(titleId)) {
                    _state.update { it.copy(detail = null) }
                } else {
                    _state.update { it.copy(message = NOT_OWNED_TEXT) }
                }
                refresh()
            }
        }
    }

    fun dismissDetail() {
        _state.update { it.copy(detail = null) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    /** 동작 중 예외. 로딩을 풀고 알려준다. */
    fun onFailure(error: Throwable) {
        _state.update { it.copy(loading = false, message = Messages.FAILURE) }
    }

    private fun section(series: Series, state: GameState, representativeId: Int?): SeriesSection {
        val info = CodexLabels.info(series)
        val value = CodexLabels.counter(series, state)
        val rows = catalog.series(series).sortedBy { it.id }.map { title ->
            val owned = state.inventory[title.id] ?: 0
            TitleRow(
                id = title.id,
                name = title.name,
                condition = info.condition(title.threshold),
                owned = owned,
                progress = CodexLabels.progress(series.kind, title.threshold, value, owned, info.unit),
                isNew = title.id in newBadges,
                isRepresentative = title.id == representativeId,
            )
        }
        return SeriesSection(info.name, rows.count { it.owned > 0 }, rows)
    }

    private companion object {
        const val NOT_OWNED_TEXT = "가진 칭호만 대표로 고를 수 있어."
    }
}
