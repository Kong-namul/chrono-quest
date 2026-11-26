package com.chronoquest.core.service

import androidx.room.withTransaction
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.QuestSource
import com.chronoquest.core.data.ScheduleTemplateEntity
import com.chronoquest.core.data.VacationEntity
import com.chronoquest.core.domain.QuestGenerator
import com.chronoquest.core.domain.QuestIds
import com.chronoquest.core.domain.ScheduleProblem
import com.chronoquest.core.domain.TemplateRules
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
     * 일정 하나의 예정·목표 출발 시각을 바꾼다(이른 기록 확인 뒤 "일정 변경"). 기록이 있으면 바꿀 수 없다.
     * ID가 예정 시각을 담으므로 옛 일정을 지우고 새 ID로 다시 넣는다. 나머지 필드는 그대로다.
     */
    suspend fun reschedule(questId: String, dueTime: LocalTime, targetDepartTime: LocalTime): ScheduleResult =
        db.withTransaction {
            val quest = db.questDao().get(questId)
                ?: return@withTransaction ScheduleResult.Rejected(listOf(ScheduleProblem.NOT_FOUND))
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

    suspend fun setVacation(date: LocalDate, on: Boolean) {
        if (on) db.vacationDao().add(VacationEntity(date)) else db.vacationDao().remove(date)
    }

    private suspend fun dropFutureUnrecorded(templateId: Long) {
        val ids = db.questDao().unrecordedFutureIds(templateId, clock.nowLocal())
        if (ids.isNotEmpty()) db.questDao().deleteIds(ids)
    }
}
