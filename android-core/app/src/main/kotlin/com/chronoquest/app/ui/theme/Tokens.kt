// 생성 파일: tools/gen-tokens/gen_tokens.py가 design-tokens.json에서 만든다. 직접 고치지 말 것.
package com.chronoquest.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ChronoTokens {
    object Colors {
        val Bg1 = Color(0xFFFFFFFF)
        val Bg2 = Color(0xFFF7F8FC)
        val Text1 = Color(0xFF1A1C24)
        val Text2 = Color(0xFF6A6D7A)
        val Border = Color(0xFFE6E8F0)
        val Blue500 = Color(0xFF2F6FED)
        val Blue700 = Color(0xFF1F55C4)
        val Blue50 = Color(0xFFEAF1FE)
        val Gold500 = Color(0xFFE8B530)
        val Gold700 = Color(0xFFB8861B)
        val Yellow400 = Color(0xFFFFD54A)
        val Yellow50 = Color(0xFFFFF3C4)

        val byKey: Map<String, Color> = mapOf(
            "bg.1" to Bg1,
            "bg.2" to Bg2,
            "text.1" to Text1,
            "text.2" to Text2,
            "border" to Border,
            "blue.500" to Blue500,
            "blue.700" to Blue700,
            "blue.50" to Blue50,
            "gold.500" to Gold500,
            "gold.700" to Gold700,
            "yellow.400" to Yellow400,
            "yellow.50" to Yellow50,
        )
    }

    object Tier {
        val all: Map<String, Color> = mapOf(
        )
    }

    object TypeScale {
        val S72 = 72.sp
        val S48 = 48.sp
        val S32 = 32.sp
        val S24 = 24.sp
        val S22 = 22.sp
        val S18 = 18.sp
        val S14 = 14.sp

        val sizes: List<Int> = listOf(72, 48, 32, 24, 22, 18, 14)
    }

    object Space {
        val Xs = 4.dp
        val Sm = 8.dp
        val Md = 12.dp
        val Lg = 16.dp
        val Xl = 24.dp

        val all: Map<String, Dp> = mapOf(
            "xs" to Xs,
            "sm" to Sm,
            "md" to Md,
            "lg" to Lg,
            "xl" to Xl,
        )
    }

    object Radius {
        val Sm = 8.dp
        val Md = 12.dp
        val Lg = 16.dp

        val all: Map<String, Dp> = mapOf(
            "sm" to Sm,
            "md" to Md,
            "lg" to Lg,
        )
    }
}
