package co.adityarajput.notifilter

import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import co.adityarajput.notifilter.utils.BadgeNotifications
import org.junit.Assert.*
import org.junit.Test

class BadgeNotificationTest {
    @Test
    fun postsBadgeAndUpdatesSameSourceWithoutDuplicates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission("com.vivo.notification.permission.BADGE_ICON"))
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            BadgeNotifications.post(context, "device-test", "NotiFilter badge test", "Testing your app icon badge")
            Thread.sleep(1000)
            BadgeNotifications.post(context, "device-test", "NotiFilter badge test", "Updated test notification")
            Thread.sleep(1000)
            BadgeNotifications.refreshLauncherCount(context)
            assertTrue(manager.getNotificationChannel(BadgeNotifications.CHANNEL_ID).canShowBadge())
            val notifications = manager.activeNotifications.filter { it.tag == "badge:device-test" }
            assertEquals(1, notifications.size)
            assertEquals(1, notifications.single().notification.number)
            // Leave time to inspect the launcher's actual badge state during this device test.
            Thread.sleep(20000)
        } finally {
            manager.cancel("badge:device-test", 0)
            Thread.sleep(500)
            BadgeNotifications.refreshLauncherCount(context)
        }
    }
}
