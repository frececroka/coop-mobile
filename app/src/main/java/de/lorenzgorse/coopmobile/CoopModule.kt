package de.lorenzgorse.coopmobile

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import de.lorenzgorse.coopmobile.coopclient.CoopLogin
import de.lorenzgorse.coopmobile.coopclient.RealCoopLogin
import de.lorenzgorse.coopmobile.data.CoopClientFactory
import de.lorenzgorse.coopmobile.data.RealCoopClientFactory
import de.lorenzgorse.coopmobile.ui.overview.FirstInstallTimeProvider
import de.lorenzgorse.coopmobile.ui.overview.RealFirstInstallTimeProvider

object CoopModule {
    var coopLogin: CoopLogin = RealCoopLogin()
    var coopClientFactory: CoopClientFactory = RealCoopClientFactory()
    var firstInstallTimeProvider: FirstInstallTimeProvider = RealFirstInstallTimeProvider()
    var firebaseAnalytics: (Context) -> FirebaseAnalytics = FirebaseAnalytics::getInstance
    var firebaseCrashlytics: () -> FirebaseCrashlytics = FirebaseCrashlytics::getInstance
}
