package de.lorenzgorse.coopmobile.ui.debug

import android.content.Context
import de.lorenzgorse.coopmobile.getCoopSharedPreferences

object DebugMode {
    fun isEnabled(context: Context): Boolean {
        return context.getCoopSharedPreferences().getBoolean("debug_mode", false)
    }
    fun enable(context: Context) {
        context.getCoopSharedPreferences().edit().putBoolean("debug_mode", true).apply()
    }
}
