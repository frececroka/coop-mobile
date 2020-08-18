package de.lorenzgorse.coopmobile

import android.content.Context

class Fuse(context: Context, name: String) {

    private val sharedPreferences = context.getCoopSharedPreferences()
    private val preferencesKey = "fuse.$name"

    fun burn() {
        sharedPreferences.edit().putBoolean(preferencesKey, true).apply()
    }

    fun mend() {
        sharedPreferences.edit().putBoolean(preferencesKey, false).apply()
    }

    fun isBurnt(): Boolean {
        return sharedPreferences.getBoolean(preferencesKey, false)
    }

}
