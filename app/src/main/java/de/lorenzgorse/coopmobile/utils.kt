package de.lorenzgorse.coopmobile

import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
    view?.let { Snackbar.make(it, msg, 5000).show() }
}

fun Fragment.notify(msg: CharSequence) {
    view?.let { Snackbar.make(it, msg, 5000).show() }
}

suspend fun Fragment.requestPermission(permission: String): Boolean =
    launchActivityForResult(ActivityResultContracts.RequestPermission(), permission)

suspend fun <I, O> Fragment.launchActivityForResult(
    contract: ActivityResultContract<I, O>,
    input: I
): O = suspendCoroutine { cont ->
    registerForActivityResult(contract) {
        cont.resume(it)
    }.launch(input)
}
