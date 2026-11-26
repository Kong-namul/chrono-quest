package com.chronoquest.core.domain

import com.chronoquest.rules.engine.GameState
import com.chronoquest.rules.engine.GrantType
import com.chronoquest.rules.titles.TitleCatalog

/** 대표 칭호 판단 결과. [titleId]가 null이면 칭호가 없어 [name]은 미각성자다. */
data class TitleChoice(val titleId: Int?, val name: String)

/**
 * 홈에 띄우는 대표 칭호 (스펙 5.7, 인계 메모 A2). 저장은 GameService가 한다.
 * - 저장된 칭호를 아직 갖고 있으면 그대로 쓴다. 새 칭호를 얻어도 바꾸지 않는다.
 * - 저장된 것이 없거나 더는 갖고 있지 않으면 가장 최근에 얻은 칭호를 고른다.
 * - 칭호가 하나도 없으면 미각성자다.
 */
object RepresentativeTitle {
    const val NONE = "미각성자"

    fun resolve(selectedId: Int?, state: GameState, catalog: TitleCatalog): TitleChoice {
        if (selectedId != null && owns(state, selectedId)) return TitleChoice(selectedId, catalog.title(selectedId).name)
        val latest = state.grants.lastOrNull { it.type == GrantType.TITLE && it.titleId?.let { id -> owns(state, id) } == true }
            ?.titleId
            ?: return TitleChoice(null, NONE)
        return TitleChoice(latest, catalog.title(latest).name)
    }

    private fun owns(state: GameState, titleId: Int): Boolean = (state.inventory[titleId] ?: 0) > 0
}
