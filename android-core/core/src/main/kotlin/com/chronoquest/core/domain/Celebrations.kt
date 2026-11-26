package com.chronoquest.core.domain

import com.chronoquest.rules.engine.GameState
import com.chronoquest.rules.engine.GrantType
import com.chronoquest.rules.model.Tier

enum class CelebrationKind { TITLE, PERIOD_BONUS, PET_EVOLVED, BOSS_RESULT, TIER_UP }

/** "오늘의 결과"·알림으로 보여줄 연출 한 건. [key]는 CelebrationSeen과 비교하는 안정 키다. */
data class Celebration(
    val key: String,
    val kind: CelebrationKind,
    val titleId: Int? = null,
    val xp: Int = 0,
    val detail: String = "",
)

/**
 * 연출 목록 (스펙 5.10, 인계 메모 A2).
 * - 엔진 지급 키를 그대로 쓴다.
 * - 티어 승급은 엔진 지급이 없으므로 `tier:<TIER>` 키로 여기서 만든다.
 * - 일일 XP(도착·출발·기상·행동통제)는 연출 대상이 아니다.
 */
object Celebrations {
    private val PERIOD_BONUS_TYPES = setOf(
        GrantType.XP_PERFECT_WEEK,
        GrantType.XP_THREE_WEEKS,
        GrantType.XP_PERFECT_MONTH,
    )

    /** 지급 시간순 연출 다음에 티어 승급(낮은 티어부터). */
    fun all(state: GameState): List<Celebration> {
        val fromGrants = state.grants.mapNotNull { g ->
            when {
                g.type == GrantType.TITLE ->
                    Celebration(g.key, CelebrationKind.TITLE, titleId = g.titleId, detail = g.detail)
                g.type in PERIOD_BONUS_TYPES ->
                    Celebration(g.key, CelebrationKind.PERIOD_BONUS, xp = g.xp, detail = g.type.name)
                g.type == GrantType.PET_EVOLVED ->
                    Celebration(g.key, CelebrationKind.PET_EVOLVED, detail = g.detail)
                g.type == GrantType.BOSS_RESULT ->
                    Celebration(g.key, CelebrationKind.BOSS_RESULT, detail = g.detail)
                else -> null
            }
        }
        val tierUps = Tier.entries
            .filter { it != Tier.BRONZE && state.xp >= it.minXp }
            .map { Celebration("tier:${it.name}", CelebrationKind.TIER_UP, detail = it.title) }
        return fromGrants + tierUps
    }

    fun unseen(state: GameState, seenKeys: Set<String>): List<Celebration> =
        all(state).filter { it.key !in seenKeys }

    /** 칭호 화면 NEW 표시용 본 기록 키. 연출 키와 같은 CelebrationSeen 테이블을 쓴다. */
    fun codexKey(titleId: Int): String = "codex:$titleId"

    /** 가진 칭호 중 칭호 화면에서 아직 보지 않은 것(ID 순). */
    fun newTitleIds(state: GameState, seenKeys: Set<String>): Set<Int> =
        state.inventory.filterValues { it > 0 }.keys.filterTo(LinkedHashSet()) { codexKey(it) !in seenKeys }
}
