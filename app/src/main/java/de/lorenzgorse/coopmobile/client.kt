package de.lorenzgorse.coopmobile

import android.content.Context
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import de.lorenzgorse.coopmobile.client.Config
import de.lorenzgorse.coopmobile.client.RemoteConfig
import de.lorenzgorse.coopmobile.client.refreshing.RealCoopClientFactory
import de.lorenzgorse.coopmobile.client.refreshing.RefreshingSessionCoopClient
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.client.simple.RealCoopLogin
import de.lorenzgorse.coopmobile.client.simple.SimpleHttpClient
import de.lorenzgorse.coopmobile.preferences.SharedPreferencesCredentialsStore
import okhttp3.CookieJar

fun createClient(context: Context): CoopClient =
    MonitoredCoopClient(RefreshingSessionCoopClient(createCoopClientFactory(context)))

fun createCoopClientFactory(context: Context) = RealCoopClientFactory(
    createConfig(),
    createCredentialsStore(context),
    createCoopLogin(context),
    createHttpClientFactory(context),
)

fun createCoopLogin(context: Context) =
    MonitoredCoopLogin(
        context,
        UserProperties(context),
        RealCoopLogin(createConfig(), createHttpClientFactory(context)),
        RealFirebaseAnalytics(Firebase.analytics)
    )

fun createCredentialsStore(context: Context) = SharedPreferencesCredentialsStore(context)

fun createHttpClientFactory(context: Context) = { cookieJar: CookieJar ->
    MonitoredHttpClient(context, SimpleHttpClient(cookieJar))
}

fun createConfig(): Config = RemoteConfig()
