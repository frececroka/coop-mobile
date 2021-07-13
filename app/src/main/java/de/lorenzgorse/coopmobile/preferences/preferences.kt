package de.lorenzgorse.coopmobile

import android.content.Context
import android.content.SharedPreferences

fun Context.getCoopSharedPreferences(): SharedPreferences {
    return this.getSharedPreferences("de.lorenzgorse.coopmobile", Context.MODE_PRIVATE)
}
