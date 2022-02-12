package de.lorenzgorse.coopmobile

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import de.lorenzgorse.coopmobile.ui.overview.FirstInstallTimeProvider
import de.lorenzgorse.coopmobile.ui.overview.RealFirstInstallTimeProvider

object CoopModule {
    var firstInstallTimeProvider: FirstInstallTimeProvider = RealFirstInstallTimeProvider()
    var firebaseAnalytics: (Context) -> FirebaseAnalytics = FirebaseAnalytics::getInstance
    var firebaseCrashlytics: () -> FirebaseCrashlytics = FirebaseCrashlytics::getInstance
}
