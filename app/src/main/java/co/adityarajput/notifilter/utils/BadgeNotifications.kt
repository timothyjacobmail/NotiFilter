package co.adityarajput.notifilter.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import co.adityarajput.notifilter.MainActivity
import co.adityarajput.notifilter.R

object BadgeNotifications {
    const val CHANNEL_ID = "notifilter_badge"

    fun refreshLauncherCount(context: Context) {
        if (!android.os.Build.MANUFACTURER.equals("vivo", true) && !android.os.Build.BRAND.equals("iqoo", true)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val count = manager.activeNotifications.count { it.notification.channelId == CHANNEL_ID }
        val component = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return
        // Vivo's badge receiver runs in ABE on current OriginOS devices.
        context.sendBroadcast(
            Intent("launcher.action.CHANGE_APPLICATION_NOTIFICATION_NUM")
                .setPackage("com.vivo.abe")
                .addFlags(0x01000000)
                .putExtra("packageName", context.packageName)
                .putExtra("className", component.className)
                .putExtra("notificationNum", count),
        )
    }

    fun post(context: Context, sourceKey: String, title: String, content: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.badge_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(true)
                setSound(null, null)
                enableVibration(false)
            },
        )
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            "badge:$sourceKey", 0,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(openApp)
                .setNumber(1)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setAutoCancel(true)
                .build(),
        )
    }
}
