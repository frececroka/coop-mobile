package de.lorenzgorse.coopmobile

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics

object CoopModule {
    var coopLogin: CoopLogin = RealCoopLogin()
    var coopClientFactory: CoopClientFactory = RealCoopClientFactory()
    var firstInstallTimeProvider: FirstInstallTimeProvider = RealFirstInstallTimeProvider()
    var firebaseAnalyticsFactory: (Context) -> FirebaseAnalytics = FirebaseAnalytics::getInstance
}
