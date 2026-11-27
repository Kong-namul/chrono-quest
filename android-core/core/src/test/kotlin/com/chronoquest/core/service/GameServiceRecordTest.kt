package com.chronoquest.core.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.core.clockAt
import com.chronoquest.core.d
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.RecordSource
import com.chronoquest.core.data.VacationEntity
import com.chronoquest.core.domain.RejectReason
import com.chronoquest.core.dt
import com.chronoquest.core.inMemoryDb
import com.chronoquest.core.questEntity
import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.model.QuestKind
import com.chronoquest.rules.model.QuestStatus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameServiceRecordTest {
    private lateinit var db: ChronoDatabase
    private val work = questEntity("2026-11-30")
    private val other = questEntity("2026-11-30", kind = QuestKind.OTHER, due = "19:00", depart = "18:30", title = "약속")

    @Before
    fun setUp() = runTest {
        db = inMemoryDb()
        db.questDao().insertAll(listOf(work, other))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun service(time: String) = GameService(db, clockAt("2026-11-30", time))

    private fun done(result: ActionResult): ActionResult.Done = result as ActionResult.Done

    @Test
    fun `정시 출발이면 10 XP와 정시 출발 칭호를 주고 그날 첫 출발이라 행동통제를 묻는다`() = runTest {
        val result = done(service("08:25").depart(work.id))
        assertEquals(10, result.xpGained)
        assertTrue(result.newCelebrations.any { it.key == "title:41:1" })
        assertTrue(result.askSelfControl)
        assertEquals(RecordSource.BUTTON, db.recordDao().get(work.id)?.departSource)
    }

    @Test
    fun `안전한 정시 도착이면 20 XP와 첫 미션 칭호`() = runTest {
        val result = done(service("08:55").arrive(work.id, safe = true))
        assertEquals(20, result.xpGained)
        assertEquals(listOf("title:1:1", "title:21:1"), result.newCelebrations.map { it.key })
    }

    @Test
    fun `이미 출발한 퀘스트와 미래 시각과 없는 퀘스트는 거부한다`() = runTest {
        service("08:25").depart(work.id)
        assertEquals(ActionResult.Rejected(RejectReason.ALREADY_RECORDED), service("08:30").depart(work.id))
        assertEquals(
            ActionResult.Rejected(RejectReason.FUTURE_TIME),
            service("18:00").depart(other.id, at = dt("2026-11-30", "18:20")),
        )
        assertEquals(ActionResult.NotFound, service("08:30").depart("없는-퀘스트"))
    }

    @Test
    fun `그날 두 번째 출발이면 행동통제를 묻지 않는다`() = runTest {
        service("08:25").depart(work.id)
        assertFalse(done(service("18:40").depart(other.id)).askSelfControl)
    }

    @Test
    fun `행동통제를 이미 답했으면 묻지 않는다`() = runTest {
        service("07:00").answerSelfControl(d("2026-11-30"), true)
        assertFalse(done(service("08:25").depart(work.id)).askSelfControl)
    }

    @Test
    fun `행동통제 답이 예면 5 XP와 행동통제 칭호`() = runTest {
        val result = done(service("08:25").answerSelfControl(d("2026-11-30"), true))
        assertEquals(5, result.xpGained)
        assertTrue(result.newCelebrations.any { it.key == "title:101:1" })
    }

    @Test
    fun `기록을 고치면 다시 정산하고 수정 표시를 남긴다`() = runTest {
        assertEquals(0, done(service("09:10").arrive(work.id, safe = true)).xpGained)
        val edited = done(service("09:20").editRecord(work.id, null, dt("2026-11-30", "08:55"), true))
        assertEquals(20, edited.xpGained)
        val record = db.recordDao().get(work.id)!!
        assertTrue(record.edited)
        assertEquals(RecordSource.EDIT, record.arriveSource)
    }

    @Test
    fun `기록 없음으로 정리하면 지각으로 판정되고 보류에서 빠진다`() = runTest {
        val svc = service("13:00")
        assertTrue(work.id in svc.state().pendingQuestIds)
        done(svc.resolveNoRecord(work.id))
        val state = svc.state()
        assertEquals(ArrivalVerdict.LATE, state.outcomes[work.id]?.verdict)
        assertFalse(work.id in state.pendingQuestIds)
    }

    @Test
    fun `기록이 있는 일정은 취소할 수 없다`() = runTest {
        service("08:55").arrive(work.id, safe = true)
        assertEquals(ActionResult.Rejected(RejectReason.HAS_RECORD), service("10:00").cancelQuest(work.id))
        assertEquals(QuestStatus.SCHEDULED, db.questDao().get(work.id)?.status)
    }

    @Test
    fun `기록 없는 일정을 취소하면 판정에서 빠지고 되돌리면 돌아온다`() = runTest {
        val svc = service("10:00")
        assertTrue(work.id in svc.state().pendingQuestIds)
        done(svc.cancelQuest(work.id))
        assertFalse(work.id in svc.state().pendingQuestIds)
        assertEquals(ActionResult.Rejected(RejectReason.QUEST_CANCELED), svc.cancelQuest(work.id))
        done(svc.restoreQuest(work.id))
        assertEquals(QuestStatus.SCHEDULED, db.questDao().get(work.id)?.status)
        assertTrue(work.id in svc.state().pendingQuestIds)
        assertEquals(ActionResult.Rejected(RejectReason.NOT_CANCELED), svc.restoreQuest(work.id))
        assertEquals(ActionResult.NotFound, svc.restoreQuest("없는-일정"))
    }

    @Test
    fun `지난 날짜의 취소는 되돌릴 수 없다`() = runTest {
        done(service("10:00").cancelQuest(work.id))
        val nextDay = GameService(db, clockAt("2026-12-01", "08:00"))
        assertEquals(ActionResult.Rejected(RejectReason.PAST_DATE), nextDay.restoreQuest(work.id))
        assertEquals(QuestStatus.CANCELED, db.questDao().get(work.id)?.status)
    }

    @Test
    fun `휴가인 날의 출근은 기록하지 않지만 다른 일정은 기록한다`() = runTest {
        db.vacationDao().add(VacationEntity(d("2026-11-30")))
        assertEquals(ActionResult.Rejected(RejectReason.ON_VACATION), service("08:25").depart(work.id))
        assertNull(db.recordDao().get(work.id))
        assertTrue(service("18:25").depart(other.id) is ActionResult.Done)
    }

    @Test
    fun `예정보다 30분 이상 이른 도착은 기록하지 않고 확인을 묻는다`() = runTest {
        assertEquals(ActionResult.ConfirmEarly(605), service("08:55").arrive(other.id, safe = true))
        assertNull(db.recordDao().get(other.id))
        assertTrue(service("08:55").arrive(other.id, safe = true, allowEarly = true) is ActionResult.Done)
        assertEquals(dt("2026-11-30", "08:55"), db.recordDao().get(other.id)?.arrivedAt)
    }

    @Test
    fun `내일 행동통제는 미리 답할 수 없다`() = runTest {
        assertEquals(
            ActionResult.Rejected(RejectReason.FUTURE_TIME),
            service("08:00").answerSelfControl(d("2026-12-01"), true),
        )
        assertNull(db.dayDao().get(d("2026-12-01")))
    }

    @Test
    fun `이미 본 연출은 쓰기 결과의 새 연출에서 빠진다`() = runTest {
        val svc = service("08:55")
        svc.markSeen(listOf("title:1:1"))
        assertEquals(listOf("title:21:1"), done(svc.arrive(work.id, safe = true)).newCelebrations.map { it.key })
    }

    @Test
    fun `본 연출은 다시 새 연출로 나오지 않는다`() = runTest {
        val svc = service("08:55")
        svc.arrive(work.id, safe = true)
        val unseen = svc.unseenCelebrations()
        assertEquals(listOf("title:1:1", "title:21:1"), unseen.map { it.key })
        svc.markSeen(unseen.map { it.key })
        assertEquals(emptyList(), svc.unseenCelebrations())
    }
}
