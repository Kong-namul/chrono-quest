package com.chronoquest.app

import androidx.compose.ui.graphics.Color
import com.chronoquest.app.ui.theme.ChronoColorScheme
import com.chronoquest.app.ui.theme.ChronoTokens
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun `화면에 쓰이는 색 역할은 모두 디자인 토큰이다`() {
        val s = ChronoColorScheme
        val roles: Map<String, Color> = mapOf(
            "primary" to s.primary, "onPrimary" to s.onPrimary,
            "primaryContainer" to s.primaryContainer, "onPrimaryContainer" to s.onPrimaryContainer,
            "secondary" to s.secondary, "onSecondary" to s.onSecondary,
            "secondaryContainer" to s.secondaryContainer, "onSecondaryContainer" to s.onSecondaryContainer,
            "background" to s.background, "onBackground" to s.onBackground,
            "surface" to s.surface, "onSurface" to s.onSurface,
            "surfaceVariant" to s.surfaceVariant, "onSurfaceVariant" to s.onSurfaceVariant,
            "outline" to s.outline, "outlineVariant" to s.outlineVariant,
            "surfaceTint" to s.surfaceTint, "surfaceBright" to s.surfaceBright, "surfaceDim" to s.surfaceDim,
            "surfaceContainerLowest" to s.surfaceContainerLowest, "surfaceContainerLow" to s.surfaceContainerLow,
            "surfaceContainer" to s.surfaceContainer, "surfaceContainerHigh" to s.surfaceContainerHigh,
            "surfaceContainerHighest" to s.surfaceContainerHighest,
        )
        val tokens = ChronoTokens.Colors.byKey.values.toSet()
        roles.forEach { (name, color) -> assertTrue("$name 색이 토큰이 아니다: $color", color in tokens) }
    }
}
