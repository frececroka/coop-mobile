package de.lorenzgorse.coopmobile

import de.lorenzgorse.coopmobile.ui.overview.FirstInstallTimeProvider
import de.lorenzgorse.coopmobile.ui.overview.RealFirstInstallTimeProvider

object CoopModule {
    var firstInstallTimeProvider: FirstInstallTimeProvider = RealFirstInstallTimeProvider()
}
