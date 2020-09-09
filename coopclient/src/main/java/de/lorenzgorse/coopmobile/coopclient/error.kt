package de.lorenzgorse.coopmobile.coopclient

sealed class CoopException(cause: Throwable?) : Exception(cause) {
    class UnauthorizedException(val redirect: String?) : CoopException(null)
    class HtmlChangedException(cause: Throwable?) : CoopException(cause)
    class PlanUnsupported(val plan: String?) : CoopException(null)
}
