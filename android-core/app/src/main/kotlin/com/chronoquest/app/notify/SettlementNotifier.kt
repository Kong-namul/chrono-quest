package com.chronoquest.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chronoquest.app.MainActivity
import com.chronoquest.app.R
import com.chronoquest.app.home.celebrationText
import com.chronoquest.core.domain.Celebration
import java.util.concurrent.atomic.AtomicInteger

/**
 * 기록을 정산한 결과를 알린다(스펙 6.3 "+30 XP · 새 칭호: 최초의 각성").
 * [xpGained]는 음수일 수 있다(기록한 일정을 취소하면 -20 등).
 * presenter의 compute 디스패처(백그라운드 스레드)에서 불리므로 UI는 직접 건드리지 말 것.
 */
fun interface SettlementNotifier {
    fun notify(xpGained: Int, celebrations: List<Celebration>)

    companion object {
        val None = SettlementNotifier { _, _ -> }
    }
}

/** 알림 채널 "정산"으로 한 건씩 보낸다. 권한이 없거나 알림이 꺼져 있으면 조용히 넘어간다. */
class AndroidSettlementNotifier(context: Context) : SettlementNotifier {
    private val appContext = context.applicationContext
    // 백그라운드 스레드에서 불리므로 원자적으로 증가시킨다.
    private val nextId = AtomicInteger(1000)

    init {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "정산 알림", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    override fun notify(xpGained: Int, celebrations: List<Celebration>) {
        if (xpGained == 0 && celebrations.isEmpty()) return
        if (!canPost()) return
        val title = if (xpGained >= 0) "+$xpGained XP" else "$xpGained XP"
        val text = if (celebrations.isEmpty()) "기록을 정산했어." else celebrations.joinToString(" · ") { celebrationText(it) }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_chrono)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(appContext).notify(nextId.getAndIncrement(), notification)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        appContext,
        0,
        Intent(appContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun canPost(): Boolean {
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val CHANNEL_ID = "settlement"
    }
}
