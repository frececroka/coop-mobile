package de.lorenzgorse.coopmobile.data

import android.content.Context
import androidx.core.os.bundleOf
import com.google.firebase.ktx.Firebase
import com.google.firebase.perf.ktx.performance
import de.lorenzgorse.coopmobile.CoopModule.coopClientFactory
import de.lorenzgorse.coopmobile.CoopModule.firebaseAnalytics
import de.lorenzgorse.coopmobile.CoopModule.firebaseCrashlytics
import de.lorenzgorse.coopmobile.coopclient.CoopClient
import de.lorenzgorse.coopmobile.coopclient.CoopException
import de.lorenzgorse.coopmobile.data.Either.Left
import de.lorenzgorse.coopmobile.data.Either.Right
import de.lorenzgorse.coopmobile.logEventOnce
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException

sealed class CoopError {
    object NoNetwork : CoopError()
    object NoClient: CoopError()
    object Unauthorized : CoopError()
    object FailedLogin : CoopError()
    data class BadHtml(val ex: CoopException.BadHtml) : CoopError()
    data class HtmlChanged(val ex: CoopException.HtmlChanged) : CoopError()
    object PlanUnsupported : CoopError()
    data class Other(val message: String) : CoopError()
}

sealed class Either<out L, out R> {
    data class Left<L>(val value: L): Either<L, Nothing>()
    data class Right<R>(val value: R): Either<Nothing, R>()
    fun right(): R? = when (this) {
        is Right -> value
        else -> null
    }
}

private val log: Logger = LoggerFactory.getLogger("LoadData")

suspend fun <T> loadData(context: Context, loader: suspend (client: CoopClient) -> T): Either<CoopError, T> {
    log.info("About to load data.")

    val analytics = firebaseAnalytics(context)

    fun networkUnavailable(e: IOException) {
        log.error("Network unavailable.", e)
        analytics.logEvent("LoadData_NoNetwork", null)
    }

    fun planUnsupported(e: CoopException.PlanUnsupported) {
        log.error("Plan '${e.plan}' unsupported.")
        analytics.logEvent("LoadData_PlanUnsupported", bundleOf("plan" to e.plan))
        firebaseCrashlytics().recordException(e)
    }

    fun refreshFailed() {
        log.info("Refreshing session failed.")
        analytics.logEvent("LoadData_RefreshSessionFailed", null)
    }

    fun refreshedSessionExpired(e: CoopException.Unauthorized) {
        log.info("Refreshed session expired: ${e.redirect} (this should not happen).")
        analytics.logEvent("refreshed_session_expired", bundleOf("redirect" to e.redirect))
        firebaseCrashlytics().recordException(e)
    }

    fun badHtml(e: CoopException.BadHtml) {
        log.error("Bad HTML.", e)
        analytics.logEvent("LoadData_BadHtml", null)
        firebaseCrashlytics().recordException(e)
    }

    fun htmlChanged(e: CoopException.HtmlChanged) {
        log.error("HTML changed.", e)
        analytics.logEvent("LoadData_HtmlChanged", null)
        firebaseCrashlytics().recordException(e)
    }

    fun sessionExpired(e: CoopException.Unauthorized) {
        log.info("Session expired: ${e.redirect}.")
        analytics.logEvent("LoadData_SessionExpired", bundleOf("redirect" to e.redirect))
    }

    fun loadedData() {
        analytics.logEvent("LoadData_Success", null)
        analytics.logEventOnce(context, "onb_loaded_data", null)
    }

    return try {
        log.info("Obtaining client from CoopClientFactory.")
        val client = coopClientFactory.get(context)
        if (client == null) {
            log.info("No client available.")
            Left(CoopError.NoClient)
        } else {
            log.info("Obtained client $client.")
            log.info("Loading data.")
            analytics.logEventOnce(context, "onb_load_data", null)
            analytics.logEvent("LoadData_Start", null)
            val loadDataTrace = Firebase.performance.newTrace("LoadData")
            loadDataTrace.start()
            loadDataTrace.incrementMetric("Attempt", 1)
            try {
                val data = loader(client)
                log.info("Loaded data: [redacted]")
                loadedData()
                loadDataTrace.incrementMetric("Success", 1)
                Right(data)
            } catch (e: CoopException.Unauthorized) {
                sessionExpired(e)
                log.info("Trying to force refresh of session.")
                val newClient = coopClientFactory.refresh(context, true)
                if (newClient != null) {
                    log.info("Obtained new client $newClient.")
                    try {
                        log.info("Loading data (again).")
                        val data = loader(newClient)
                        log.info("Loaded data: [redacted]")
                        loadedData()
                        loadDataTrace.incrementMetric("Success", 1)
                        Right(data)
                    } catch (e: CoopException.Unauthorized) {
                        refreshedSessionExpired(e)
                        Left(CoopError.Unauthorized)
                    }
                } else {
                    refreshFailed()
                    Left(CoopError.FailedLogin)
                }
            } finally {
                loadDataTrace.stop()
            }
        }
    } catch (e: IOException) {
        networkUnavailable(e)
        Left(CoopError.NoNetwork)
    } catch (e: CoopException.PlanUnsupported) {
        planUnsupported(e)
        Left(CoopError.PlanUnsupported)
    } catch (e: CoopException.BadHtml) {
        badHtml(e)
        Left(CoopError.BadHtml(e))
    } catch (e: CoopException.HtmlChanged) {
        htmlChanged(e)
        Left(CoopError.HtmlChanged(e))
    }
}
