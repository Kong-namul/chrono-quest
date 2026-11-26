package com.chronoquest.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.chronoquest.app.R
import com.chronoquest.rules.model.Tier

/** 앱 글꼴 Pretendard (OFL, ASSETS.md). */
val ChronoFont: FontFamily = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

internal val ChronoColorScheme = lightColorScheme(
    primary = ChronoTokens.Colors.Blue500,
    onPrimary = ChronoTokens.Colors.Bg1,
    primaryContainer = ChronoTokens.Colors.Blue50,
    onPrimaryContainer = ChronoTokens.Colors.Blue700,
    secondary = ChronoTokens.Colors.Gold500,
    onSecondary = ChronoTokens.Colors.Text1,
    secondaryContainer = ChronoTokens.Colors.Yellow50,
    onSecondaryContainer = ChronoTokens.Colors.Gold700,
    background = ChronoTokens.Colors.Bg1,
    onBackground = ChronoTokens.Colors.Text1,
    surface = ChronoTokens.Colors.Bg1,
    onSurface = ChronoTokens.Colors.Text1,
    surfaceVariant = ChronoTokens.Colors.Bg2,
    onSurfaceVariant = ChronoTokens.Colors.Text2,
    outline = ChronoTokens.Colors.Border,
    outlineVariant = ChronoTokens.Colors.Border,
    surfaceTint = ChronoTokens.Colors.Bg1,
    surfaceBright = ChronoTokens.Colors.Bg1,
    surfaceDim = ChronoTokens.Colors.Bg2,
    surfaceContainerLowest = ChronoTokens.Colors.Bg1,
    surfaceContainerLow = ChronoTokens.Colors.Bg1,
    surfaceContainer = ChronoTokens.Colors.Bg2,
    surfaceContainerHigh = ChronoTokens.Colors.Bg1,
    surfaceContainerHighest = ChronoTokens.Colors.Bg2,
)

private fun style(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = ChronoFont, fontSize = size, fontWeight = weight, lineHeight = size * 1.4f)

/** 글자 크기는 공통 스케일(72/48/32/24/22/18/14)만 쓴다. */
private val ChronoTypography = Typography(
    displayLarge = style(ChronoTokens.TypeScale.S72, FontWeight.Bold),
    displayMedium = style(ChronoTokens.TypeScale.S48, FontWeight.Bold),
    displaySmall = style(ChronoTokens.TypeScale.S32, FontWeight.Bold),
    headlineLarge = style(ChronoTokens.TypeScale.S32, FontWeight.Bold),
    headlineMedium = style(ChronoTokens.TypeScale.S24, FontWeight.Bold),
    headlineSmall = style(ChronoTokens.TypeScale.S22, FontWeight.Bold),
    titleLarge = style(ChronoTokens.TypeScale.S22, FontWeight.Medium),
    titleMedium = style(ChronoTokens.TypeScale.S18, FontWeight.Medium),
    titleSmall = style(ChronoTokens.TypeScale.S14, FontWeight.Medium),
    bodyLarge = style(ChronoTokens.TypeScale.S18),
    bodyMedium = style(ChronoTokens.TypeScale.S14),
    bodySmall = style(ChronoTokens.TypeScale.S14),
    labelLarge = style(ChronoTokens.TypeScale.S14, FontWeight.Medium),
    labelMedium = style(ChronoTokens.TypeScale.S14),
    labelSmall = style(ChronoTokens.TypeScale.S14),
)

@Composable
fun ChronoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ChronoColorScheme, typography = ChronoTypography, content = content)
}

/** 티어 엠블럼·배지 색. 토큰에만 있는 값을 쓴다. */
fun tierColor(tier: Tier): Color = ChronoTokens.Tier.all.getValue(tier.name)

/** 티어 배지 위 글자색. 밝은 골드만 어두운 글자를 쓴다. */
fun onTierColor(tier: Tier): Color =
    if (tier == Tier.GOLD) ChronoTokens.Colors.Text1 else ChronoTokens.Colors.Bg1
