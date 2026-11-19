package co.adityarajput.notifilter

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import co.adityarajput.notifilter.utils.GlowSpeed
import co.adityarajput.notifilter.utils.IconGlow
import org.junit.Assert.*
import org.junit.Test

class ManualGlowTest {
    @Test fun matchesPersistUntilTheirNotificationsAreRemoved() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("test_$name", mode)
        }
        try {
            IconGlow.record(context, "one")
            IconGlow.record(context, "two")
            assertTrue(IconGlow.isActive(context))
            IconGlow.remove(context, "one")
            assertTrue(IconGlow.isActive(context))
            IconGlow.remove(context, "not-a-match")
            assertTrue(IconGlow.isActive(context))
            IconGlow.remove(context, "two")
            assertFalse(IconGlow.isActive(context))
            IconGlow.record(context, "stale")
            IconGlow.retain(context, emptySet())
            assertFalse(IconGlow.isActive(context))
            assertEquals(co.adityarajput.notifilter.data.models.Action.GLOW, co.adityarajput.notifilter.data.models.Action.fromString("GLOW"))
        } finally { IconGlow.preferences(context).edit().clear().commit() }
    }
    @Test fun gmailAndWhatsappClearIndependently() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("multi_test_$name", mode)
        }
        val prefs = IconGlow.preferences(context)
        try {
            prefs.edit().clear().putStringSet("active_keys", setOf("old-chat")).commit()
            IconGlow.reconnect(context, mapOf("old-chat" to "com.whatsapp"))
            IconGlow.record(context, "new-chat", "com.whatsapp")
            IconGlow.record(context, "mail", "com.google.android.gm")
            assertEquals(setOf("com.whatsapp", "com.google.android.gm"), IconGlow.activeTargets(context))
            IconGlow.remove(context, "new-chat")
            assertEquals(2, IconGlow.activeTargets(context).size)
            IconGlow.clearTarget(context, "com.google.android.gm")
            assertEquals(setOf("com.whatsapp"), IconGlow.activeTargets(context))
            IconGlow.remove(context, "old-chat")
            assertTrue(IconGlow.activeTargets(context).isEmpty())
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun pulseSpeedIsSavedPerAppAndDefaultsToNormal() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences("speed_test_$name", mode)
        }
        try {
            assertEquals(GlowSpeed.NORMAL, IconGlow.speed(context, "com.whatsapp"))
            IconGlow.setSpeed(context, "com.whatsapp", GlowSpeed.FAST)
            IconGlow.setSpeed(context, "com.google.android.gm", GlowSpeed.SLOW)
            assertEquals(GlowSpeed.FAST, IconGlow.speed(context, "com.whatsapp"))
            assertEquals(GlowSpeed.SLOW, IconGlow.speed(context, "com.google.android.gm"))
            assertEquals(1200L, GlowSpeed.NORMAL.halfCycleMillis)
        } finally {
            IconGlow.preferences(context).edit().clear().commit()
        }
    }

    @Test fun openingAnAppKeepsGlowUntilNotificationIsRemoved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val channel = "glow_lifecycle_test"
        val tag = "glow-lifecycle-test"
        var key: String? = null
        fun await(condition: () -> Boolean): Boolean {
            repeat(100) { if (condition()) return true; Thread.sleep(100) }
            return false
        }
        try {
            context.startActivity(android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            assertTrue("Notification listener should connect", await { co.adityarajput.notifilter.services.NotificationListener.isServiceInitialized })
            manager.createNotificationChannel(android.app.NotificationChannel(channel, "Glow lifecycle test", android.app.NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
            manager.notify(tag, 987654, androidx.core.app.NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("Glow lifecycle test").setSilent(true).build())
            assertTrue(await { manager.activeNotifications.any { it.tag == tag } })
            // Use the listener's posted key, as the real glow action does. Some devices
            // return a different key through the posting application's NotificationManager.
            assertTrue("Listener should see the test notification", await {
                key = runCatching {
                    co.adityarajput.notifilter.services.NotificationListener.instance.activeNotifications
                        .firstOrNull { it.tag == tag && it.packageName == context.packageName }?.key
                }.getOrNull()
                key != null
            })
            IconGlow.record(context, requireNotNull(key))
            Thread.sleep(1500)
            assertTrue("Opening the app must not consume a pending glow", IconGlow.preferences(context).getStringSet("active_keys", emptySet())!!.contains(key))
            manager.cancel(tag, 987654)
            assertTrue("Removing the notification should clear its glow", await { !IconGlow.preferences(context).getStringSet("active_keys", emptySet())!!.contains(key) })
        } finally {
            manager.cancel(tag, 987654)
            key?.let { IconGlow.remove(context, it) }
            manager.deleteNotificationChannel(channel)
        }
    }

}
