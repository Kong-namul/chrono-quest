package com.chronoquest.core.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.core.clockAt
import com.chronoquest.core.d
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.RecordSource
import com.chronoquest.core.domain.RejectReason
import com.chronoquest.core.dt
import com.chronoquest.core.inMemoryDb
import com.chronoquest.core.questEntity
import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.model.QuestKind
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `취소하면 판정에서 빠지고 XP도 다시 계산된다`() = runTest {
        service("08:55").arrive(work.id, safe = true)
        val result = done(service("10:00").cancelQuest(work.id))
        assertEquals(-20, result.xpGained)
        assertFalse(work.id in service("10:00").state().outcomes)
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
