package com.chronoquest.app.schedule

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.app.FLIP5
import com.chronoquest.app.d
import com.chronoquest.app.ui.ChronoScaffold
import com.chronoquest.app.ui.MainTab
import com.chronoquest.app.ui.theme.ChronoTheme
import com.chronoquest.rules.model.QuestKind
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private object Samples {
    private const val WORK_TIME = "출발 08:30 · 도착 09:00"

    val overview = ScheduleUiState(
        loading = false,
        today = d("2026-11-30"),
        templates = listOf(
            TemplateRow(1, "출근", "출근", "평일", WORK_TIME),
            TemplateRow(2, "헬스", "약속", "화 목", "출발 18:30 · 도착 19:00"),
        ),
        days = listOf(
            UpcomingDayRow(
                d("2026-11-30"), "오늘 · 11/30 (월)", vacation = false, canToggleVacation = false,
                quests = listOf(UpcomingQuestRow("a", "출근", WORK_TIME, canceled = false, canCancel = false, canRestore = false)),
            ),
            UpcomingDayRow(
                d("2026-12-01"), "내일 · 12/1 (화)", vacation = false, canToggleVacation = true,
                quests = listOf(
                    UpcomingQuestRow("b", "출근", WORK_TIME, canceled = true, canCancel = false, canRestore = true),
                    UpcomingQuestRow("c", "헬스", "출발 18:30 · 도착 19:00", canceled = false, canCancel = true, canRestore = false),
                ),
            ),
            UpcomingDayRow(
                d("2026-12-02"), "12/2 (수)", vacation = true, canToggleVacation = true,
                quests = listOf(UpcomingQuestRow("d", "출근", WORK_TIME, canceled = false, canCancel = true, canRestore = false)),
            ),
            UpcomingDayRow(d("2026-12-05"), "12/5 (토)", vacation = false, canToggleVacation = true, quests = emptyList()),
        ),
        laterVacations = listOf(VacationRow(d("2026-12-24"), "12/24 (목)")),
    )

    val templateDialog = overview.copy(
        dialog = ScheduleDialog.EditTemplate(
            TemplateForm(
                id = 2,
                title = "헬스",
                kind = QuestKind.OTHER,
                days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
                due = LocalTime.of(19, 0),
                depart = LocalTime.of(18, 30),
            ),
        ),
    )

    val oneOffDialog = overview.copy(
        dialog = ScheduleDialog.AddOneOff(
            OneOffForm(date = d("2026-12-02"), title = "보드게임", due = LocalTime.of(14, 0), depart = LocalTime.of(13, 20)),
        ),
    )
}

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = FLIP5)
class ScheduleScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: ScheduleUiState) {
        compose.setContent {
            ChronoTheme {
                ChronoScaffold(selectedTab = MainTab.SCHEDULE, onTabSelected = {}) {
                    ScheduleScreen(state = state, actions = ScheduleActions())
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("screenshots/$name.png")
    }

    @Test
    fun `일정 탭`() = shoot("schedule_overview", Samples.overview)

    @Test
    fun `반복 일정 편집 창`() = shoot("schedule_template_dialog", Samples.templateDialog)

    @Test
    fun `단발 일정 추가 창`() = shoot("schedule_one_off_dialog", Samples.oneOffDialog)
}
