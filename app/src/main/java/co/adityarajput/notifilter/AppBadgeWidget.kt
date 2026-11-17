package co.adityarajput.notifilter

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import co.adityarajput.notifilter.services.NotificationListener

/** A live notification count, not an unread-message count supplied by individual apps. */
class AppBadgeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = ids.forEach { update(context, it) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = update(context, id)
    override fun onDeleted(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove("apps:$it"); remove("filtered:$it"); remove("labels:$it") } }.apply()
    }

    companion object {
        fun prefs(context: Context) = context.getSharedPreferences("app_badge_widgets", Context.MODE_PRIVATE)
        fun selected(context: Context, id: Int): List<String> = prefs(context).getString("apps:$id", "")!!.split('|').filter { it.isNotEmpty() }
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, AppBadgeWidget::class.java)).forEach { update(context, it) }
        }

        fun update(context: Context, id: Int) {
            AppWidgetManager.getInstance(context).updateAppWidget(id, render(context, id))
        }

        private fun liveCounts(context: Context, id: Int): Map<String, Int>? =
            if (NotificationListener.isServiceInitialized) NotificationListener.instance.widgetBadgeCounts(prefs(context).getBoolean("filtered:$id", false)) else null

        fun render(context: Context, id: Int, counts: Map<String, Int>? = liveCounts(context, id)): RemoteViews {
            val manager = AppWidgetManager.getInstance(context)
            val apps = selected(context, id)
            val showLabels = prefs(context).getBoolean("labels:$id", false)
            val view = RemoteViews(context.packageName, R.layout.app_badge_widget)
            val configure = Intent(context, AppBadgeWidgetConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            view.setOnClickPendingIntent(R.id.badge_settings, PendingIntent.getActivity(context, id, configure, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            view.setTextViewText(R.id.badge_status, when {
                apps.isEmpty() -> "Tap Configure to select apps"
                counts == null -> "Notification access disconnected · tap Configure"
                else -> ""
            })
            view.setViewVisibility(R.id.badge_status, if (apps.isEmpty() || counts == null) View.VISIBLE else View.GONE)
            view.removeAllViews(R.id.badge_rows)
            val width = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val columns = ((width - 28) / 52).coerceIn(1, 12)
            apps.filter { (counts?.get(it) ?: 0) > 0 }.chunked(columns).forEach { packages ->
                val row = RemoteViews(context.packageName, R.layout.app_badge_row)
                packages.forEach { pkg ->
                    try {
                        val info = context.packageManager.getApplicationInfo(pkg, 0)
                        val label = context.packageManager.getApplicationLabel(info).toString()
                        val item = RemoteViews(context.packageName, R.layout.app_badge_item)
                        val pixels = (40 * context.resources.displayMetrics.density).toInt()
                        item.setImageViewBitmap(R.id.badge_icon, context.packageManager.getApplicationIcon(info).toBitmap(pixels, pixels))
                        item.setTextViewText(R.id.badge_label, label)
                        item.setViewVisibility(R.id.badge_label, if (showLabels) View.VISIBLE else View.GONE)
                        val count = counts?.get(pkg) ?: 0
                        item.setTextViewText(R.id.badge_count, if (count > 99) "99+" else count.toString())
                        item.setViewVisibility(R.id.badge_count, if (count > 0) View.VISIBLE else View.GONE)
                        item.setContentDescription(R.id.badge_item, "$label, ${if (counts == null) "count unavailable" else "$count notifications"}")
                        context.packageManager.getLaunchIntentForPackage(pkg)?.let { launch ->
                            item.setOnClickPendingIntent(R.id.badge_item, PendingIntent.getActivity(context, pkg.hashCode(), launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                        }
                        row.addView(R.id.badge_row, item)
                    } catch (_: android.content.pm.PackageManager.NameNotFoundException) { /* App uninstalled. */ }
                }
                view.addView(R.id.badge_rows, row)
            }
            return view
        }
    }
}
