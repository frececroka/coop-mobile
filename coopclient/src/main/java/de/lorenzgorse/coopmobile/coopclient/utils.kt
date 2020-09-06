package de.lorenzgorse.coopmobile.coopclient

import de.lorenzgorse.coopmobile.coopclient.CoopException.HtmlChangedException
import org.jsoup.nodes.Document
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
fun <T> Document.safe(fn: Document.() -> T): T {
    return try {
        fn(this)
    } catch (e: NullPointerException) {
        throw HtmlChangedException(e, this)
    } catch (e: IllegalStateException) {
        throw HtmlChangedException(e, this)
    } catch (e: HtmlChangedException) {
        throw e.copy(document = this)
    }
}
