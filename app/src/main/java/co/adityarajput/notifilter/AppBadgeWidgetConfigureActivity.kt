package co.adityarajput.notifilter

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class AppBadgeWidgetConfigureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val chosen = AppBadgeWidget.selected(this, id).toMutableSet()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 16) }
        root.addView(TextView(this).apply { text = "App notification badges"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Choose up to 12 apps. Counts track active notifications, excluding group summaries, and clear when notifications are dismissed. Resize the widget to fit more icons." })
        root.addView(Button(this).apply { text = "Notification access"; setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } })
        val filtered = CheckBox(this).apply { text = "Count only notifications matching enabled filters (including text and case matching)"; isChecked = AppBadgeWidget.prefs(this@AppBadgeWidgetConfigureActivity).getBoolean("filtered:$id", false) }
        root.addView(filtered)
        val labelsToggle = CheckBox(this).apply { text = "Show app names (adds height)"; isChecked = AppBadgeWidget.prefs(this@AppBadgeWidgetConfigureActivity).getBoolean("labels:$id", false) }
        root.addView(labelsToggle)
        val apps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        val labels = apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        val list = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            adapter = ArrayAdapter(this@AppBadgeWidgetConfigureActivity, android.R.layout.simple_list_item_multiple_choice, labels)
            apps.forEachIndexed { index, app -> setItemChecked(index, app.activityInfo.packageName in chosen) }
            setOnItemClickListener { _, _, position, _ ->
                val pkg = apps[position].activityInfo.packageName
                if (isItemChecked(position)) chosen.add(pkg) else chosen.remove(pkg)
            }
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply {
            text = "Save widget"
            setOnClickListener {
                if (chosen.isEmpty()) { Toast.makeText(this@AppBadgeWidgetConfigureActivity, "Select at least one app", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                if (chosen.size > 12) { Toast.makeText(this@AppBadgeWidgetConfigureActivity, "Choose up to 12 apps per widget", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                AppBadgeWidget.prefs(this@AppBadgeWidgetConfigureActivity).edit()
                    .putString("apps:$id", apps.map { it.activityInfo.packageName }.filter { it in chosen }.joinToString("|"))
                    .putBoolean("filtered:$id", filtered.isChecked).apply()
                AppBadgeWidget.prefs(this@AppBadgeWidgetConfigureActivity).edit().putBoolean("labels:$id", labelsToggle.isChecked).apply()
                AppBadgeWidget.update(this@AppBadgeWidgetConfigureActivity, id)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                finish()
            }
        })
        setContentView(root)
    }
}
