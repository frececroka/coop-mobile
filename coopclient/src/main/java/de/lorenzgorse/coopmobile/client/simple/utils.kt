package de.lorenzgorse.coopmobile.client.simple

import de.lorenzgorse.coopmobile.client.simple.CoopException.HtmlChanged
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
 * [IllegalStateException]s to [HtmlChanged]s. This is used for code that extracts data
 * from the DOM.
 */
fun <T> Document.safe(fn: Document.() -> T): T {
    return try {
        fn(this)
    } catch (e: NullPointerException) {
        throw HtmlChanged(e, this)
    } catch (e: IllegalStateException) {
        throw HtmlChanged(e, this)
    } catch (e: HtmlChanged) {
        throw e.copy(document = this)
    }
}
