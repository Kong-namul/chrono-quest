package com.chronoquest.app.notify

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.core.domain.Celebration
import com.chronoquest.core.domain.CelebrationKind
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class SettlementNotifierTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun grant() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `XP와 새 칭호를 한 알림으로 보낸다`() {
        AndroidSettlementNotifier(context).notify(
            30,
            listOf(
                Celebration("title:1:1", CelebrationKind.TITLE, titleId = 1, detail = "최초의 각성"),
                Celebration("title:21:1", CelebrationKind.TITLE, titleId = 21, detail = "운명의 첫걸음"),
            ),
        )
        val notification = shadowOf(manager).allNotifications.single()
        assertEquals("+30 XP", notification.extras.getString(NotificationCompat.EXTRA_TITLE))
        val text = notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString()
        assertTrue("새 칭호: 최초의 각성" in text)
        assertTrue("새 칭호: 운명의 첫걸음" in text)
        assertEquals(AndroidSettlementNotifier.CHANNEL_ID, notification.channelId)
    }

    @Test
    fun `XP 변화도 연출도 없으면 알리지 않는다`() {
        AndroidSettlementNotifier(context).notify(0, emptyList())
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun `XP가 줄면 줄었다고 알린다`() {
        AndroidSettlementNotifier(context).notify(-20, emptyList())
        assertEquals("-20 XP", shadowOf(manager).allNotifications.single().extras.getString(NotificationCompat.EXTRA_TITLE))
    }
}
