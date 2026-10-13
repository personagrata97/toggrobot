package com.toggrobot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri

object Notifier {
    private const val CH_STATUS = "status"
    private const val CH_ALERT = "alert_v2"
    private const val CH_INFO = "info"
    private const val ID_STATUS = 1
    const val ID_PROBLEM = 2

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_STATUS, "Durum (sessiz)", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) }
        )
        nm.createNotificationChannel(NotificationChannel(CH_INFO, "Bilgi", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "TOGG fiyat alarmı", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 250, 600, 250, 900)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
            }
        )
    }

    /** Robot id + tur icin sabit bildirim numarasi (her robotun bildirimi ayri durur). */
    fun idFor(robotId: String, kind: String) = 1000 + ((robotId + kind).hashCode() and 0xFFFFF)

    private fun openSite(ctx: Context, id: Int) = PendingIntent.getActivity(
        ctx, id, Intent(Intent.ACTION_VIEW, Uri.parse(Api.SITE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE
    )

    fun alert(ctx: Context, id: Int, title: String, text: String, @Suppress("UNUSED_PARAMETER") url: String) {
        val n = Notification.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text.lineSequence().firstOrNull() ?: text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openSite(ctx, id))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }

    fun info(ctx: Context, id: Int, title: String, text: String) {
        val n = Notification.Builder(ctx, CH_INFO)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openSite(ctx, id))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }

    /** Sessiz, kucuk durum bildirimi: son kontrol zamani ve ozet. */
    fun status(ctx: Context, summary: String) {
        if (!Settings(ctx).running) return
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Togg Robot • son kontrol ${Log.time().substringAfter(' ').substring(0, 5)}")
            .setContentText(summary.lineSequence().firstOrNull() ?: "")
            .setStyle(Notification.BigTextStyle().bigText(summary))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_STATUS, n)
    }

    fun cancelStatus(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(ID_STATUS)
    }
}
