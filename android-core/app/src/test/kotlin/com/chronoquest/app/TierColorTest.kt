package com.chronoquest.app

import com.chronoquest.app.ui.theme.ChronoTokens
import com.chronoquest.app.ui.theme.onTierColor
import com.chronoquest.app.ui.theme.tierColor
import com.chronoquest.rules.model.Tier
import kotlin.test.assertEquals
import org.junit.Test

class TierColorTest {
    @Test
    fun `모든 티어에 색이 있다`() {
        assertEquals(Tier.entries.map { it.name }.toSet(), ChronoTokens.Tier.all.keys)
        Tier.entries.forEach { assertEquals(ChronoTokens.Tier.all.getValue(it.name), tierColor(it)) }
    }

    @Test
    fun `골드 배지 글자는 어두운 색이고 나머지는 흰색이다`() {
        assertEquals(ChronoTokens.Colors.Text1, onTierColor(Tier.GOLD))
        assertEquals(ChronoTokens.Colors.Bg1, onTierColor(Tier.BRONZE))
    }
}
