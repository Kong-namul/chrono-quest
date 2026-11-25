package com.chronoquest.core.domain

import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.QuestSource
import com.chronoquest.core.data.ScheduleTemplateEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 반복 템플릿 → 일정 인스턴스 (스펙 6.4). */
object QuestGenerator {
    const val HORIZON_DAYS = 7

    /** [from]부터 [days]일 동안 만들 일정. 비활성·잘못된 템플릿은 건너뛰고, 같은 ID는 처음 것만 남긴다. */
    fun generate(
        templates: List<ScheduleTemplateEntity>,
        from: LocalDate,
        days: Int = HORIZON_DAYS,
    ): List<QuestEntity> {
        val usable = templates.filter { it.active && TemplateRules.validate(it).isEmpty() }
        return (0 until days)
            .flatMap { offset ->
                val date = from.plusDays(offset.toLong())
                usable
                    .filter { (it.daysMask and TemplateRules.bit(date.dayOfWeek)) != 0 }
                    .map { questFor(it, date) }
            }
            .distinctBy { it.id }
    }

    private fun questFor(template: ScheduleTemplateEntity, date: LocalDate) = QuestEntity(
        id = QuestIds.of(date, template.kind, template.dueTime),
        date = date,
        kind = template.kind,
        title = template.title,
        dueAt = date.atTime(template.dueTime.truncatedTo(ChronoUnit.MINUTES)),
        targetDepartAt = date.atTime(template.targetDepartTime.truncatedTo(ChronoUnit.MINUTES)),
        source = QuestSource.TEMPLATE,
        templateId = template.id,
        fromPlaceId = template.fromPlaceId,
        toPlaceId = template.toPlaceId,
    )
}
