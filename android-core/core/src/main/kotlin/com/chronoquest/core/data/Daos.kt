package com.chronoquest.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import java.time.LocalDate
import java.time.LocalDateTime

@Dao
interface TemplateDao {
    @Query("SELECT * FROM schedule_template ORDER BY id")
    suspend fun all(): List<ScheduleTemplateEntity>

    @Insert
    suspend fun insert(template: ScheduleTemplateEntity): Long

    @Update
    suspend fun update(template: ScheduleTemplateEntity)

    @Query("DELETE FROM schedule_template WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface QuestDao {
    @Query("SELECT * FROM quest")
    suspend fun all(): List<QuestEntity>

    @Query("SELECT * FROM quest WHERE id = :id")
    suspend fun get(id: String): QuestEntity?

    @Query("SELECT * FROM quest WHERE date = :date ORDER BY dueAt, id")
    suspend fun onDate(date: LocalDate): List<QuestEntity>

    @Query("SELECT id FROM quest")
    suspend fun allIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(quests: List<QuestEntity>)

    @Update
    suspend fun update(quest: QuestEntity)

    @Query("DELETE FROM quest WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<String>)

    /**
     * 템플릿이 만든 예정 일정 중 [after]보다 뒤에 있고 기록도 없는 것. 템플릿을 고치거나 지울 때 다시 만든다.
     * 취소한 일정은 남겨 둬야 그날 일정이 되살아나지 않는다.
     */
    @Query(
        "SELECT q.id FROM quest q LEFT JOIN quest_record r ON r.questId = q.id " +
            "WHERE q.templateId = :templateId AND q.dueAt > :after AND r.questId IS NULL " +
            "AND q.status = 'SCHEDULED'",
    )
    suspend fun unrecordedFutureIds(templateId: Long, after: LocalDateTime): List<String>
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM quest_record")
    suspend fun all(): List<QuestRecordEntity>

    @Query("SELECT * FROM quest_record WHERE questId = :questId")
    suspend fun get(questId: String): QuestRecordEntity?

    @Upsert
    suspend fun upsert(record: QuestRecordEntity)
}

@Dao
interface DayDao {
    @Query("SELECT * FROM day_record")
    suspend fun all(): List<DayRecordEntity>

    @Query("SELECT * FROM day_record WHERE date = :date")
    suspend fun get(date: LocalDate): DayRecordEntity?

    @Upsert
    suspend fun upsert(day: DayRecordEntity)
}

@Dao
interface VacationDao {
    @Query("SELECT * FROM vacation ORDER BY date")
    suspend fun all(): List<VacationEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(vacation: VacationEntity)

    @Query("DELETE FROM vacation WHERE date = :date")
    suspend fun remove(date: LocalDate)
}

@Dao
interface CelebrationDao {
    @Query("SELECT grantKey FROM celebration_seen")
    suspend fun seenKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSeen(rows: List<CelebrationSeenEntity>)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 0")
    suspend fun get(): AppSettingsEntity?

    @Upsert
    suspend fun upsert(settings: AppSettingsEntity)
}
