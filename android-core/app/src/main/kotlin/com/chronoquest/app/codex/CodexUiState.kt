package com.chronoquest.app.codex

/** 칭호 한 줄. [progress]는 보유 수와 다음 획득까지 남은 정도를 한 줄로 쓴 것이다. */
data class TitleRow(
    val id: Int,
    val name: String,
    val condition: String,
    val owned: Int,
    val progress: String,
    val isNew: Boolean,
    val isRepresentative: Boolean,
)

/** 칭호 계열 하나(20종). [owned]는 하나 이상 가진 칭호 수다. */
data class SeriesSection(val name: String, val owned: Int, val titles: List<TitleRow>)

data class CodexUiState(
    val loading: Boolean = true,
    /** 도감에 오른 고유 칭호 수(스펙 5.4). */
    val collected: Int = 0,
    val total: Int = 0,
    val representative: String = "미각성자",
    val sections: List<SeriesSection> = emptyList(),
    /** 눌러서 자세히 보는 칭호. */
    val detail: TitleRow? = null,
    val message: String? = null,
)
