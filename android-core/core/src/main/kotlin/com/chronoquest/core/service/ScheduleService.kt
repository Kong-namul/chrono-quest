package com.chronoquest.core.service

import androidx.room.withTransaction
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.QuestSource
import com.chronoquest.core.data.ScheduleTemplateEntity
import com.chronoquest.core.data.VacationEntity
import com.chronoquest.core.domain.DayActivity
import com.chronoquest.core.domain.QuestGenerator
import com.chronoquest.core.domain.QuestIds
import com.chronoquest.core.domain.ScheduleProblem
import com.chronoquest.core.domain.TemplateRules
import com.chronoquest.core.domain.Upcoming
import com.chronoquest.core.domain.UpcomingDay
import com.chronoquest.rules.model.QuestKind
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

sealed interface ScheduleResult {
    data class Saved(val id: String) : ScheduleResult
    data class Rejected(val problems: List<ScheduleProblem>) : ScheduleResult
}

/** 일정 소스 관리 (스펙 6.4): 반복 템플릿, 7일치 생성, 단발 추가, 휴가. */
class ScheduleService(
    private val db: ChronoDatabase,
    private val clock: Clock,
) {
    private val ledgers = LedgerRepository(db)

    suspend fun templates(): List<ScheduleTemplateEntity> = db.templateDao().all()

    /** 일정 탭에 보여줄 것. 오늘부터 [days]일의 일정과 휴가다. */
    suspend fun upcoming(days: Int = QuestGenerator.HORIZON_DAYS): List<UpcomingDay> = db.withTransaction {
        Upcoming.build(
            from = clock.today(),
            days = days,
            quests = db.questDao().all(),
            records = db.recordDao().all(),
            dayRecords = db.dayDao().all(),
            vacations = db.vacationDao().all().mapTo(HashSet()) { it.date },
        )
    }

    /** 템플릿을 저장하고, 그 템플릿의 기록 없는 미래 일정을 새 내용으로 다시 만든다. */
    suspend fun saveTemplate(template: ScheduleTemplateEntity): ScheduleResult {
        val problems = TemplateRules.validate(template)
        if (problems.isNotEmpty()) return ScheduleResult.Rejected(problems)
        val id = db.withTransaction {
            val id = if (template.id == 0L) {
                db.templateDao().insert(template)
            } else {
                db.templateDao().update(template)
                template.id
            }
            dropFutureUnrecorded(id)
            generateQuests()
            id
        }
        return ScheduleResult.Saved(id.toString())
    }

    /** 템플릿을 지우고 그 템플릿의 기록 없는 미래 일정도 지운다. 지난 일정과 기록은 남긴다. */
    suspend fun deleteTemplate(id: Long) {
        db.withTransaction {
            dropFutureUnrecorded(id)
            db.templateDao().delete(id)
        }
    }

    /**
     * 오늘부터 [days]일치 템플릿 일정을 만들고 새로 만든 개수를 돌려준다.
     * 이미 있는 ID, 같은 템플릿이 그날 이미 만든 일정, 이미 지난 일정은 만들지 않는다.
     */
    suspend fun generateQuests(days: Int = QuestGenerator.HORIZON_DAYS): Int = db.withTransaction {
        val now = clock.nowLocal()
        val existing = db.questDao().all()
        val existingIds = existing.mapTo(HashSet()) { it.id }
        val templateDays = existing.filter { it.templateId != null }.mapTo(HashSet()) { it.templateId to it.date }
        val fresh = QuestGenerator.generate(db.templateDao().all(), now.toLocalDate(), days).filter {
            it.id !in existingIds && (it.templateId to it.date) !in templateDays && it.dueAt.isAfter(now)
        }
        db.questDao().insertAll(fresh)
        fresh.size
    }

    /** 단발 일정 추가. 중복 확인과 저장은 한 트랜잭션이다. */
    suspend fun addOneOffQuest(
        date: LocalDate,
        kind: QuestKind,
        title: String,
        dueTime: LocalTime,
        targetDepartTime: LocalTime,
    ): ScheduleResult = db.withTransaction {
        val problems = TemplateRules.problems(title, dueTime, targetDepartTime).toMutableList()
        if (date.isBefore(ledgers.settings().seasonStart)) problems += ScheduleProblem.BEFORE_SEASON
        val id = QuestIds.of(date, kind, dueTime)
        if (problems.isEmpty() && db.questDao().get(id) != null) problems += ScheduleProblem.DUPLICATE
        if (problems.isNotEmpty()) return@withTransaction ScheduleResult.Rejected(problems)
        db.questDao().insertAll(
            listOf(
                QuestEntity(
                    id = id,
                    date = date,
                    kind = kind,
                    title = title,
                    dueAt = date.atTime(dueTime.truncatedTo(ChronoUnit.MINUTES)),
                    targetDepartAt = date.atTime(targetDepartTime.truncatedTo(ChronoUnit.MINUTES)),
                    source = QuestSource.MANUAL,
                ),
            ),
        )
        ScheduleResult.Saved(id)
    }

    /**
     * 일정 하나의 예정·목표 출발 시각을 바꾼다(이른 기록 확인 뒤 "일정 변경"). 기록이 있으면 바꿀 수 없다. 지난 날짜 일정도 바꿀 수 없다.
     * ID가 예정 시각을 담으므로 옛 일정을 지우고 새 ID로 다시 넣는다. 나머지 필드는 그대로다.
     */
    suspend fun reschedule(questId: String, dueTime: LocalTime, targetDepartTime: LocalTime): ScheduleResult =
        db.withTransaction {
            val quest = db.questDao().get(questId)
                ?: return@withTransaction ScheduleResult.Rejected(listOf(ScheduleProblem.NOT_FOUND))
            if (quest.date.isBefore(clock.today())) {
                return@withTransaction ScheduleResult.Rejected(listOf(ScheduleProblem.PAST_DATE))
            }
            if (db.recordDao().get(questId) != null) {
                return@withTransaction ScheduleResult.Rejected(listOf(ScheduleProblem.HAS_RECORD))
            }
            val problems = TemplateRules.problems(quest.title, dueTime, targetDepartTime)
            if (problems.isNotEmpty()) return@withTransaction ScheduleResult.Rejected(problems)
            val newId = QuestIds.of(quest.date, quest.kind, dueTime)
            if (newId != questId && db.questDao().get(newId) != null) {
                return@withTransaction ScheduleResult.Rejected(listOf(ScheduleProblem.DUPLICATE))
            }
            db.questDao().deleteIds(listOf(questId))
            db.questDao().insertAll(
                listOf(
                    quest.copy(
                        id = newId,
                        dueAt = quest.date.atTime(dueTime.truncatedTo(ChronoUnit.MINUTES)),
                        targetDepartAt = quest.date.atTime(targetDepartTime.truncatedTo(ChronoUnit.MINUTES)),
                    ),
                ),
            )
            ScheduleResult.Saved(newId)
        }

    suspend fun vacations(): List<LocalDate> = db.vacationDao().all().map { it.date }

    /**
     * 휴가 켜기·끄기 (스펙 6.4, D13).
     * - 오늘과 그 뒤 날짜는 켜고 끌 수 있다. 단, 그날 기록이 있으면 켤 수 없다(그날 출근 XP가 빠지므로).
     * - 지난 날짜는 그날 활동이 전혀 없을 때만 켤 수 있다(깜빡한 휴가). 끌 수는 없다.
     * - 시즌 시작 전 날짜는 안 된다.
     */
    suspend fun setVacation(date: LocalDate, on: Boolean): ScheduleResult = db.withTransaction {
        val problem = when {
            date.isBefore(ledgers.settings().seasonStart) -> ScheduleProblem.BEFORE_SEASON
            !on && date.isBefore(clock.today()) -> ScheduleProblem.PAST_DATE
            on && hasActivity(date) -> ScheduleProblem.DAY_HAS_RECORD
            else -> null
        }
        if (problem != null) return@withTransaction ScheduleResult.Rejected(listOf(problem))
        if (on) db.vacationDao().add(VacationEntity(date)) else db.vacationDao().remove(date)
        ScheduleResult.Saved(date.toString())
    }

    /** 홈 "정리 필요"의 [휴가였어] 대상인지 본다. 지난 날이고, 아직 휴가가 아니고, 그날 활동이 전혀 없어야 한다. */
    suspend fun canMarkMissedVacation(date: LocalDate): Boolean = db.withTransaction {
        date.isBefore(clock.today()) &&
            !date.isBefore(ledgers.settings().seasonStart) &&
            !db.vacationDao().isVacation(date) &&
            !hasActivity(date)
    }

    /** 그날 일정 기록이나 하루 기록이 하나라도 있는지 본다. */
    private suspend fun hasActivity(date: LocalDate): Boolean =
        DayActivity.hasDayRecord(db.dayDao().get(date)) ||
            db.questDao().onDate(date).any { DayActivity.hasRecord(db.recordDao().get(it.id)) }

    private suspend fun dropFutureUnrecorded(templateId: Long) {
        val ids = db.questDao().unrecordedFutureIds(templateId, clock.nowLocal())
        if (ids.isNotEmpty()) db.questDao().deleteIds(ids)
    }
}
