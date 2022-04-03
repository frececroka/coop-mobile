package de.lorenzgorse.coopmobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.Event.SCREEN_VIEW
import com.google.firebase.analytics.FirebaseAnalytics.Param.SCREEN_NAME
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import de.lorenzgorse.coopmobile.client.CoopError
import kotlinx.coroutines.sync.Mutex
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

fun Context.openUri(uri: String) {
    openUri(Uri.parse(uri))
}

fun Context.openUri(uri: Uri) {
    startActivity(Intent(Intent.ACTION_VIEW, uri))
}

fun Fragment.notify(msg: Int) {
    view?.let { Snackbar.make(it, msg, 5000).show() }
}

fun Fragment.notify(msg: CharSequence) {
    view?.let { Snackbar.make(it, msg, 5000).show() }
}

class ActivityResultQuery<I, O>(fragment: Fragment, contract: ActivityResultContract<I, O>) {

    private val mutex = Mutex()

    @Volatile
    private var continuation: Continuation<O>? = null

    private val launcher = fragment.registerForActivityResult(contract) {
        continuation?.resume(it)
        mutex.unlock()
    }

    suspend fun query(input: I): O {
        mutex.lock()
        return suspendCoroutine { c ->
            continuation = c
            launcher.launch(input)
        }
    }

}

class PermissionRequest(fragment: Fragment) {
    private val activityResultQuery =
        ActivityResultQuery(fragment, ActivityResultContracts.RequestPermission())

    suspend fun perform(permission: String): Boolean = activityResultQuery.query(permission)
}

fun Spanned.trim() {
    if (this is SpannableStringBuilder) {
        while (length > 0 && this[length - 1].isWhitespace()) {
            delete(length - 1, length)
        }
        while (length > 0 && this[0].isWhitespace()) {
            delete(0, 1)
        }
    }
}

fun Context.openPlayStore() {
    val appPackageName = packageName
    try {
        val marketUrl = Uri.parse("market://details?id=$appPackageName")
        ContextCompat.startActivity(this, Intent(Intent.ACTION_VIEW, marketUrl), null)
    } catch (anfe: android.content.ActivityNotFoundException) {
        val marketUrl = Uri.parse(
            "https://play.google.com/store/apps/details?id=$appPackageName"
        )
        ContextCompat.startActivity(this, Intent(Intent.ACTION_VIEW, marketUrl), null)
    }
}

fun FirebaseAnalytics.setScreen(screenName: String) {
    logEvent(SCREEN_VIEW, bundleOf(SCREEN_NAME to screenName))
}

fun coopErrorToAnalyticsResult(coopError: CoopError) = coopError::class.simpleName

suspend fun userPseudoId(): String? =
    try {
        waitForTask(Firebase.analytics.appInstanceId)
    } catch (e: Exception) {
        null
    }

suspend fun <T> waitForTask(task: Task<T>): T {
    return suspendCoroutine {
        task.addOnCompleteListener { task ->
            val ex = task.exception
            if (ex != null) {
                it.resumeWithException(ex)
            } else {
                it.resume(task.result)
            }
        }
    }
}

fun gzip(bytes: ByteArray): ByteArray {
    val gzipped = ByteArrayOutputStream()
    val gzip = GZIPOutputStream(gzipped)
    gzip.write(bytes)
    gzip.close()
    return gzipped.toByteArray()
}
