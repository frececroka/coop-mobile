package de.lorenzgorse.coopmobile

import android.annotation.SuppressLint
import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.LiveData
import de.lorenzgorse.coopmobile.CoopClient.CoopException.*
import de.lorenzgorse.coopmobile.CoopModule.coopClientFactory
import de.lorenzgorse.coopmobile.Either.Left
import de.lorenzgorse.coopmobile.Either.Right
import de.lorenzgorse.coopmobile.LoadDataError.*
import de.lorenzgorse.coopmobile.LoadOnceLiveData.Value
import java.io.IOException

enum class LoadDataError {
    NO_NETWORK, NO_CLIENT, UNAUTHORIZED, FAILED_LOGIN, HTML_CHANGED, PLAN_UNSUPPORTED
}

sealed class Either<out L, out R> {
    data class Left<L>(val value: L): Either<L, Nothing>()
    data class Right<R>(val value: R): Either<Nothing, R>()
}

fun <P, T> loadData(context: Context, loader: (client: CoopClient) -> T, setFailure: (LoadDataError) -> Unit, setSuccess: (T) -> Unit) {
    val asyncTask = object : LoadDataAsyncTask<P, T>(context) {
        override fun loadData(client: CoopClient): T {
            return loader(client)
        }
        override fun onFailure(error: LoadDataError) {
            setFailure(error)
        }
        override fun onSuccess(result: T) {
            setSuccess(result)
        }
    }
    asyncTask.execute()
}

@SuppressLint("StaticFieldLeak")
abstract class LoadDataAsyncTask<P, T>(val context: Context): AsyncTask<Void, P, Either<LoadDataError, T>>() {

    private val analytics = CoopModule.firebaseAnalyticsFactory(context)

    override fun doInBackground(vararg params: Void): Either<LoadDataError, T> {
        analytics.logEventOnce(context, "onb_load_data", null)
        analytics.logEvent("load_data", null)
        return try {
            val client = coopClientFactory.get(context)
            if (client == null) {
                Log.i("CoopMobile", "Client unavailable.")
                Left(NO_CLIENT)
            } else {
                try {
                    Right(loadData(client))
                } catch (e: UnauthorizedException) {
                    Log.i("CoopMobile", "Session expired.")
                    val bundle = Bundle()
                    bundle.putString("redirect", e.redirect)
                    analytics.logEvent("session_expired", bundle)
                    val newClient = coopClientFactory.refresh(context, true)
                    if (newClient != null) {
                        try {
                            Right(loadData(newClient))
                        } catch (e: UnauthorizedException) {
                            Log.i("CoopMobile", "Refreshed session invalid.")
                            val bundle = Bundle()
                            bundle.putString("redirect", e.redirect)
                            analytics.logEvent("refreshed_session_expired", bundle)
                            Left(UNAUTHORIZED)
                        } catch (e: HtmlChangedException) {
                            Log.e("CoopMobile", "Html changed.", e)
                            Left(HTML_CHANGED)
                        }
                    } else {
                        Log.i("CoopMobile", "Refresh session failed.")
                        analytics.logEvent("refresh_session_failed", null)
                        Left(FAILED_LOGIN)
                    }
                } catch (e: HtmlChangedException) {
                    Log.e("CoopMobile", "Html changed.", e)
                    Left(HTML_CHANGED)
                }
            }
        } catch (e: IOException) {
            Log.i("CoopMobile", "Network unavailable.", e)
            analytics.logEvent("network_unavailable", null)
            Left(NO_NETWORK)
        } catch (e: PlanUnsupported) {
            Log.i("CoopMobile", "Plan '${e.plan}' unsupported.")
            val bundle = Bundle()
            bundle.putString("plan", e.plan)
            analytics.logEvent("plan_unsupported", bundle)
            Left(PLAN_UNSUPPORTED)
        } catch (e: HtmlChangedException) {
            Log.e("CoopMobile", "Html changed.", e)
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

fun <P, T> liveData(context: Context, loader: (client: CoopClient) -> T): LoadOnceLiveData<T> {
    return object : LoadOnceLiveData<T>() {
        override fun loadValue() {
            loadData<P, T>(context, loader, ::setFailure, ::setSuccess)
        }
    }
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
