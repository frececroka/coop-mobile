package de.lorenzgorse.coopmobile.components

import android.content.Context
import de.lorenzgorse.coopmobile.preferences.getCoopSharedPreferences

class Counter(context: Context, name: String) {

    private val sharedPreferences = context.getCoopSharedPreferences()
    private val preferencesKey = "counter.$name"

    fun get(): Int = sharedPreferences.getInt(preferencesKey, 0)
    fun set(n: Int) = sharedPreferences.edit().putInt(preferencesKey, n).apply()

    fun increment() = set(get() + 1)
    fun decrement() = set(get() - 1)

}
