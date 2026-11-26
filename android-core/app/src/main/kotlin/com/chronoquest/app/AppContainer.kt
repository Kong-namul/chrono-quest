package com.chronoquest.app

import android.content.Context
import com.chronoquest.app.notify.SettlementNotifier
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.service.GameService
import com.chronoquest.core.service.ScheduleService
import com.chronoquest.rules.engine.Engine
import com.chronoquest.rules.titles.TitleCatalog
import java.time.Clock

/**
 * 앱 전체에서 하나씩만 쓰는 객체들(인계 메모 A2b "프로세스당 하나").
 * 화면, 알림 리시버, 워커는 모두 여기서 꺼내 쓴다.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val clock: Clock = SystemZoneClock()
    val catalog: TitleCatalog by lazy { TitleCatalog.load() }
    val database: ChronoDatabase by lazy { ChronoDatabase.build(appContext) }
    val engine: Engine by lazy { Engine(catalog) }
    val gameService: GameService by lazy { GameService(database, clock, catalog, engine) }
    val scheduleService: ScheduleService by lazy { ScheduleService(database, clock) }
    val notifier: SettlementNotifier by lazy { SettlementNotifier.None }
}
