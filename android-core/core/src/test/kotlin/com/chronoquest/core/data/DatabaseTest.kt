package com.chronoquest.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.core.d
import com.chronoquest.core.dt
import com.chronoquest.core.inMemoryDb
import com.chronoquest.core.questEntity
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
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
    fun `퀘스트와 기록을 저장하고 그대로 읽는다`() = runTest {
        val q = questEntity("2026-11-30")
        db.questDao().insertAll(listOf(q))
        val r = QuestRecordEntity(
            questId = q.id,
            departedAt = dt("2026-11-30", "08:20"),
            departSource = RecordSource.BUTTON,
            arrivedAt = dt("2026-11-30", "08:55:30"),
            arriveSource = RecordSource.BUTTON,
            safe = true,
        )
        db.recordDao().upsert(r)
        assertEquals(q, db.questDao().get(q.id))
        assertEquals(r, db.recordDao().get(q.id))
    }

    @Test
    fun `같은 ID의 퀘스트는 두 번 넣어도 처음 것만 남는다`() = runTest {
        val q = questEntity("2026-11-30")
        db.questDao().insertAll(listOf(q))
        db.questDao().insertAll(listOf(q.copy(title = "다른 제목")))
        assertEquals(listOf(q), db.questDao().all())
    }

    @Test
    fun `시각은 초 단위까지만 저장한다`() = runTest {
        val q = questEntity("2026-11-30")
        db.questDao().insertAll(listOf(q))
        db.recordDao().upsert(
            QuestRecordEntity(questId = q.id, arrivedAt = LocalDateTime.parse("2026-11-30T08:55:30.987"), safe = true),
        )
        assertEquals(dt("2026-11-30", "08:55:30"), db.recordDao().get(q.id)?.arrivedAt)
    }

    @Test
    fun `하루 기록은 날짜를 키로 덮어쓴다`() = runTest {
        db.dayDao().upsert(DayRecordEntity(date = d("2026-11-30"), selfControl = true))
        db.dayDao().upsert(DayRecordEntity(date = d("2026-11-30"), selfControl = false))
        assertEquals(listOf(DayRecordEntity(date = d("2026-11-30"), selfControl = false)), db.dayDao().all())
    }

    @Test
    fun `날짜로 퀘스트를 예정 시각 순으로 읽는다`() = runTest {
        val evening = questEntity("2026-11-30", due = "19:00", depart = "18:30")
        val morning = questEntity("2026-11-30")
        val nextDay = questEntity("2026-12-01")
        db.questDao().insertAll(listOf(evening, nextDay, morning))
        assertEquals(listOf(morning, evening), db.questDao().onDate(d("2026-11-30")))
    }

    @Test
    fun `기록 없는 미래 템플릿 일정만 골라낸다`() = runTest {
        val past = questEntity("2026-11-30", source = QuestSource.TEMPLATE, templateId = 1)
        val recorded = questEntity("2026-12-01", source = QuestSource.TEMPLATE, templateId = 1)
        val future = questEntity("2026-12-02", source = QuestSource.TEMPLATE, templateId = 1)
        val otherTemplate = questEntity("2026-12-02", due = "10:00", source = QuestSource.TEMPLATE, templateId = 2)
        db.questDao().insertAll(listOf(past, recorded, future, otherTemplate))
        db.recordDao().upsert(QuestRecordEntity(questId = recorded.id, departedAt = dt("2026-12-01", "08:20")))
        assertEquals(listOf(future.id), db.questDao().unrecordedFutureIds(1, dt("2026-11-30", "12:00")))
    }

    @Test
    fun `본 연출 키는 중복 없이 쌓인다`() = runTest {
        val at = dt("2026-11-30", "09:00")
        db.celebrationDao().markSeen(listOf(CelebrationSeenEntity("title:1:1", at)))
        db.celebrationDao().markSeen(
            listOf(CelebrationSeenEntity("title:1:1", at), CelebrationSeenEntity("tier:SILVER", at)),
        )
        assertEquals(setOf("title:1:1", "tier:SILVER"), db.celebrationDao().seenKeys().toSet())
    }

    @Test
    fun `휴가와 설정을 저장한다`() = runTest {
        db.vacationDao().add(VacationEntity(d("2026-12-02")))
        db.vacationDao().add(VacationEntity(d("2026-12-02")))
        assertEquals(listOf(VacationEntity(d("2026-12-02"))), db.vacationDao().all())
        db.vacationDao().remove(d("2026-12-02"))
        assertEquals(emptyList(), db.vacationDao().all())

        db.settingsDao().upsert(AppSettingsEntity(seasonStart = d("2026-11-25"), representativeTitleId = 21))
        assertEquals(21, db.settingsDao().get()?.representativeTitleId)
    }
}
