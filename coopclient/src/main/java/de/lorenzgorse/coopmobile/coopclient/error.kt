package de.lorenzgorse.coopmobile.coopclient

import org.jsoup.nodes.Document

sealed class CoopException(cause: Throwable?) : Exception(cause) {
    data class UnauthorizedException(val redirect: String?) : CoopException(null)

    data class HtmlChangedException(
        override val cause: Throwable? = null,
        val document: Document? = null
    ) : CoopException(cause)

    data class PlanUnsupported(val plan: String?) : CoopException(null)
}
