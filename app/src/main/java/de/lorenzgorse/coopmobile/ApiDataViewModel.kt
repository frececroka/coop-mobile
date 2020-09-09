package de.lorenzgorse.coopmobile

import android.app.Application
import android.content.Context
import androidx.core.os.bundleOf
import androidx.lifecycle.*
import de.lorenzgorse.coopmobile.CoopModule.coopClientFactory
import de.lorenzgorse.coopmobile.CoopModule.firebaseAnalytics
import de.lorenzgorse.coopmobile.CoopModule.firebaseCrashlytics
import de.lorenzgorse.coopmobile.Either.Left
import de.lorenzgorse.coopmobile.Either.Right
import de.lorenzgorse.coopmobile.LoadDataError.*
import de.lorenzgorse.coopmobile.coopclient.CoopClient
import de.lorenzgorse.coopmobile.coopclient.CoopException.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException

enum class LoadDataError {
    NO_NETWORK, NO_CLIENT, UNAUTHORIZED, FAILED_LOGIN, HTML_CHANGED, PLAN_UNSUPPORTED
}

sealed class Value<out T> {
    object Initiated: Value<Nothing>()
    data class Progress(val current: Int, val total: Int): Value<Nothing>()
    data class Failure(val error: LoadDataError): Value<Nothing>()
    data class Success<T>(val value: T): Value<T>()
}

sealed class Either<out L, out R> {
    data class Left<L>(val value: L): Either<L, Nothing>()
    data class Right<R>(val value: R): Either<Nothing, R>()
}

private val log: Logger = LoggerFactory.getLogger(ApiDataViewModel::class.java)

abstract class ApiDataViewModel<T>(
    private val app: Application,
    private val loader: (suspend (Int, Int) -> Unit) -> suspend (client: CoopClient) -> T
) : AndroidViewModel(app) {

    private val refresh = MutableLiveData(true)

    val data: LiveData<Value<T>> = Transformations.switchMap(refresh) {
        liveData {
            emit(Value.Initiated)
            val progress: suspend (Int, Int) -> Unit = { current, total ->
                emit(Value.Progress(current, total)) }
            when (val value = loadData(app.applicationContext, loader(progress))) {
                is Left -> emit(Value.Failure(value.value))
                is Right -> emit(Value.Success(value.value))
            }
        }
    }

    fun refresh() {
        refresh.value = true
    }

}

suspend fun <T> loadData(context: Context, loader: suspend (client: CoopClient) -> T): Either<LoadDataError, T> {
    log.info("About to load data.")

    val analytics = firebaseAnalytics(context)

    fun networkUnavailable(e: IOException) {
        log.error("Network unavailable.", e)
        analytics.logEvent("LoadData_NoNetwork", null)
    }

    fun planUnsupported(e: PlanUnsupported) {
        log.error("Plan '${e.plan}' unsupported.")
        analytics.logEvent("LoadData_PlanUnsupported", bundleOf("plan" to e.plan))
        firebaseCrashlytics().recordException(e)
    }

    fun refreshFailed() {
        log.info("Refreshing session failed.")
        analytics.logEvent("LoadData_RefreshSessionFailed", null)
    }

    fun refreshedSessionExpired(e: UnauthorizedException) {
        log.info("Refreshed session expired: ${e.redirect} (this should not happen).")
        analytics.logEvent("refreshed_session_expired", bundleOf("redirect" to e.redirect))
        firebaseCrashlytics().recordException(e)
    }

    fun htmlChanged(e: HtmlChangedException) {
        log.error("Html changed.", e)
        analytics.logEvent("LoadData_HtmlChanged", null)
        firebaseCrashlytics().recordException(e)
    }

    fun sessionExpired(e: UnauthorizedException) {
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
            Left(NO_CLIENT)
        } else {
            log.info("Obtained client $client.")
            log.info("Loading data.")
            analytics.logEventOnce(context, "onb_load_data", null)
            analytics.logEvent("LoadData_Start", null)
            try {
                val data = loader(client)
                log.info("Loaded data: [redacted]")
                loadedData()
                Right(data)
            } catch (e: UnauthorizedException) {
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
