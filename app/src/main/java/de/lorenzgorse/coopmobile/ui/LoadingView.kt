package de.lorenzgorse.coopmobile.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.ProgressBar
import de.lorenzgorse.coopmobile.R

class LoadingView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {

    private val progressBar: ProgressBar

    init {
        inflate(context, R.layout.loading, this)
        progressBar = findViewById(R.id.progressBar)
    }

    fun setProgress(current: Int, total: Int) {
        progressBar.isIndeterminate = false
        progressBar.max = total
        progressBar.setProgress(current, true)
    }

    fun makeIndeterminate() {
        progressBar.isIndeterminate = true
    }

}
