package com.chronoquest.core.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.core.clockAt
import com.chronoquest.core.domain.RejectReason
import com.chronoquest.core.dt
import com.chronoquest.core.inMemoryDb
import com.chronoquest.core.questEntity
import com.chronoquest.rules.titles.TitleCatalog
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameServiceConcurrencyTest {
    private val work = questEntity("2026-11-30")
    private val catalog = TitleCatalog.load()

    @Test
    fun `두 인스턴스가 동시에 출발을 기록해도 먼저 확정된 하나만 남는다`() = runTest {
        repeat(20) {
            val db = inMemoryDb()
            try {
                db.questDao().insertAll(listOf(work))
                val early = GameService(db, clockAt("2026-11-30", "08:20"), catalog)
                val late = GameService(db, clockAt("2026-11-30", "08:25"), catalog)
                val (first, second) = withContext(Dispatchers.Default) {
                    val a = async { early.depart(work.id) }
                    val b = async { late.depart(work.id) }
                    a.await() to b.await()
                }
                val results = listOf(first, second)
                assertEquals(1, results.count { it is ActionResult.Done }, "iteration $it: $results")
                assertEquals(1, results.count { it == ActionResult.Rejected(RejectReason.ALREADY_RECORDED) }, "iteration $it: $results")
                val winnerTime = if (first is ActionResult.Done) "08:20" else "08:25"
                assertEquals(dt("2026-11-30", winnerTime), db.recordDao().get(work.id)?.departedAt, "iteration $it")
            } finally {
                db.close()
            }
        }
    }
}
