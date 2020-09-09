package de.lorenzgorse.coopmobile.coopclient

import java.util.*

fun determineCountry(): String {
    return when (Locale.getDefault().language) {
        "de" -> "de"
        "it" -> "it"
        "fr" -> "fr"
        else -> "de"
    }
}

/**
 * Executes the given closure and translates all [NullPointerException]s and
 * [IllegalStateException]s to [HtmlChangedException]s. This is used for code that extracts data
 * from the DOM.
 */
fun <T> safeHtml(fn: () -> T): T {
    return try {
        fn()
    } catch (e: NullPointerException) {
        throw CoopException.HtmlChangedException(e)
    } catch (e: IllegalStateException) {
        throw CoopException.HtmlChangedException(e)
    }
}
