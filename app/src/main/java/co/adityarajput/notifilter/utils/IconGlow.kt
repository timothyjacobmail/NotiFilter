package co.adityarajput.notifilter.utils

import android.content.Context
import androidx.annotation.StringRes
import co.adityarajput.notifilter.R

enum class GlowSpeed(val halfCycleMillis: Long, @StringRes val label: Int) {
    SLOW(2000, R.string.glow_speed_slow),
    NORMAL(1200, R.string.glow_speed_normal),
    FAST(600, R.string.glow_speed_fast),
}

object IconGlow {
    fun preferences(context: Context) = context.getSharedPreferences("manual_icon_glow", Context.MODE_PRIVATE)

    fun speed(context: Context, target: String): GlowSpeed {
        val saved = preferences(context).getString("speed:$target", null)
        return GlowSpeed.entries.firstOrNull { it.name == saved } ?: GlowSpeed.NORMAL
    }

    fun setSpeed(context: Context, target: String, speed: GlowSpeed) {
        preferences(context).edit().putString("speed:$target", speed.name).apply()
        ManualGlow.get(context).refreshGlow()
    }

    @Synchronized fun record(context: Context, key: String, target: String = "") {
        val prefs = preferences(context)
        val keys = prefs.getStringSet("active_keys", emptySet())!!.toMutableSet()
        keys.add(key)
        prefs.edit().putStringSet("active_keys", keys).putString("source:$key", target).apply()
        ManualGlow.get(context).refreshGlow()
    }
    @Synchronized fun remove(context: Context, key: String) {
        val prefs = preferences(context)
        val keys = prefs.getStringSet("active_keys", emptySet())!!.toMutableSet()
        if (!keys.remove(key)) return
        prefs.edit().putStringSet("active_keys", keys).remove("source:$key").apply()
        ManualGlow.get(context).refreshGlow()
    }
    @Synchronized fun retain(context: Context, validKeys: Set<String>) {
        preferences(context).getStringSet("active_keys", emptySet())!!.toList().forEach {
            if (it !in validKeys) remove(context, it)
        }
    }
    @Synchronized fun reconnect(context: Context, activeSources: Map<String, String>) {
        retain(context, activeSources.keys)
        val prefs = preferences(context)
        val editor = prefs.edit()
        for (key in prefs.getStringSet("active_keys", emptySet()).orEmpty()) {
            if (!prefs.contains("source:$key")) editor.putString("source:$key", activeSources[key])
        }
        editor.apply()
    }
    fun activeTargets(context: Context): Set<String> {
        val prefs = preferences(context)
        return prefs.getStringSet("active_keys", emptySet()).orEmpty().mapTo(mutableSetOf()) {
            prefs.getString("source:$it", "").orEmpty()
        }
    }
    @Synchronized fun clearTarget(context: Context, target: String) {
        val prefs = preferences(context)
        prefs.getStringSet("active_keys", emptySet()).orEmpty().toList().forEach { key ->
            if (prefs.getString("source:$key", "").orEmpty() == target) remove(context, key)
        }
    }
    @Synchronized fun clear(context: Context) = retain(context, emptySet())
    fun isActive(context: Context) = !preferences(context).getStringSet("active_keys", emptySet()).isNullOrEmpty()
}
