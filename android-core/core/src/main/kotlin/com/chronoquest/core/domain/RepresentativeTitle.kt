package com.chronoquest.core.domain

import com.chronoquest.rules.engine.GameState
import com.chronoquest.rules.engine.GrantType
import com.chronoquest.rules.titles.TitleCatalog

/** 홈에 띄우는 대표 칭호 (스펙 5.7, 인계 메모 A2). */
object RepresentativeTitle {
    const val NONE = "미각성자"

    /** 고른 칭호를 아직 갖고 있으면 그것, 아니면 가장 최근에 얻은 칭호, 하나도 없으면 미각성자. */
    fun resolve(selectedId: Int?, state: GameState, catalog: TitleCatalog): String {
        if (selectedId != null && (state.inventory[selectedId] ?: 0) > 0) return catalog.title(selectedId).name
        val latest = state.grants.lastOrNull { it.type == GrantType.TITLE }?.titleId ?: return NONE
        return catalog.title(latest).name
    }
}
