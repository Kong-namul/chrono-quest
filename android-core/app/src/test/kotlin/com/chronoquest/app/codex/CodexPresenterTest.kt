package com.chronoquest.app.codex

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.app.MutableClock
import com.chronoquest.app.dt
import com.chronoquest.app.inMemoryDb
import com.chronoquest.app.questEntity
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.service.GameService
import com.chronoquest.rules.titles.TitleCatalog
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
class CodexPresenterTest {
    private lateinit var db: ChronoDatabase
    private lateinit var presenter: CodexPresenter
    private val clock = MutableClock(dt("2026-11-30", "08:00"))
    private val work = questEntity("2026-11-30")

    @Before
    fun setUp() = runTest {
        db = inMemoryDb()
        db.questDao().insertAll(listOf(work))
        presenter = CodexPresenter({ GameService(db, clock) }, { TitleCatalog.load() }, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val state get() = presenter.state.value

    private fun row(id: Int): TitleRow = state.sections.flatMap { it.titles }.single { it.id == id }

    private suspend fun arriveOnTime() {
        clock.now = dt("2026-11-30", "08:55")
        GameService(db, clock).arrive(work.id, safe = true)
    }

    @Test
    fun `처음엔 모두 잠겨 있고 얼마나 남았는지 보여준다`() = runTest {
        presenter.onEnter()
        assertFalse(state.loading)
        assertEquals(0, state.collected)
        assertEquals(200, state.total)
        assertEquals("미각성자", state.representative)
        assertEquals(10, state.sections.size)
        assertTrue(state.sections.all { it.titles.size == 20 && it.owned == 0 })
        assertEquals(
            TitleRow(1, "최초의 각성", "도착 1회마다", owned = 0, progress = "앞으로 1회", isNew = false, isRepresentative = false),
            row(1),
        )
        assertEquals("도착 5회마다", row(5).condition)
        assertEquals("앞으로 5회", row(5).progress)
        assertEquals("연속 도착 1회", row(21).condition)
    }

    @Test
    fun `칭호를 얻으면 보유 수와 NEW를 보여주고 다음에 열면 NEW가 사라진다`() = runTest {
        arriveOnTime()
        presenter.onEnter()
        assertEquals(2, state.collected)
        assertEquals("운명의 첫걸음", state.representative)
        assertEquals(
            TitleRow(1, "최초의 각성", "도착 1회마다", owned = 1, progress = "보유 1개 · 다음까지 1회", isNew = true, isRepresentative = false),
            row(1),
        )
        assertTrue(row(21).isNew)
        assertTrue(row(21).isRepresentative)
        assertEquals("보유 1개 · 이번 연속에서 받음", row(21).progress)
        assertEquals("앞으로 1회", row(2).progress)
        assertEquals(1, state.sections[0].owned)

        presenter.refresh()
        assertTrue(row(1).isNew)   // 같은 방문 동안은 NEW가 남는다

        presenter.onEnter()
        assertFalse(row(1).isNew)
        assertFalse(row(21).isNew)
    }

    @Test
    fun `가진 칭호만 대표로 고른다`() = runTest {
        arriveOnTime()
        presenter.onEnter()
        presenter.onTitleSelected(1)
        assertEquals(1, state.detail?.id)
        presenter.onSetRepresentative(1)
        assertNull(state.detail)
        assertEquals("최초의 각성", state.representative)
        assertTrue(row(1).isRepresentative)
        assertFalse(row(21).isRepresentative)
        presenter.onSetRepresentative(61)
        assertEquals("가진 칭호만 대표로 고를 수 있어.", state.message)
    }
}
