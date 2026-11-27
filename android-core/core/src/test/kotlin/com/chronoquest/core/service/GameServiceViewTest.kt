package com.chronoquest.core.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.core.clockAt
import com.chronoquest.core.d
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.VacationEntity
import com.chronoquest.core.domain.TitleChoice
import com.chronoquest.core.inMemoryDb
import com.chronoquest.core.questEntity
import com.chronoquest.rules.engine.GrantType
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
    fun `정리 필요 목록은 기록 여부를 함께 준다`() = runTest {
        val old = questEntity("2026-11-27")
        db.questDao().insertAll(listOf(old))
        service("08:25").depart(work.id)
        val cleanup = service("12:30").cleanupQuests()
        assertEquals(listOf(old, work), cleanup.map { it.quest })
        assertFalse(cleanup.single { it.quest.id == old.id }.hasRecord)
        assertTrue(cleanup.single { it.quest.id == work.id }.hasRecord)
    }

    @Test
    fun `대표 칭호는 가진 칭호만 고를 수 있고 사라지면 최근 칭호로 돌아간다`() = runTest {
        assertEquals("미각성자", service("08:00").representativeTitle())
        service("08:55").arrive(work.id, safe = true)
        val svc = service("09:00")
        assertEquals("운명의 첫걸음", svc.representativeTitle())
        assertEquals(21, storedTitleId())
        assertTrue(svc.setRepresentativeTitle(1))
        assertEquals("최초의 각성", svc.representativeTitle())
        assertFalse(svc.setRepresentativeTitle(61))
        assertEquals("최초의 각성", svc.representativeTitle())
        svc.editRecord(work.id, departedAt = null, arrivedAt = null, safe = null)
        assertEquals("미각성자", svc.representativeTitle())
        assertNull(storedTitleId())
    }

    @Test
    fun `자동으로 고른 대표 칭호는 새 칭호를 얻어도 그대로다`() = runTest {
        service("08:55").arrive(work.id, safe = true)
        assertEquals("운명의 첫걸음", service("09:00").representativeTitle())
        service("09:00").answerSelfControl(d("2026-11-30"), true)
        val svc = service("09:05")
        assertEquals(101, svc.state().grants.last { it.type == GrantType.TITLE }.titleId)
        assertEquals("운명의 첫걸음", svc.representativeTitle())
        assertEquals(21, storedTitleId())
    }

    @Test
    fun `저장된 대표 칭호를 잃으면 가장 최근에 얻은 칭호로 바꿔 저장한다`() = runTest {
        service("08:55").arrive(work.id, safe = true)
        service("09:00").answerSelfControl(d("2026-11-30"), true)
        assertTrue(service("09:00").setRepresentativeTitle(101))
        service("09:05").answerSelfControl(d("2026-11-30"), false)
        assertEquals("운명의 첫걸음", service("09:10").representativeTitle())
        assertEquals(21, storedTitleId())
    }

    @Test
    fun `대표 칭호 선택을 지우면 다음 조회에서 최근 칭호를 고른다`() = runTest {
        service("08:55").arrive(work.id, safe = true)
        val svc = service("09:00")
        assertTrue(svc.setRepresentativeTitle(1))
        assertEquals("최초의 각성", svc.representativeTitle())
        assertTrue(svc.setRepresentativeTitle(null))
        assertNull(storedTitleId())
        assertEquals("운명의 첫걸음", svc.representativeTitle())
        assertEquals(21, storedTitleId())
    }

    @Test
    fun `안 본 칭호만 NEW이고 보고 나면 빠진다`() = runTest {
        val svc = service("09:00")
        assertEquals(emptySet(), svc.newTitleIds())
        service("08:55").arrive(work.id, safe = true)
        assertEquals(setOf(1, 21), svc.newTitleIds())
        svc.markTitlesViewed(listOf(1))
        assertEquals(setOf(21), svc.newTitleIds())
        assertEquals(listOf("title:1:1", "title:21:1"), svc.unseenCelebrations().map { it.key })
    }

    @Test
    fun `오늘이 휴가면 오늘 요약에 표시한다`() = runTest {
        assertFalse(service("08:00").today().vacation)
        db.vacationDao().add(VacationEntity(d("2026-11-30")))
        assertTrue(service("08:00").today().vacation)
    }

    @Test
    fun `대표 칭호는 ID와 이름을 같이 준다`() = runTest {
        assertEquals(TitleChoice(null, "미각성자"), service("08:00").representative())
        service("08:55").arrive(work.id, safe = true)
        assertEquals(TitleChoice(21, "운명의 첫걸음"), service("09:00").representative())
    }

    @Test
    fun `대표 칭호가 맞으면 그대로 두고 갖지 않은 칭호면 최근 칭호로 한 번 고친다`() = runTest {
        service("08:55").arrive(work.id, safe = true)
        val svc = service("09:00")
        assertEquals(TitleChoice(21, "운명의 첫걸음"), svc.representative())
        assertEquals(21, storedTitleId())
        assertEquals(TitleChoice(21, "운명의 첫걸음"), svc.representative())
        assertEquals(21, storedTitleId())
        db.settingsDao().upsert(db.settingsDao().get()!!.copy(representativeTitleId = 61))
        assertEquals(TitleChoice(21, "운명의 첫걸음"), svc.representative())
        assertEquals(21, storedTitleId())
    }

    private suspend fun storedTitleId(): Int? = db.settingsDao().get()?.representativeTitleId
}
