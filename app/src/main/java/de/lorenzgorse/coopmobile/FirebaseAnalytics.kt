package de.lorenzgorse.coopmobile

import android.os.Bundle

interface FirebaseAnalytics {
    fun logEvent(name: String, params: Bundle?)
}

class RealFirebaseAnalytics(
    private val firebaseAnalytics: com.google.firebase.analytics.FirebaseAnalytics
) : FirebaseAnalytics {
    override fun logEvent(name: String, params: Bundle?) {
        firebaseAnalytics.logEvent(name, params)
    }
}
