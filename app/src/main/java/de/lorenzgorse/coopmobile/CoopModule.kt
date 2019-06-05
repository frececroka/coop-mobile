package de.lorenzgorse.coopmobile

object CoopModule {
    var coopLogin: CoopLogin = RealCoopLogin()
    var coopClientFactory: CoopClientFactory = RealCoopClientFactory()
    var firstInstallTimeProvider: FirstInstallTimeProvider = RealFirstInstallTimeProvider()
}
