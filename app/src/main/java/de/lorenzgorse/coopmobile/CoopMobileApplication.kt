package de.lorenzgorse.coopmobile;

import android.app.Application

@Suppress("unused")
open class CoopMobileApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        UserProperties(this).restore()
    }

}
