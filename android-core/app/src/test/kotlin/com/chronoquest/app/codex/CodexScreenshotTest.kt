package com.chronoquest.app.codex

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.app.FLIP5
import com.chronoquest.app.ui.ChronoScaffold
import com.chronoquest.app.ui.MainTab
import com.chronoquest.app.ui.theme.ChronoTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private object Samples {
    private val arrive = listOf(
        TitleRow(1, "최초의 각성", "도착 1회마다", owned = 3, progress = "보유 3개 · 다음까지 1회", isNew = false, isRepresentative = false),
        TitleRow(2, "시간의 여행자", "도착 2회마다", owned = 1, progress = "보유 1개 · 다음까지 1회", isNew = true, isRepresentative = false),
        TitleRow(3, "시계탑의 방문자", "도착 3회마다", owned = 1, progress = "보유 1개 · 다음까지 3회", isNew = true, isRepresentative = false),
        TitleRow(4, "초침의 추적자", "도착 4회마다", owned = 0, progress = "앞으로 1회", isNew = false, isRepresentative = false),
        TitleRow(5, "시간의 사냥꾼", "도착 5회마다", owned = 0, progress = "앞으로 2회", isNew = false, isRepresentative = false),
    )
    private val streak = listOf(
        TitleRow(21, "운명의 첫걸음", "연속 도착 1회", owned = 1, progress = "보유 1개 · 이번 연속에서 받음", isNew = false, isRepresentative = true),
        TitleRow(22, "연속 도착 2", "연속 도착 2회", owned = 1, progress = "보유 1개 · 이번 연속에서 받음", isNew = true, isRepresentative = false),
        TitleRow(23, "연속 도착 3", "연속 도착 3회", owned = 0, progress = "앞으로 1회", isNew = false, isRepresentative = false),
    )

    val overview = CodexUiState(
        loading = false,
        collected = 5,
        total = 200,
        representative = "운명의 첫걸음",
        sections = listOf(
            SeriesSection("누적 도착", owned = 3, titles = arrive),
            SeriesSection("연속 도착", owned = 2, titles = streak),
        ),
    )

    val detail = overview.copy(detail = arrive[1])
}

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = FLIP5)
class CodexScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: CodexUiState) {
        compose.setContent {
            ChronoTheme {
                ChronoScaffold(selectedTab = MainTab.CODEX, onTabSelected = {}) {
                    CodexScreen(state = state, actions = CodexActions())
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("screenshots/$name.png")
    }

    @Test
    fun `칭호 탭`() = shoot("codex_overview", Samples.overview)

    @Test
    fun `칭호 자세히`() = shoot("codex_detail", Samples.detail)
}
