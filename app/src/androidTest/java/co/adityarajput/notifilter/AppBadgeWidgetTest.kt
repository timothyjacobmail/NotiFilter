package co.adityarajput.notifilter

import android.app.Notification
import android.service.notification.StatusBarNotification
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import co.adityarajput.notifilter.data.models.Action
import co.adityarajput.notifilter.data.models.App
import co.adityarajput.notifilter.data.models.Filter
import co.adityarajput.notifilter.data.models.RegexTarget
import co.adityarajput.notifilter.utils.AppBadgeCounts
import org.junit.Assert.*
import org.junit.Test

class AppBadgeWidgetTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun notification(id: Int, title: String, pkg: String = "com.whatsapp", summary: Boolean = false): StatusBarNotification {
        val n = Notification.Builder(context, "widget-test").setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title).setGroup("test").setGroupSummary(summary).build()
        return StatusBarNotification(pkg, pkg, id, null, 1000, 0, 0, n, android.os.Process.myUserHandle(), 0)
    }

    @Test fun countsUpdatesDismissalsSummariesAndCaseMatching() {
        val one = notification(1, "How are you")
        val two = notification(2, "Other message")
        val active = listOf(one, two, one, notification(3, "summary", summary = true), notification(4, "own output", context.packageName))
        assertEquals(mapOf("com.whatsapp" to 2), AppBadgeCounts.count(active, emptyList(), false, context.packageName))
        val filter = Filter(App("WhatsApp", "com.whatsapp"), "how are you", Action.BADGE, RegexTarget.TITLE, matchCase = false)
        assertEquals(mapOf("com.whatsapp" to 1), AppBadgeCounts.count(active, listOf(filter, filter), true, context.packageName))
        assertTrue(AppBadgeCounts.count(active, listOf(filter.copy(matchCase = true)), true, context.packageName).isEmpty())
        assertTrue(AppBadgeCounts.count(active, listOf(filter.copy(enabled = false)), true, context.packageName).isEmpty())
        assertTrue(AppBadgeCounts.count(listOf(two), listOf(filter), true, context.packageName).isEmpty())
        assertTrue(AppBadgeCounts.count(emptyList(), listOf(filter), false, context.packageName).isEmpty())
    }

    @Test fun remoteViewsInflateWithSelectedAppAndDeleteCleansSettings() {
        val id = 987654
        try {
            AppBadgeWidget.prefs(context).edit().putString("apps:$id", context.packageName).putBoolean("filtered:$id", true).commit()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val view = AppBadgeWidget.render(context, id, mapOf(context.packageName to 3)).apply(context, LinearLayout(context))
                assertEquals(1, view.findViewById<LinearLayout>(R.id.badge_rows).childCount)
                assertTrue(view.findViewById<TextView>(R.id.badge_label).text.isNotEmpty())
                assertEquals(android.view.View.GONE, view.findViewById<TextView>(R.id.badge_status).visibility)
                assertEquals(android.view.View.GONE, view.findViewById<TextView>(R.id.badge_label).visibility)
                assertEquals("3", view.findViewById<TextView>(R.id.badge_count).text.toString())
                val height = (48 * context.resources.displayMetrics.density).toInt()
                view.measure(android.view.View.MeasureSpec.makeMeasureSpec(720, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY))
                view.layout(0, 0, 720, height)
                assertTrue(view.findViewById<LinearLayout>(R.id.badge_rows).measuredHeight <= height)
                val bitmap = android.graphics.Bitmap.createBitmap(720, height, android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                context.openFileOutput("app-badge-widget-preview.png", 0).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                val cleared = AppBadgeWidget.render(context, id, emptyMap()).apply(context, LinearLayout(context))
                assertEquals(0, cleared.findViewById<LinearLayout>(R.id.badge_rows).childCount)
                val disconnected = AppBadgeWidget.render(context, id, null).apply(context, LinearLayout(context))
                assertEquals(0, disconnected.findViewById<LinearLayout>(R.id.badge_rows).childCount)
                assertEquals(android.view.View.VISIBLE, disconnected.findViewById<TextView>(R.id.badge_status).visibility)
                AppBadgeWidget.prefs(context).edit().putBoolean("labels:$id", true).commit()
                val labelled = AppBadgeWidget.render(context, id, mapOf(context.packageName to 1)).apply(context, LinearLayout(context))
                assertEquals(android.view.View.VISIBLE, labelled.findViewById<TextView>(R.id.badge_label).visibility)
            }
            AppBadgeWidget().onDeleted(context, intArrayOf(id))
            assertTrue(AppBadgeWidget.selected(context, id).isEmpty())
            assertFalse(AppBadgeWidget.prefs(context).contains("filtered:$id"))
            assertFalse(AppBadgeWidget.prefs(context).contains("labels:$id"))
        } finally { AppBadgeWidget().onDeleted(context, intArrayOf(id)) }
    }
}
