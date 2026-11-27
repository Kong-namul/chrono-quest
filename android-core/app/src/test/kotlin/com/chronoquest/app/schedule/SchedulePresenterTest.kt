package com.chronoquest.app.schedule

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.app.MutableClock
import com.chronoquest.app.d
import com.chronoquest.app.dt
import com.chronoquest.app.inMemoryDb
import com.chronoquest.app.notify.SettlementNotifier
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.ScheduleTemplateEntity
import com.chronoquest.core.domain.TemplateRules
import com.chronoquest.core.service.GameService
import com.chronoquest.core.service.ScheduleService
import com.chronoquest.rules.model.QuestKind
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SchedulePresenterTest {
    private lateinit var db: ChronoDatabase
    private lateinit var presenter: SchedulePresenter
    private val clock = MutableClock(dt("2026-11-30", "08:00"))
    private val notified = mutableListOf<Int>()

    @Before
    fun setUp() {
        db = inMemoryDb()
        presenter = SchedulePresenter(
            { GameService(db, clock) },
            { ScheduleService(db, clock) },
            SettlementNotifier { xp, _ -> notified += xp },
            Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val state get() = presenter.state.value

    private suspend fun saveWeekdayWork() {
        ScheduleService(db, clock).saveTemplate(
            ScheduleTemplateEntity(
                title = "출근",
                kind = QuestKind.WORK,
                daysMask = TemplateRules.maskOf(TemplateForm.WEEKDAYS),
                dueTime = LocalTime.of(9, 0),
                targetDepartTime = LocalTime.of(8, 30),
            ),
        )
    }

    @Test
    fun `새로고침하면 반복 일정과 오늘부터 7일을 보여준다`() = runTest {
        saveWeekdayWork()
        presenter.refresh()
        assertFalse(state.loading)
        assertEquals(d("2026-11-30"), state.today)
        assertEquals(listOf(TemplateRow(1, "출근", "출근", "평일", "출발 08:30 · 도착 09:00")), state.templates)
        assertEquals(7, state.days.size)
        assertEquals("오늘 · 11/30 (월)", state.days[0].dateLabel)
        assertEquals("내일 · 12/1 (화)", state.days[1].dateLabel)
        assertEquals("12/2 (수)", state.days[2].dateLabel)
        assertEquals(
            listOf(UpcomingQuestRow("2026-11-30-WORK-09:00", "출근", "출발 08:30 · 도착 09:00", canceled = false, canCancel = true, canRestore = false)),
            state.days[0].quests,
        )
        assertTrue(state.days[5].quests.isEmpty())   // 12/5 토요일
        assertTrue(state.days.all { it.canToggleVacation && !it.vacation })
    }

    @Test
    fun `반복 일정을 추가하고 고치고 지운다`() = runTest {
        presenter.refresh()
        presenter.onAddTemplate()
        val add = state.dialog as ScheduleDialog.EditTemplate
        assertEquals(TemplateForm(), add.form)
        presenter.onTemplateSaved(
            add,
            TemplateForm(
                title = "헬스",
                kind = QuestKind.OTHER,
                days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
                due = LocalTime.of(19, 0),
                depart = LocalTime.of(18, 30),
            ),
        )
        assertNull(state.dialog)
        val row = state.templates.single()
        assertEquals(TemplateRow(row.id, "헬스", "약속", "화 목", "출발 18:30 · 도착 19:00"), row)

        presenter.onEditTemplate(row.id)
        val edit = state.dialog as ScheduleDialog.EditTemplate
        assertEquals("헬스", edit.form.title)
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), edit.form.days)
        presenter.onTemplateSaved(edit, edit.form.copy(due = LocalTime.of(20, 0), depart = LocalTime.of(19, 30)))
        assertEquals(listOf("출발 19:30 · 도착 20:00"), state.templates.map { it.timeLabel })

        presenter.onDeleteTemplateRequested(row.id, "헬스")
        val confirm = state.dialog as ScheduleDialog.ConfirmDeleteTemplate
        assertEquals(ScheduleDialog.ConfirmDeleteTemplate(row.id, "헬스"), confirm)
        presenter.onDeleteTemplateConfirmed(confirm)
        assertNull(state.dialog)
        assertTrue(state.templates.isEmpty())
    }

    @Test
    fun `잘못된 반복 일정은 창을 두고 이유를 알려준다`() = runTest {
        presenter.refresh()
        presenter.onAddTemplate()
        val add = state.dialog as ScheduleDialog.EditTemplate
        presenter.onTemplateSaved(add, TemplateForm(title = "  "))
        assertEquals(add, state.dialog)
        assertEquals("일정 이름이 비어 있어.", state.message)
        presenter.onTemplateSaved(add, TemplateForm(title = "출근", days = emptySet()))
        assertEquals("요일을 하나 이상 골라줘.", state.message)
        assertTrue(state.templates.isEmpty())
    }

    @Test
    fun `단발 일정을 추가하고 같은 것은 막는다`() = runTest {
        presenter.refresh()
        presenter.onAddOneOff()
        val add = state.dialog as ScheduleDialog.AddOneOff
        assertEquals(OneOffForm(date = d("2026-11-30")), add.form)
        val form = OneOffForm(date = d("2026-12-02"), title = "보드게임", due = LocalTime.of(14, 0), depart = LocalTime.of(13, 20))
        presenter.onOneOffSaved(add, form)
        assertNull(state.dialog)
        assertEquals(listOf("보드게임"), state.days[2].quests.map { it.title })
        presenter.onAddOneOff()
        val again = state.dialog as ScheduleDialog.AddOneOff
        presenter.onOneOffSaved(again, form)
        assertEquals(again, state.dialog)
        assertEquals("같은 시각에 같은 종류 일정이 이미 있어.", state.message)
    }

    @Test
    fun `일정을 취소하고 되돌린다`() = runTest {
        saveWeekdayWork()
        presenter.refresh()
        presenter.onCancelQuest("2026-12-01-WORK-09:00")
        val canceled = state.days[1].quests.single()
        assertTrue(canceled.canceled)
        assertTrue(canceled.canRestore)
        assertFalse(canceled.canCancel)
        presenter.onRestoreQuest("2026-12-01-WORK-09:00")
        assertFalse(state.days[1].quests.single().canceled)
        assertTrue(notified.isEmpty())
    }

    @Test
    fun `기록이 있는 일정과 그날은 바꿀 수 없다`() = runTest {
        saveWeekdayWork()
        clock.now = dt("2026-11-30", "08:25")
        GameService(db, clock).depart("2026-11-30-WORK-09:00")
        presenter.refresh()
        assertFalse(state.days[0].quests.single().canCancel)
        assertFalse(state.days[0].canToggleVacation)
        presenter.onCancelQuest("2026-11-30-WORK-09:00")
        assertEquals("기록이 있는 일정은 취소할 수 없어.", state.message)
        presenter.dismissMessage()
        presenter.onVacationToggled(d("2026-11-30"), true)
        assertEquals("그날 기록이 있어서 휴가로 바꿀 수 없어.", state.message)
    }

    @Test
    fun `휴가를 켜고 끄고 7일 뒤 휴가는 날짜를 골라 넣는다`() = runTest {
        presenter.refresh()
        presenter.onVacationToggled(d("2026-12-01"), true)
        assertTrue(state.days[1].vacation)
        presenter.onVacationToggled(d("2026-12-01"), false)
        assertFalse(state.days[1].vacation)
        presenter.onPickVacationDate()
        assertEquals(ScheduleDialog.PickVacationDate, state.dialog)
        presenter.onVacationDatePicked(d("2026-12-24"))
        assertNull(state.dialog)
        assertEquals(listOf(VacationRow(d("2026-12-24"), "12/24 (목)")), state.laterVacations)
        presenter.onVacationToggled(d("2026-12-24"), false)
        assertTrue(state.laterVacations.isEmpty())
    }

    @Test
    fun `실패하면 로딩을 풀고 알려준다`() = runTest {
        presenter.onFailure(IllegalStateException("고장"))
        assertFalse(state.loading)
        assertEquals("문제가 생겼어. 잠시 뒤에 다시 해줘.", state.message)
    }
}
