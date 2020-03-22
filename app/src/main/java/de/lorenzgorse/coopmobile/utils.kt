package de.lorenzgorse.coopmobile

import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

fun handleLoadDataError(
    error: LoadDataError,
    showNoNetwork: () -> Unit,
    showUpdateNecessary: () -> Unit,
    showPlanUnsupported: () -> Unit,
    goToLogin: () -> Unit
) {
    when (error) {
        LoadDataError.NO_NETWORK -> showNoNetwork()
        LoadDataError.HTML_CHANGED -> showUpdateNecessary()
        LoadDataError.PLAN_UNSUPPORTED -> showPlanUnsupported()
        LoadDataError.NO_CLIENT -> goToLogin()
        LoadDataError.UNAUTHORIZED -> goToLogin()
        LoadDataError.FAILED_LOGIN -> goToLogin()
    }
}

fun Fragment.notify(msg: Int) {
    view.doMaybe { Snackbar.make(it, msg, 5000).show() }
}

fun Fragment.notify(msg: CharSequence) {
    view.doMaybe { Snackbar.make(it, msg, 5000).show() }
}

fun <T> T?.doMaybe(block: (T) -> Unit) {
    if (this != null) block(this)
}
