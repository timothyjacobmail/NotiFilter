package co.adityarajput.notifilter

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
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
            key = manager.activeNotifications.single { it.tag == tag }.key
            IconGlow.record(context, key)
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
