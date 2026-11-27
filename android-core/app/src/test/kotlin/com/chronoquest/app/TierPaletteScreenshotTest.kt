package com.chronoquest.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.app.ui.theme.ChronoTheme
import com.chronoquest.app.ui.theme.ChronoTokens
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val TIERS = listOf("BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND", "MASTER", "LEGEND", "MYTHIC")

private val PALETTE_A = listOf("#B8733A", "#8E95A3", "#E8B530", "#2FA89C", "#3C8DDE", "#D64550", "#F07A1A", "#1F2A5A")
private val PALETTE_B = listOf("#B8733A", "#8E95A3", "#E8B530", "#2FA89C", "#3C8DDE", "#8A4FD8", "#D64550", "#F07A1A")

private fun color(hex: String) = Color(android.graphics.Color.parseColor(hex))

/** 시안 색 견본 크기(이 테스트 전용). */
private val SWATCH = 44.dp

@Composable
private fun PaletteColumn(title: String, palette: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Sm)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = ChronoTokens.Colors.Text1)
        TIERS.zip(palette).forEach { (tier, hex) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(SWATCH).background(color(hex), RoundedCornerShape(ChronoTokens.Radius.Md)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tier.take(1), style = MaterialTheme.typography.labelLarge, color = if (tier == "GOLD") ChronoTokens.Colors.Text1 else ChronoTokens.Colors.Bg1)
                }
                Text("  $tier", style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Text1)
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = FLIP5)
class TierPaletteScreenshotTest {
    @Test
    fun `티어 색 시안 두 가지`() {
        captureRoboImage("screenshots/tier_palettes.png") {
            ChronoTheme {
                Row(
                    modifier = Modifier.fillMaxSize().background(ChronoTokens.Colors.Bg1).padding(ChronoTokens.Space.Xl),
                    horizontalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Xl),
                ) {
                    PaletteColumn("A. 금속·보석", PALETTE_A)
                    PaletteColumn("B. 게임 관례형", PALETTE_B)
                }
            }
        }
    }
}
