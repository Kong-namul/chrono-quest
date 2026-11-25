package com.chronoquest.core.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.core.clockAt
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.inMemoryDb
import com.chronoquest.core.questEntity
import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.model.QuestKind
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
class GameServiceViewTest {
    private lateinit var db: ChronoDatabase
    private val work = questEntity("2026-11-30")
    private val other = questEntity("2026-11-30", kind = QuestKind.OTHER, due = "19:00", depart = "18:30", title = "약속")
    private val tomorrow = questEntity("2026-12-01")

    @Before
    fun setUp() = runTest {
        db = inMemoryDb()
        db.questDao().insertAll(listOf(other, tomorrow, work))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun service(time: String) = GameService(db, clockAt("2026-11-30", time))

    @Test
    fun `오늘 일정은 예정 시각 순으로 기록과 판정을 함께 준다`() = runTest {
        service("08:55").arrive(work.id, safe = true)
        val today = service("12:00").today()
        assertEquals(listOf(work.id, other.id), today.quests.map { it.quest.id })
        assertEquals(ArrivalVerdict.ON_TIME, today.quests[0].outcome?.verdict)
        assertNull(today.quests[1].record)
        assertNull(today.day)
    }

    @Test
    fun `예정 3시간이 지나도 기록이 없으면 정리 필요`() = runTest {
        assertEquals(emptyList(), service("11:59").needsCleanup())
        assertEquals(listOf(work), service("12:00").needsCleanup())
    }

    @Test
    fun `대표 칭호는 가진 칭호만 고를 수 있고 사라지면 최근 칭호로 돌아간다`() = runTest {
        assertEquals("미각성자", service("08:00").representativeTitle())
        service("08:55").arrive(work.id, safe = true)
        val svc = service("09:00")
        assertEquals("운명의 첫걸음", svc.representativeTitle())
        assertTrue(svc.setRepresentativeTitle(1))
        assertEquals("최초의 각성", svc.representativeTitle())
        assertFalse(svc.setRepresentativeTitle(61))
        assertEquals("최초의 각성", svc.representativeTitle())
        svc.cancelQuest(work.id)
        assertEquals("미각성자", svc.representativeTitle())
    }
}
