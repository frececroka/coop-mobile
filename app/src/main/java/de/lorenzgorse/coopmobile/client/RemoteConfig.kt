package de.lorenzgorse.coopmobile.client

import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig

// TODO: write all values to analytics at some point
class RemoteConfig(private val base: Config) : Config {
    override fun coopBase() =
        get("config.coopBase") ?: base.coopBase()
    override fun loginUrl() =
        get("config.loginUrl") ?: base.loginUrl()
    override fun loginUrlRegex() =
        get("config.loginUrlRegex") ?: base.loginUrlRegex()
    override fun loginSuccessRegex() =
        get("config.loginSuccessRegex") ?: base.loginSuccessRegex()
    override fun planRegex() =
        get("config.planRegex") ?: base.planRegex()
    override fun overviewUrl() =
        get("config.overviewUrl") ?: base.overviewUrl()
    override fun consumptionLogUrl() =
        get("config.consumptionLogUrl") ?: base.consumptionLogUrl()
    override fun productsUrl() =
        get("config.productsUrl") ?: base.productsUrl()
    override fun correspondencesUrl() =
        get("config.correspondencesUrl") ?: base.correspondencesUrl()

    private fun get(key: String): String? {
        val value = Firebase.remoteConfig.getValue(key)
        return when (value.source) {
            FirebaseRemoteConfig.VALUE_SOURCE_REMOTE -> value.asString()
            else -> null
        }
    }
}
