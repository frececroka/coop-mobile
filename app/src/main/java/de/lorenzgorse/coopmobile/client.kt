package de.lorenzgorse.coopmobile

import android.content.Context
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import de.lorenzgorse.coopmobile.client.refreshing.RealCoopClientFactory
import de.lorenzgorse.coopmobile.client.refreshing.RefreshingSessionCoopClient
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.client.simple.RealCoopLogin
import de.lorenzgorse.coopmobile.preferences.SharedPreferencesCredentialsStore

fun createClient(context: Context): CoopClient =
    MonitoredCoopClient(RefreshingSessionCoopClient(createCoopClientFactory(context)))

fun createCoopClientFactory(context: Context) = RealCoopClientFactory(
    createCredentialsStore(context),
    createCoopLogin(context)
)

fun createCoopLogin(context: Context) =
    MonitoredCoopLogin(
        context,
        UserProperties(context),
        RealCoopLogin(),
        RealFirebaseAnalytics(Firebase.analytics)
    )

fun createCredentialsStore(context: Context) = SharedPreferencesCredentialsStore(context)
