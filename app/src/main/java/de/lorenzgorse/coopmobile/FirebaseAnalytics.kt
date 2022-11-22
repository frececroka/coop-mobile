package de.lorenzgorse.coopmobile

import android.os.Bundle
import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics.Event
import com.google.firebase.analytics.FirebaseAnalytics.Param

interface FirebaseAnalytics {
    fun logEvent(name: String, params: Bundle?)
    fun setScreen(name: String)
}

class RealFirebaseAnalytics(
    private val firebaseAnalytics: com.google.firebase.analytics.FirebaseAnalytics
) : FirebaseAnalytics {
    override fun logEvent(name: String, params: Bundle?) {
        firebaseAnalytics.logEvent(name, params)
    }

    override fun setScreen(name: String) {
        logEvent(Event.SCREEN_VIEW, bundleOf(Param.SCREEN_CLASS to name))
    }
}
