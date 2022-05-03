package de.lorenzgorse.coopmobile

import android.content.Context
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import de.lorenzgorse.coopmobile.client.Config
import de.lorenzgorse.coopmobile.client.RemoteConfig
import de.lorenzgorse.coopmobile.client.refreshing.RealCoopClientFactory
import de.lorenzgorse.coopmobile.client.refreshing.RefreshingSessionCoopClient
import de.lorenzgorse.coopmobile.client.simple.*
import de.lorenzgorse.coopmobile.preferences.SharedPreferencesCredentialsStore
import okhttp3.CookieJar

// TODO: replace this with DI library

fun createClient(context: Context): CoopClient =
    MonitoredCoopClient(RefreshingSessionCoopClient(createCoopClientFactory(context)))

fun createCoopClientFactory(context: Context) = RealCoopClientFactory(
    createCredentialsStore(context),
    createCoopLogin(context),
    { sessionId -> staticSessionCoopClient(context, sessionId) },
)

fun staticSessionCoopClient(
    context: Context,
    sessionId: String,
): CoopClient {
    val testAccounts = TestAccounts(context)
    return if (testAccounts.modeActive()) TestModeCoopClient(sessionId)
    else StaticSessionCoopClient(createConfig(), sessionId, createHttpClientFactory(context))
}

fun createCoopLogin(context: Context): CoopLogin {
    val testAccounts = TestAccounts(context)
    val coopLogin =
        if (testAccounts.modeActive()) TestModeCoopLogin()
        else RealCoopLogin(createConfig(), createHttpClientFactory(context))
    return MonitoredCoopLogin(
        context,
        UserProperties(context),
        coopLogin,
        RealFirebaseAnalytics(Firebase.analytics)
    )
}

fun createCredentialsStore(context: Context) = SharedPreferencesCredentialsStore(context)

fun createHttpClientFactory(context: Context) = { cookieJar: CookieJar ->
    MonitoredHttpClient(context, SimpleHttpClient(cookieJar))
}

fun createConfig(): Config = RemoteConfig()
