package com.chronoquest.core.service

import androidx.room.withTransaction
import com.chronoquest.core.data.AppSettingsEntity
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.LedgerMapper
import com.chronoquest.rules.model.Ledger
import java.time.LocalDate

class LedgerRepository(private val db: ChronoDatabase) {
    /** 설정 행이 없으면 시즌 시작일 기본값으로 만들어 저장한다. */
    suspend fun settings(): AppSettingsEntity =
        db.settingsDao().get()
            ?: AppSettingsEntity(seasonStart = DEFAULT_SEASON_START).also { db.settingsDao().upsert(it) }

    /** 원장 전체를 한 트랜잭션에서 읽어 판정 엔진 입력으로 바꾼다. */
    suspend fun loadLedger(): Ledger = db.withTransaction {
        LedgerMapper.toLedger(
            settings = settings(),
            quests = db.questDao().all(),
            records = db.recordDao().all(),
            days = db.dayDao().all(),
            vacations = db.vacationDao().all(),
        )
    }

    companion object {
        val DEFAULT_SEASON_START: LocalDate = LocalDate.of(2026, 11, 25)
    }
}
