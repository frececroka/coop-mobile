package de.lorenzgorse.coopmobile.client

import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig

// TODO: write all values to analytics at some point
class RemoteConfig : Config() {
    override fun coopBase() =
        get("config.coopBase") ?: super.coopBase()
    override fun loginUrl() =
        get("config.loginUrl") ?: super.loginUrl()
    override fun loginUrlRegex() =
        get("config.loginUrlRegex") ?: super.loginUrlRegex()
    override fun loginSuccessRegex() =
        get("config.loginSuccessRegex") ?: super.loginSuccessRegex()
    override fun planRegex() =
        get("config.planRegex") ?: super.planRegex()
    override fun overviewUrl() =
        get("config.overviewUrl") ?: super.overviewUrl()
    override fun consumptionLogUrl() =
        get("config.consumptionLogUrl") ?: super.consumptionLogUrl()
    override fun productsUrl() =
        get("config.productsUrl") ?: super.productsUrl()
    override fun correspondencesUrl() =
        get("config.correspondencesUrl") ?: super.correspondencesUrl()

    private fun get(key: String): String? {
        val value = Firebase.remoteConfig.getValue(key)
        return when (value.source) {
            FirebaseRemoteConfig.VALUE_SOURCE_REMOTE -> value.asString()
            else -> null
        }
    }
}
