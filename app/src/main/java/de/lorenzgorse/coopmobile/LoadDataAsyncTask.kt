package de.lorenzgorse.coopmobile

import android.annotation.SuppressLint
import android.content.Context
import android.os.AsyncTask
import androidx.core.os.bundleOf
import androidx.lifecycle.LiveData
import de.lorenzgorse.coopmobile.CoopClient.CoopException.*
import de.lorenzgorse.coopmobile.CoopModule.coopClientFactory
import de.lorenzgorse.coopmobile.CoopModule.firebaseAnalytics
import de.lorenzgorse.coopmobile.CoopModule.firebaseCrashlytics
import de.lorenzgorse.coopmobile.Either.Left
import de.lorenzgorse.coopmobile.Either.Right
import de.lorenzgorse.coopmobile.LoadDataError.*
import de.lorenzgorse.coopmobile.LoadOnceLiveData.Value
import org.slf4j.LoggerFactory
import java.io.IOException

enum class LoadDataError {
    NO_NETWORK, NO_CLIENT, UNAUTHORIZED, FAILED_LOGIN, HTML_CHANGED, PLAN_UNSUPPORTED
}

sealed class Either<out L, out R> {
    data class Left<L>(val value: L): Either<L, Nothing>()
    data class Right<R>(val value: R): Either<Nothing, R>()
}

fun <P, T> loadData(
    context: Context,
    loader: (client: CoopClient) -> T,
    setFailure: (LoadDataError) -> Unit,
    setSuccess: (T) -> Unit
) {
    val asyncTask = object : LoadDataAsyncTask<P, T>(context) {
        override fun loadData(client: CoopClient) = loader(client)
        override fun onFailure(error: LoadDataError) = setFailure(error)
        override fun onSuccess(result: T) = setSuccess(result)
    }
    asyncTask.execute()
}

@SuppressLint("StaticFieldLeak")
abstract class LoadDataAsyncTask<P, T>(
    val context: Context
): AsyncTask<Void, P, Either<LoadDataError, T>>() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val analytics = firebaseAnalytics(context)

    override fun doInBackground(vararg params: Void): Either<LoadDataError, T> {
        log.info("About to load data in background.")

        fun networkUnavailable(e: IOException) {
            log.error("Network unavailable.", e)
            analytics.logEvent("network_unavailable", null)
            firebaseCrashlytics().recordException(e)
        }

        fun planUnsupported(e: PlanUnsupported) {
            log.error("Plan '${e.plan}' unsupported.")
            analytics.logEvent("plan_unsupported", bundleOf("plan" to e.plan))
            firebaseCrashlytics().recordException(e)
        }

        fun refreshFailed() {
            log.info("Refreshing session failed.")
            analytics.logEvent("refresh_session_failed", null)
            firebaseCrashlytics().recordException(Exception())
        }

        fun refreshedSessionExpired(e: UnauthorizedException) {
            log.info("Refreshed session expired (this should not happen).")
            analytics.logEvent("refreshed_session_expired", bundleOf("redirect" to e.redirect))
            firebaseCrashlytics().recordException(e)
        }

        fun htmlChanged(e: HtmlChangedException) {
            log.error("Html changed.", e)
            firebaseCrashlytics().recordException(e)
        }

        fun sessionExpired(e: UnauthorizedException) {
            log.info("Session expired.")
            analytics.logEvent("session_expired", bundleOf("redirect" to e.redirect))
        }

        return try {
            log.info("Obtaining client from CoopClientFactory.")
            val client = coopClientFactory.get(context)
            if (client == null) {
                log.info("No client available.")
                Left(NO_CLIENT)
            } else {
                log.info("Obtained client $client.")
                log.info("Loading data.")
                analytics.logEventOnce(context, "onb_load_data", null)
                analytics.logEvent("load_data", null)
                try {
                    val data = loadData(client)
                    log.info("Loaded data: [redacted]")
                    Right(data)
                } catch (e: UnauthorizedException) {
                    sessionExpired(e)
                    log.info("Trying to force refresh of session.")
                    val newClient = coopClientFactory.refresh(context, true)
                    if (newClient != null) {
                        log.info("Obtained new client $newClient.")
                        try {
                            log.info("Loading data (again).")
                            val data = loadData(newClient)
                            log.info("Loaded data: [redacted]")
                            Right(data)
                        } catch (e: UnauthorizedException) {
                            refreshedSessionExpired(e)
                            Left(UNAUTHORIZED)
                        }
                    } else {
                        refreshFailed()
                        Left(FAILED_LOGIN)
                    }
                }
            }
        } catch (e: IOException) {
            networkUnavailable(e)
            Left(NO_NETWORK)
        } catch (e: PlanUnsupported) {
            planUnsupported(e)
            Left(PLAN_UNSUPPORTED)
        } catch (e: HtmlChangedException) {
            htmlChanged(e)
            Left(HTML_CHANGED)
        }
    }

    override fun onPostExecute(result: Either<LoadDataError, T>) {
        when (result) {
            is Left -> onFailure(result.value)
            is Right -> onSuccess(result.value)
        }
    }

    abstract fun loadData(client: CoopClient): T

    abstract fun onFailure(error: LoadDataError)
    abstract fun onSuccess(result: T)

}

fun <P, T> liveData(context: Context, loader: (client: CoopClient) -> T): LoadOnceLiveData<T> =
    object : LoadOnceLiveData<T>() {
        override fun loadValue() = loadData<P, T>(context, loader, ::setFailure, ::setSuccess)
    }

abstract class LoadOnceLiveData<T>: LiveData<Value<T>>() {

    sealed class Value<out T> {
        object Initiated: Value<Nothing>()
        data class Progress(val current: Int, val total: Int): Value<Nothing>()
        data class Failure(val error: LoadDataError): Value<Nothing>()
        data class Success<T>(val value: T): Value<T>()
    }

    override fun onActive() {
        if (value == null) {
            internalLoadValue()
        }
    }

    private fun internalLoadValue() {
        value = Value.Initiated
        loadValue()
    }

    abstract fun loadValue()

    fun refresh() {
        internalLoadValue()
    }

    fun setProgress(current: Int, total: Int) {
        value = Value.Progress(current, total)
    }

    fun setFailure(e: LoadDataError) {
        value = Value.Failure(e)
    }

    fun setSuccess(v: T) {
        value = Value.Success(v)
    }

}
