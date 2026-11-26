package com.chronoquest.app

import androidx.compose.ui.graphics.toArgb
import com.chronoquest.app.ui.theme.ChronoTokens
import java.io.File
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** 생성 파일(Tokens.kt)이 원본(design-tokens.json)과 어긋나지 않았는지 지킨다. */
class TokensTest {
    private val json = Json.parseToJsonElement(File("../../design-tokens.json").readText()).jsonObject

    private fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    @Test
    fun `색 토큰이 원본과 같다`() {
        val expected = json.getValue("color").jsonObject.mapValues { it.value.jsonPrimitive.content.uppercase() }
        val actual = ChronoTokens.Colors.byKey.mapValues { hex(it.value.toArgb()) }
        assertEquals(expected, actual)
    }

    @Test
    fun `티어 색 토큰이 원본과 같다`() {
        val expected = json.getValue("tier").jsonObject.mapValues { it.value.jsonPrimitive.content.uppercase() }
        val actual = ChronoTokens.Tier.all.mapValues { hex(it.value.toArgb()) }
        assertEquals(expected, actual)
    }

    @Test
    fun `글자 크기는 공통 스케일만 쓴다`() {
        val expected = json.getValue("typeScale").jsonArray.map { it.jsonPrimitive.int }
        assertEquals(listOf(72, 48, 32, 24, 22, 18, 14), expected)
        assertEquals(expected, ChronoTokens.TypeScale.sizes)
    }

    @Test
    fun `간격과 모서리 토큰이 원본과 같다`() {
        fun numbers(key: String) = json.getValue(key).jsonObject.mapValues { it.value.jsonPrimitive.int }
        assertEquals(numbers("space"), ChronoTokens.Space.all.mapValues { it.value.value.toInt() })
        assertEquals(numbers("radius"), ChronoTokens.Radius.all.mapValues { it.value.value.toInt() })
    }
}
