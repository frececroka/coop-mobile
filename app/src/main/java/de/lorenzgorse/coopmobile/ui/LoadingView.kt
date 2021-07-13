package de.lorenzgorse.coopmobile.ui

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.widget.LinearLayout
import de.lorenzgorse.coopmobile.R
import kotlinx.android.synthetic.main.loading.view.*

class LoadingView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {

    init {
        inflate(context, R.layout.loading, this)
    }

    fun setProgress(current: Int, total: Int) {
        progressBar.isIndeterminate = false
        progressBar.max = total
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            progressBar.setProgress(current, true)
        } else {
            progressBar.progress = current
        }
    }

    fun makeIndeterminate() {
        progressBar.isIndeterminate = true
    }

}
