package de.lorenzgorse.coopmobile

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import com.google.firebase.analytics.FirebaseAnalytics
import java.io.File

fun createAnalytics(ctx: Context): FirebaseAnalytics {
    val analytics = FirebaseAnalytics.getInstance(ctx)
    val testLabSetting = Settings.System.getString(ctx.contentResolver, "firebase.test.lab")
    if ("true" == testLabSetting) {
        analytics.setAnalyticsCollectionEnabled(false)
    }
    return analytics
}

fun FirebaseAnalytics.logEventOnce(ctx: Context, name: String, params: Bundle?) {
    if (!File(ctx.filesDir, name).exists()) {
        logEvent(name, params)
        File(ctx.filesDir, name).writeText(System.currentTimeMillis().toString())
    }
}
