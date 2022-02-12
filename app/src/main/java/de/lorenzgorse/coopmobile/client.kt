package de.lorenzgorse.coopmobile

import android.content.Context
import de.lorenzgorse.coopmobile.client.refreshing.RealCoopClientFactory
import de.lorenzgorse.coopmobile.client.refreshing.RefreshingSessionCoopClient
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import de.lorenzgorse.coopmobile.client.simple.RealCoopLogin
import de.lorenzgorse.coopmobile.preferences.SharedPreferencesCredentialsStore

fun createClient(context: Context): CoopClient =
    MonitoredCoopClient(RefreshingSessionCoopClient(createCoopClientFactory(context)))

fun createCoopClientFactory(context: Context) = RealCoopClientFactory(
    createCredentialsStore(context),
    RealCoopLogin()
)

fun createCredentialsStore(context: Context) = SharedPreferencesCredentialsStore(context)
