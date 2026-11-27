package com.chronoquest.app.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.app.MutableClock
import com.chronoquest.app.d
import com.chronoquest.app.dt
import com.chronoquest.app.inMemoryDb
import com.chronoquest.app.notify.SettlementNotifier
import com.chronoquest.app.questEntity
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.core.data.VacationEntity
import com.chronoquest.core.domain.Celebration
import com.chronoquest.core.service.GameService
import com.chronoquest.core.service.ScheduleService
import com.chronoquest.rules.model.QuestKind
import com.chronoquest.rules.model.Tier
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
class HomePresenterTest {
    private lateinit var db: ChronoDatabase
    private lateinit var presenter: HomePresenter
    private val clock = MutableClock(dt("2026-11-30", "08:00"))
    private val notified = mutableListOf<Pair<Int, List<Celebration>>>()
    private val work = questEntity("2026-11-30")
    private val other = questEntity("2026-11-30", kind = QuestKind.OTHER, due = "19:00", depart = "18:30", title = "약속")

    @Before
    fun setUp() = runTest {
        db = inMemoryDb()
        db.questDao().insertAll(listOf(other, work))
        val notifier = SettlementNotifier { xp, celebrations -> notified += xp to celebrations }
        presenter = HomePresenter({ GameService(db, clock) }, { ScheduleService(db, clock) }, notifier, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val state get() = presenter.state.value

    private fun card(id: String): QuestCard = state.quests.single { it.id == id }

    @Test
    fun `새로고침하면 오늘 퀘스트와 티어와 대표 칭호를 보여준다`() = runTest {
        presenter.refresh()
        assertFalse(state.loading)
        assertEquals(listOf(work.id, other.id), state.quests.map { it.id })
        assertTrue(state.quests.all { it.status == CardStatus.WAITING && it.canDepart && it.canArrive })
        assertEquals("08:30", card(work.id).departLabel)
        assertEquals("09:00", card(work.id).dueLabel)
        assertEquals(Tier.BRONZE, state.tier)
        assertEquals("미각성자", state.representativeTitle)
        assertEquals(0, state.xp)
        assertEquals(100, state.nextTierXp)
        assertTrue(state.celebrations.isEmpty())
    }

    @Test
    fun `출발하면 행동통제를 묻고 정산 알림을 보낸다`() = runTest {
        clock.now = dt("2026-11-30", "08:25")
        presenter.onDepart(work.id)
        assertEquals(HomeDialog.AskSelfControl(d("2026-11-30")), state.dialog)
        assertEquals(CardStatus.DEPARTED, card(work.id).status)
        assertFalse(card(work.id).canDepart)
        assertTrue(card(work.id).canArrive)
        assertEquals(10, notified.single().first)
        assertTrue(state.celebrations.any { it.key == "title:41:1" })
    }

    @Test
    fun `행동통제에 답하면 창이 닫히고 XP가 오른다`() = runTest {
        clock.now = dt("2026-11-30", "08:25")
        presenter.onDepart(work.id)
        presenter.onSelfControlAnswered(d("2026-11-30"), true)
        assertNull(state.dialog)
        assertEquals(15, state.xp)
    }

    @Test
    fun `도착은 안전 여부를 먼저 묻는다`() = runTest {
        presenter.refresh()
        presenter.onArrive(work.id)
        assertEquals(HomeDialog.AskSafety(work.id), state.dialog)
        clock.now = dt("2026-11-30", "08:55")
        presenter.onSafetyAnswered(work.id, true)
        assertNull(state.dialog)
        assertEquals(CardStatus.ON_TIME, card(work.id).status)
        assertFalse(card(work.id).canArrive)
        assertEquals(20, state.xp)
        assertEquals("운명의 첫걸음", state.representativeTitle)
    }

    @Test
    fun `30분 이상 이른 도착은 확인하고 그대로 기록할 수 있다`() = runTest {
        clock.now = dt("2026-11-30", "08:55")
        presenter.onSafetyAnswered(other.id, true)
        val dialog = state.dialog as HomeDialog.ConfirmEarly
        assertEquals(other.id, dialog.questId)
        assertEquals(605L, dialog.minutesEarly)
        assertEquals(PendingAction.Arrive(true), dialog.action)
        assertTrue(dialog.canReschedule)
        presenter.onRecordAnyway(dialog)
        assertNull(state.dialog)
        assertEquals(CardStatus.EARLY, card(other.id).status)
    }

    @Test
    fun `일정을 고치면 새 시각으로 기록한다`() = runTest {
        clock.now = dt("2026-11-30", "08:55")
        presenter.onSafetyAnswered(other.id, true)
        presenter.onEditScheduleRequested(state.dialog as HomeDialog.ConfirmEarly)
        val reschedule = state.dialog as HomeDialog.Reschedule
        assertEquals(LocalTime.parse("19:00"), reschedule.due)
        assertEquals(LocalTime.parse("18:30"), reschedule.depart)
        presenter.onRescheduleConfirmed(reschedule, LocalTime.parse("09:10"), LocalTime.parse("08:40"))
        assertNull(state.dialog)
        assertEquals(CardStatus.EARLY, card("2026-11-30-OTHER-09:10").status)
        assertTrue(state.quests.none { it.id == other.id })
    }

    @Test
    fun `정리 필요 일정을 지각 처리한다`() = runTest {
        clock.now = dt("2026-11-30", "12:00")
        presenter.refresh()
        assertEquals(listOf(work.id), state.cleanup.map { it.id })
        assertEquals("11/30 (월) 09:00", state.cleanup.single().whenLabel)
        presenter.onCleanupLate(work.id)
        assertTrue(state.cleanup.isEmpty())
        assertEquals(CardStatus.LATE, card(work.id).status)
        assertFalse(card(work.id).canDepart)
    }

    @Test
    fun `정리 필요 일정에 도착 시각을 넣는다`() = runTest {
        clock.now = dt("2026-11-30", "12:00")
        presenter.refresh()
        presenter.onCleanupAddArrival(work.id)
        val dialog = state.dialog as HomeDialog.AddArrival
        assertEquals(LocalTime.parse("09:00"), dialog.due)
        presenter.onArrivalEntered(dialog, LocalTime.parse("08:58"), true)
        assertTrue(state.cleanup.isEmpty())
        assertEquals(CardStatus.ON_TIME, card(work.id).status)
    }

    @Test
    fun `정리 필요 일정을 취소한다`() = runTest {
        clock.now = dt("2026-11-30", "12:00")
        presenter.refresh()
        presenter.onCleanupCancel(work.id)
        assertTrue(state.cleanup.isEmpty())
        assertEquals(CardStatus.CANCELED, card(work.id).status)
    }

    @Test
    fun `거부되면 이유를 알려준다`() = runTest {
        clock.now = dt("2026-11-30", "08:25")
        presenter.onDepart(work.id)
        presenter.dismissDialog()
        presenter.onDepart(work.id)
        assertEquals("이미 기록했어.", state.message)
        presenter.dismissMessage()
        assertNull(state.message)
    }

    @Test
    fun `연출을 확인하면 다시 나오지 않는다`() = runTest {
        clock.now = dt("2026-11-30", "08:55")
        presenter.onSafetyAnswered(work.id, true)
        assertEquals(listOf("title:1:1", "title:21:1"), state.celebrations.map { it.key })
        presenter.onCelebrationsSeen()
        assertTrue(state.celebrations.isEmpty())
        presenter.refresh()
        assertTrue(state.celebrations.isEmpty())
    }

    @Test
    fun `출발 기록이 있으면 일정 수정을 막는다`() = runTest {
        clock.now = dt("2026-11-30", "08:25")
        presenter.onDepart(work.id)
        presenter.dismissDialog()
        presenter.onSafetyAnswered(work.id, true)
        val dialog = state.dialog as HomeDialog.ConfirmEarly
        assertFalse(dialog.canReschedule)
    }

    @Test
    fun `일정 변경이 거부되면 창을 유지하고 이유를 알려준다`() = runTest {
        clock.now = dt("2026-11-30", "08:55")
        presenter.onSafetyAnswered(other.id, true)
        presenter.onEditScheduleRequested(state.dialog as HomeDialog.ConfirmEarly)
        val reschedule = state.dialog as HomeDialog.Reschedule
        presenter.onRescheduleConfirmed(reschedule, LocalTime.parse("09:10"), LocalTime.parse("09:20"))
        assertEquals(reschedule, state.dialog)
        assertEquals("출발 시각이 도착 시각보다 늦어.", state.message)
    }

    @Test
    fun `출발을 두 번 눌러도 행동통제 창은 남는다`() = runTest {
        clock.now = dt("2026-11-30", "08:25")
        presenter.onDepart(work.id)
        presenter.onDepart(work.id)
        assertEquals(HomeDialog.AskSelfControl(d("2026-11-30")), state.dialog)
        assertEquals("이미 기록했어.", state.message)
    }

    @Test
    fun `기록이 있는 일정은 취소하지 못하고 XP 변화가 없으면 알리지 않는다`() = runTest {
        clock.now = dt("2026-11-30", "08:55")
        presenter.onSafetyAnswered(work.id, true)
        val before = notified.size
        presenter.onCleanupCancel(work.id)
        assertEquals("기록이 있는 일정은 취소할 수 없어.", state.message)
        assertEquals(CardStatus.ON_TIME, card(work.id).status)
        presenter.dismissMessage()
        presenter.onCleanupCancel(other.id)
        assertEquals(before, notified.size)
        assertEquals(CardStatus.CANCELED, card(other.id).status)
    }

    @Test
    fun `출발한 일정에 정리로 도착을 넣어도 출발 기록은 남는다`() = runTest {
        clock.now = dt("2026-11-30", "08:25")
        presenter.onDepart(work.id)
        presenter.onSelfControlAnswered(d("2026-11-30"), false)
        clock.now = dt("2026-11-30", "12:00")
        presenter.refresh()
        assertEquals(listOf(work.id), state.cleanup.map { it.id })
        presenter.onCleanupAddArrival(work.id)
        val dialog = state.dialog as HomeDialog.AddArrival
        presenter.onArrivalEntered(dialog, LocalTime.parse("08:58"), true)
        assertEquals(CardStatus.ON_TIME, card(work.id).status)
        assertEquals(30, state.xp)
        assertTrue(state.cleanup.isEmpty())
    }

    @Test
    fun `행동통제를 답하지 않고 닫으면 새로고침 때 다시 묻는다`() = runTest {
        clock.now = dt("2026-11-30", "08:25")
        presenter.onDepart(work.id)
        assertEquals(HomeDialog.AskSelfControl(d("2026-11-30")), state.dialog)
        presenter.dismissDialog()
        assertNull(state.dialog)
        presenter.refresh()
        assertEquals(HomeDialog.AskSelfControl(d("2026-11-30")), state.dialog)
        presenter.onSelfControlAnswered(d("2026-11-30"), true)
        assertNull(state.dialog)
        presenter.refresh()
        assertNull(state.dialog)
    }

    @Test
    fun `펫과 이번 주 보스를 글자 카드로 보여준다`() = runTest {
        presenter.refresh()
        assertEquals(PetCard(stage = "알", form = null, mood = "보통", xpToEvolve = 100, evolveProgress = 0f), state.pet)
        assertEquals(CurrentBoss("스누즈 슬라임", hp = 100, maxHp = 100, status = "진행 중"), state.boss.current)
        assertEquals(0, state.boss.defeatedKinds)
        clock.now = dt("2026-11-30", "08:55")
        presenter.onSafetyAnswered(work.id, true)
        assertEquals("신남", state.pet.mood)
        assertEquals(80, state.pet.xpToEvolve)
        assertEquals(0, state.boss.current?.hp)
    }

    @Test
    fun `휴가인 날의 출근 카드는 휴가로 보이고 버튼이 없다`() = runTest {
        db.vacationDao().add(VacationEntity(d("2026-11-30")))
        presenter.refresh()
        assertEquals(CardStatus.VACATION, card(work.id).status)
        assertFalse(card(work.id).canDepart)
        assertFalse(card(work.id).canArrive)
        assertEquals(CardStatus.WAITING, card(other.id).status)
    }

    @Test
    fun `활동이 없는 지난 날의 출근 정리는 휴가였는지 묻고 휴가로 넣는다`() = runTest {
        val friday = questEntity("2026-11-27")
        db.questDao().insertAll(listOf(friday))
        presenter.refresh()
        assertTrue(state.cleanup.single { it.id == friday.id }.canBeVacation)
        presenter.onCleanupVacation(friday.id)
        assertEquals(HomeDialog.ConfirmVacation(d("2026-11-27")), state.dialog)
        presenter.onVacationConfirmed(HomeDialog.ConfirmVacation(d("2026-11-27")))
        assertNull(state.dialog)
        assertTrue(state.cleanup.none { it.id == friday.id })
    }

    @Test
    fun `그날 기록이 있으면 휴가였는지 묻지 않는다`() = runTest {
        val thuWork = questEntity("2026-11-26")
        val thuOther = questEntity("2026-11-26", kind = QuestKind.OTHER, due = "19:00", depart = "18:30", title = "약속")
        db.questDao().insertAll(listOf(thuWork, thuOther))
        db.recordDao().upsert(QuestRecordEntity(questId = thuOther.id, arrivedAt = dt("2026-11-26", "18:55"), safe = true))
        presenter.refresh()
        assertFalse(state.cleanup.single { it.id == thuWork.id }.canBeVacation)
        presenter.onCleanupVacation(thuWork.id)
        assertNull(state.dialog)
    }

    @Test
    fun `휴가였어는 출근 정리 카드에만 보여준다`() = runTest {
        val friWork = questEntity("2026-11-27")
        val friOther = questEntity("2026-11-27", kind = QuestKind.OTHER, due = "19:00", depart = "18:30", title = "약속")
        db.questDao().insertAll(listOf(friWork, friOther))
        presenter.refresh()
        assertTrue(state.cleanup.single { it.id == friWork.id }.canBeVacation)
        assertFalse(state.cleanup.single { it.id == friOther.id }.canBeVacation)
        presenter.onCleanupVacation(friOther.id)
        assertNull(state.dialog)
    }
}
