package com.chronoquest.app.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.app.FLIP5
import com.chronoquest.app.d
import com.chronoquest.app.ui.ChronoScaffold
import com.chronoquest.app.ui.MainTab
import com.chronoquest.app.ui.theme.ChronoTheme
import com.chronoquest.core.domain.Celebration
import com.chronoquest.core.domain.CelebrationKind
import com.chronoquest.rules.model.Tier
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private object Samples {
    val firstDay = HomeUiState(
        loading = false,
        tier = Tier.BRONZE,
        representativeTitle = "미각성자",
        xp = 0,
        nextTierXp = 100,
        quests = listOf(
            QuestCard("w", "출근", "08:30", "09:00", CardStatus.WAITING, canDepart = true, canArrive = true),
            QuestCard("o", "보드게임", "13:20", "14:00", CardStatus.WAITING, canDepart = true, canArrive = true),
        ),
    )

    val inProgress = firstDay.copy(
        representativeTitle = "운명의 첫걸음",
        xp = 50,
        quests = listOf(
            QuestCard("w", "출근", "08:30", "09:00", CardStatus.ON_TIME, canDepart = false, canArrive = false),
            QuestCard("o", "보드게임", "13:20", "14:00", CardStatus.DEPARTED, canDepart = false, canArrive = true),
        ),
    )

    val cleanupAndCelebration = inProgress.copy(
        tier = Tier.SILVER,
        xp = 120,
        nextTierXp = 300,
        cleanup = listOf(CleanupCard("c", "출근", "11/27 (금) 09:00", d("2026-11-27"))),
        celebrations = listOf(
            Celebration("title:1:1", CelebrationKind.TITLE, titleId = 1, detail = "최초의 각성"),
            Celebration("tier:SILVER", CelebrationKind.TIER_UP, detail = "시간의 수련자"),
        ),
    )

    val confirmEarly = inProgress.copy(dialog = HomeDialog.ConfirmEarly("o", PendingAction.Arrive(true), 45))

    val reschedule = inProgress.copy(
        dialog = HomeDialog.Reschedule("o", PendingAction.Arrive(true), LocalTime.of(14, 0), LocalTime.of(13, 20)),
    )
}

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = FLIP5)
class HomeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: HomeUiState) {
        compose.setContent {
            ChronoTheme {
                ChronoScaffold(selectedTab = MainTab.HOME, onTabSelected = {}) {
                    HomeScreen(state = state, actions = HomeActions())
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("screenshots/$name.png")
    }

    @Test
    fun `첫날`() = shoot("home_first_day", Samples.firstDay)

    @Test
    fun `진행 중`() = shoot("home_in_progress", Samples.inProgress)

    @Test
    fun `정리 필요와 새 칭호`() = shoot("home_cleanup_celebration", Samples.cleanupAndCelebration)

    @Test
    fun `30분 이상 이른 기록 확인 창`() = shoot("home_confirm_early", Samples.confirmEarly)

    @Test
    fun `일정 시각 변경 창`() = shoot("home_reschedule", Samples.reschedule)
}
