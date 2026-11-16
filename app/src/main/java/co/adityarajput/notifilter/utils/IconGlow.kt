package co.adityarajput.notifilter.utils

import android.content.Context

object IconGlow {
    fun preferences(context: Context) = context.getSharedPreferences("manual_icon_glow", Context.MODE_PRIVATE)
    @Synchronized fun record(context: Context, key: String) {
        val prefs = preferences(context)
        val keys = prefs.getStringSet("active_keys", emptySet())!!.toMutableSet()
        keys.add(key)
        prefs.edit().putStringSet("active_keys", keys).apply()
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
    @Synchronized fun clear(context: Context) = retain(context, emptySet())
    fun isActive(context: Context) = !preferences(context).getStringSet("active_keys", emptySet()).isNullOrEmpty()
}
