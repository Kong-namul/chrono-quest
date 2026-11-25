package com.chronoquest.core.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.core.d
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.core.dt
import com.chronoquest.core.inMemoryDb
import com.chronoquest.core.questEntity
import com.chronoquest.rules.engine.Engine
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerRepositoryTest {
    private lateinit var db: ChronoDatabase

    @Before
    fun setUp() {
        db = inMemoryDb()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `설정이 없으면 시즌 시작일 기본값으로 만들고 저장한다`() = runTest {
        val repo = LedgerRepository(db)
        assertEquals(d("2026-11-25"), repo.settings().seasonStart)
        assertEquals(d("2026-11-25"), db.settingsDao().get()?.seasonStart)
    }

    @Test
    fun `저장된 기록으로 판정 엔진이 정산한다`() = runTest {
        val q = questEntity("2026-11-30")
        db.questDao().insertAll(listOf(q))
        db.recordDao().upsert(QuestRecordEntity(questId = q.id, arrivedAt = dt("2026-11-30", "08:55"), safe = true))
        val state = Engine().compute(LedgerRepository(db).loadLedger(), dt("2026-11-30", "12:00"))
        assertEquals(20, state.xp)
        assertEquals(1, state.inventory[1])
    }
}
