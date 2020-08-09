package de.lorenzgorse.coopmobile

import android.content.Context
import android.graphics.Color

class ThemeUtils(private val context: Context) {

    fun textColor() = getColor(R.attr.colorOnBackground)

    fun getColor(attr: Int): Int {
        val attributes = context.theme.obtainStyledAttributes(
            R.style.AppTheme, arrayOf(attr).toIntArray())
        return attributes.getColor(0, Color.RED)
    }

}
