package com.chronoquest.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.ZoneId
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppContainerTest {
    @Test
    fun `서비스는 컨테이너 안에서 하나씩만 만든다`() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        assertSame(container.gameService, container.gameService)
        assertSame(container.scheduleService, container.scheduleService)
        assertSame(container.catalog, container.catalog)
        assertSame(container.database, container.database)
    }

    @Test
    fun `앱은 컨테이너를 하나만 가진다`() {
        val app = ApplicationProvider.getApplicationContext<ChronoApp>()
        assertSame(app.container, app.container)
    }

    @Test
    fun `앱 시계는 기기 타임존이 바뀌면 따라간다`() {
        val original = TimeZone.getDefault()
        try {
            val clock = SystemZoneClock()
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
            assertEquals(ZoneId.of("Asia/Seoul"), clock.zone)
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
            assertEquals(ZoneId.of("Europe/London"), clock.zone)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
