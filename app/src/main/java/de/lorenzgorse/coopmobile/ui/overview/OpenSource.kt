package de.lorenzgorse.coopmobile.ui.overview

import android.content.Context
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import de.lorenzgorse.coopmobile.FirebaseAnalytics
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.openUri
import de.lorenzgorse.coopmobile.trim
import de.lorenzgorse.coopmobile.ui.AlertDialogBuilder
import de.lorenzgorse.coopmobile.ui.AlertDialogChoice
import javax.inject.Inject

class OpenSource @Inject constructor(
    private val context: Context,
    private val analytics: FirebaseAnalytics,
) {

    suspend fun launch() {
        analytics.logEvent("view_open_source", bundleOf())
        val rawContent = context.getString(R.string.open_source)
        val htmlContent = HtmlCompat.fromHtml(rawContent, HtmlCompat.FROM_HTML_MODE_LEGACY)
        htmlContent.trim()
        val result = AlertDialogBuilder(context)
            .setTitle(R.string.title_open_source)
            .setHtml(htmlContent)
            .setPositiveButton(R.string.github)
            .setNeutralButton(R.string.dismiss)
            .show()
        if (result == AlertDialogChoice.POSITIVE) {
            analytics.logEvent("visit_github", bundleOf())
            context.openUri("https://github.com/frececroka/coop-mobile")
        }
    }

}
