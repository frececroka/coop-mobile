package de.lorenzgorse.coopmobile

import android.content.Context

interface FirstInstallTimeProvider {
    fun get(context: Context): Long
}

class RealFirstInstallTimeProvider : FirstInstallTimeProvider {
    override fun get(context: Context): Long {
        return context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    }
}

class StaticFirstInstallTimeProvider(private val firstInstallTime: Long) : FirstInstallTimeProvider {
    override fun get(context: Context): Long {
        return firstInstallTime
    }
}
