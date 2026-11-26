package com.chronoquest.core.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.core.clockAt
import com.chronoquest.core.d
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.core.data.QuestSource
import com.chronoquest.core.domain.ScheduleProblem
import com.chronoquest.core.dt
import com.chronoquest.core.inMemoryDb
import com.chronoquest.core.weekdayTemplate
import com.chronoquest.rules.model.QuestKind
import com.chronoquest.rules.model.QuestStatus
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleServiceTest {
    private lateinit var db: ChronoDatabase

    @Before
    fun setUp() {
        db = inMemoryDb()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun service(date: String = "2026-11-25", time: String = "07:00") = ScheduleService(db, clockAt(date, time))

    private suspend fun questIds(): List<String> = db.questDao().all().map { it.id }.sorted()

    @Test
    fun `평일 템플릿을 저장하면 오늘부터 7일 중 평일 일정을 만든다`() = runTest {
        val result = service().saveTemplate(weekdayTemplate())
        assertTrue(result is ScheduleResult.Saved)
        assertEquals(
            listOf(
                "2026-11-25-WORK-09:00",
                "2026-11-26-WORK-09:00",
                "2026-11-27-WORK-09:00",
                "2026-11-30-WORK-09:00",
                "2026-12-01-WORK-09:00",
            ),
            questIds(),
        )
    }

    @Test
    fun `잘못된 템플릿은 저장하지 않는다`() = runTest {
        assertEquals(
            ScheduleResult.Rejected(listOf(ScheduleProblem.DEPART_AFTER_DUE)),
            service().saveTemplate(weekdayTemplate(depart = "09:30")),
        )
        assertEquals(emptyList(), service().templates())
    }

    @Test
    fun `이미 지난 시각의 일정은 만들지 않는다`() = runTest {
        service(time = "10:00").saveTemplate(weekdayTemplate())
        assertEquals(4, db.questDao().all().size)
        assertTrue("2026-11-25-WORK-09:00" !in questIds())
    }

    @Test
    fun `일정 생성은 여러 번 해도 같다`() = runTest {
        service().saveTemplate(weekdayTemplate())
        assertEquals(0, service().generateQuests())
        assertEquals(5, db.questDao().all().size)
    }

    @Test
    fun `템플릿을 고치면 기록 없는 미래 일정만 새 시각으로 바뀐다`() = runTest {
        val id = (service().saveTemplate(weekdayTemplate()) as ScheduleResult.Saved).id.toLong()
        db.recordDao().upsert(
            QuestRecordEntity(questId = "2026-11-25-WORK-09:00", departedAt = dt("2026-11-25", "06:50")),
        )
        service().saveTemplate(weekdayTemplate(id = id, due = "09:30", depart = "09:00"))
        assertEquals(
            listOf(
                "2026-11-25-WORK-09:00",
                "2026-11-26-WORK-09:30",
                "2026-11-27-WORK-09:30",
                "2026-11-30-WORK-09:30",
                "2026-12-01-WORK-09:30",
            ),
            questIds(),
        )
    }

    @Test
    fun `취소한 템플릿 일정은 템플릿을 고쳐도 되살아나지 않는다`() = runTest {
        val id = (service().saveTemplate(weekdayTemplate()) as ScheduleResult.Saved).id.toLong()
        val canceled = db.questDao().get("2026-11-27-WORK-09:00")!!.copy(status = QuestStatus.CANCELED)
        db.questDao().update(canceled)
        service().saveTemplate(weekdayTemplate(id = id, title = "회사"))
        assertEquals(listOf(canceled), db.questDao().onDate(d("2026-11-27")))
    }

    @Test
    fun `템플릿을 지우면 기록 없는 미래 일정만 지운다`() = runTest {
        val id = (service().saveTemplate(weekdayTemplate()) as ScheduleResult.Saved).id.toLong()
        db.recordDao().upsert(
            QuestRecordEntity(questId = "2026-11-25-WORK-09:00", departedAt = dt("2026-11-25", "06:50")),
        )
        service().deleteTemplate(id)
        assertEquals(listOf("2026-11-25-WORK-09:00"), questIds())
        assertEquals(emptyList(), service().templates())
    }

    @Test
    fun `단발 일정을 추가한다`() = runTest {
        val result = service().addOneOffQuest(
            d("2026-11-28"), QuestKind.OTHER, "보드게임", LocalTime.parse("14:00"), LocalTime.parse("13:20"),
        )
        assertEquals(ScheduleResult.Saved("2026-11-28-OTHER-14:00"), result)
        val quest = db.questDao().get("2026-11-28-OTHER-14:00")!!
        assertEquals(QuestSource.MANUAL, quest.source)
        assertEquals(dt("2026-11-28", "13:20"), quest.targetDepartAt)
    }

    @Test
    fun `단발 일정 검증`() = runTest {
        val svc = service()
        svc.addOneOffQuest(d("2026-11-28"), QuestKind.OTHER, "보드게임", LocalTime.parse("14:00"), LocalTime.parse("13:20"))
        assertEquals(
            ScheduleResult.Rejected(listOf(ScheduleProblem.DUPLICATE)),
            svc.addOneOffQuest(d("2026-11-28"), QuestKind.OTHER, "다른 약속", LocalTime.parse("14:00"), LocalTime.parse("13:00")),
        )
        assertEquals(
            ScheduleResult.Rejected(listOf(ScheduleProblem.BEFORE_SEASON)),
            svc.addOneOffQuest(d("2026-11-24"), QuestKind.OTHER, "약속", LocalTime.parse("14:00"), LocalTime.parse("13:00")),
        )
        assertEquals(
            ScheduleResult.Rejected(listOf(ScheduleProblem.BLANK_TITLE, ScheduleProblem.DEPART_AFTER_DUE)),
            svc.addOneOffQuest(d("2026-11-29"), QuestKind.OTHER, " ", LocalTime.parse("14:00"), LocalTime.parse("14:10")),
        )
    }

    @Test
    fun `일정 시각을 바꾸면 ID와 시각만 바뀌고 나머지는 그대로다`() = runTest {
        service().saveTemplate(weekdayTemplate())
        val old = db.questDao().get("2026-11-26-WORK-09:00")!!
        assertEquals(
            ScheduleResult.Saved("2026-11-26-WORK-10:00"),
            service().reschedule(old.id, LocalTime.parse("10:00"), LocalTime.parse("09:20")),
        )
        assertNull(db.questDao().get(old.id))
        assertEquals(
            old.copy(id = "2026-11-26-WORK-10:00", dueAt = dt("2026-11-26", "10:00"), targetDepartAt = dt("2026-11-26", "09:20")),
            db.questDao().get("2026-11-26-WORK-10:00"),
        )
        assertEquals(0, service().generateQuests())
        assertEquals(
            ScheduleResult.Saved("2026-11-27-WORK-09:00"),
            service().reschedule("2026-11-27-WORK-09:00", LocalTime.parse("09:00"), LocalTime.parse("08:40")),
        )
        assertEquals(dt("2026-11-27", "08:40"), db.questDao().get("2026-11-27-WORK-09:00")?.targetDepartAt)
    }

    @Test
    fun `기록이 있거나 없는 일정은 시각을 바꿀 수 없다`() = runTest {
        service().saveTemplate(weekdayTemplate())
        db.recordDao().upsert(
            QuestRecordEntity(questId = "2026-11-25-WORK-09:00", departedAt = dt("2026-11-25", "06:50")),
        )
        assertEquals(
            ScheduleResult.Rejected(listOf(ScheduleProblem.HAS_RECORD)),
            service().reschedule("2026-11-25-WORK-09:00", LocalTime.parse("10:00"), LocalTime.parse("09:30")),
        )
        assertEquals(
            ScheduleResult.Rejected(listOf(ScheduleProblem.NOT_FOUND)),
            service().reschedule("없는-일정", LocalTime.parse("10:00"), LocalTime.parse("09:30")),
        )
        assertTrue("2026-11-25-WORK-09:00" in questIds())
    }

    @Test
    fun `바꾼 시각이 다른 일정과 겹치거나 잘못되면 거부한다`() = runTest {
        val svc = service()
        svc.addOneOffQuest(d("2026-11-28"), QuestKind.OTHER, "보드게임", LocalTime.parse("14:00"), LocalTime.parse("13:20"))
        svc.addOneOffQuest(d("2026-11-28"), QuestKind.OTHER, "저녁", LocalTime.parse("18:00"), LocalTime.parse("17:30"))
        assertEquals(
            ScheduleResult.Rejected(listOf(ScheduleProblem.DUPLICATE)),
            svc.reschedule("2026-11-28-OTHER-18:00", LocalTime.parse("14:00"), LocalTime.parse("13:30")),
        )
        assertEquals(
            ScheduleResult.Rejected(listOf(ScheduleProblem.DEPART_AFTER_DUE)),
            svc.reschedule("2026-11-28-OTHER-18:00", LocalTime.parse("17:00"), LocalTime.parse("17:10")),
        )
        assertEquals(listOf("2026-11-28-OTHER-14:00", "2026-11-28-OTHER-18:00"), questIds())
    }

    @Test
    fun `휴가를 켜고 끈다`() = runTest {
        val svc = service()
        svc.setVacation(d("2026-11-26"), true)
        svc.setVacation(d("2026-11-26"), true)
        assertEquals(listOf(d("2026-11-26")), svc.vacations())
        svc.setVacation(d("2026-11-26"), false)
        assertEquals(emptyList(), svc.vacations())
    }
}
