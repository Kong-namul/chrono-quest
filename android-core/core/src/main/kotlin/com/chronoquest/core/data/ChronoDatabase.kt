package com.chronoquest.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ScheduleTemplateEntity::class,
        QuestEntity::class,
        QuestRecordEntity::class,
        DayRecordEntity::class,
        VacationEntity::class,
        CelebrationSeenEntity::class,
        AppSettingsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ChronoDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao
    abstract fun questDao(): QuestDao
    abstract fun recordDao(): RecordDao
    abstract fun dayDao(): DayDao
    abstract fun vacationDao(): VacationDao
    abstract fun celebrationDao(): CelebrationDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val NAME = "chrono.db"

        fun build(context: Context): ChronoDatabase =
            Room.databaseBuilder(context.applicationContext, ChronoDatabase::class.java, NAME).build()
    }
}
