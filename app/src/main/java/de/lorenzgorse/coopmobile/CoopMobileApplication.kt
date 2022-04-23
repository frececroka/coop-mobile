package de.lorenzgorse.coopmobile;

import android.app.Application
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig

@Suppress("unused")
open class CoopMobileApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        UserProperties(this).restore()
        Firebase.remoteConfig.fetchAndActivate()
    }

}
