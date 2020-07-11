package de.lorenzgorse.coopmobile

import android.content.Context
import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics

class OpenSource(private val context: Context) {

    private val analytics = FirebaseAnalytics.getInstance(context)

    suspend fun launch() {
        analytics.logEvent("view_open_source", bundleOf())
        val result = AlertDialogBuilder(context)
            .setTitle(R.string.title_open_source)
            .setMessage(R.string.open_source)
            .setPositiveButton(R.string.github)
            .setNeutralButton(R.string.dismiss)
            .show()
        if (result == AlertDialogChoice.POSITIVE) {
            analytics.logEvent("visit_github", bundleOf())
            context.openUri("https://github.com/frececroka/coop-mobile")
        }
    }

}
