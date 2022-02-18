package de.lorenzgorse.coopmobile

import com.google.firebase.FirebaseApp

class TestApplication : CoopMobileApplication() {
    override fun onCreate() {
        FirebaseApp.initializeApp(this)
        super.onCreate()
    }
}
