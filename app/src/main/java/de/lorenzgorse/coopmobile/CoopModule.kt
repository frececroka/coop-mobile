package de.lorenzgorse.coopmobile

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import de.lorenzgorse.coopmobile.coopclient.CoopLogin
import de.lorenzgorse.coopmobile.coopclient.RealCoopLogin

object CoopModule {
    var coopLogin: CoopLogin = RealCoopLogin()
    var coopClientFactory: CoopClientFactory = RealCoopClientFactory()
    var firstInstallTimeProvider: FirstInstallTimeProvider = RealFirstInstallTimeProvider()
    var firebaseAnalytics: (Context) -> FirebaseAnalytics = FirebaseAnalytics::getInstance
    var firebaseCrashlytics: () -> FirebaseCrashlytics = FirebaseCrashlytics::getInstance
}
