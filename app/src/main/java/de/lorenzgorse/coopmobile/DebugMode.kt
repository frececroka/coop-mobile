package de.lorenzgorse.coopmobile

import android.content.Context

object DebugMode {
    fun isEnabled(context: Context): Boolean {
        return context.getCoopSharedPreferences().getBoolean("debug_mode", false)
    }
    fun enable(context: Context) {
        context.getCoopSharedPreferences().edit().putBoolean("debug_mode", true).apply()
    }
}
