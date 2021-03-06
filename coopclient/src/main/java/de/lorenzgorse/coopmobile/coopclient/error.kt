package de.lorenzgorse.coopmobile.coopclient

import org.jsoup.nodes.Document

sealed class CoopException(cause: Throwable?) : Exception(cause) {
    data class Unauthorized(val redirect: String? = null) : CoopException(null)

    data class BadHtml(
        override val cause: Throwable,
        val html: String
    ) : java.lang.Exception(cause)

    data class HtmlChanged(
        override val cause: Throwable? = null,
        val document: Document? = null
    ) : CoopException(cause)

    data class PlanUnsupported(val plan: String?) : CoopException(null)
}
