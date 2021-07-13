package de.lorenzgorse.coopmobile.ui.overview

import android.content.Context

interface FirstInstallTimeProvider {
    fun get(context: Context): Long
}

class RealFirstInstallTimeProvider : FirstInstallTimeProvider {
    override fun get(context: Context) =
        context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
}

class StaticFirstInstallTimeProvider(
    private val firstInstallTime: Long
) : FirstInstallTimeProvider {
    override fun get(context: Context) = firstInstallTime
}
