package de.lorenzgorse.coopmobile

import android.content.Context
import de.lorenzgorse.coopmobile.backend.CoopClientProxy
import de.lorenzgorse.coopmobile.backend.RealCoopClientFactory
import de.lorenzgorse.coopmobile.coopclient.RealCoopLogin
import de.lorenzgorse.coopmobile.preferences.SharedPreferencesCredentialsStore

fun createClient(context: Context) = CoopClientProxy(createCoopClientFactory(context))

fun createCoopClientFactory(context: Context) = RealCoopClientFactory(
    SharedPreferencesCredentialsStore(context),
    RealCoopLogin()
)
