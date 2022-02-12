package de.lorenzgorse.coopmobile.client

import de.lorenzgorse.coopmobile.client.simple.CoopException

sealed class CoopError : Exception() {
    object NoNetwork : CoopError()
    object NoClient : CoopError()
    object Unauthorized : CoopError()
    object FailedLogin : CoopError()
    class BadHtml(override val cause: CoopException.BadHtml) : CoopError()
    class HtmlChanged(override val cause: CoopException.HtmlChanged) : CoopError()
    object PlanUnsupported : CoopError()
    class Other(override val message: String) : CoopError()
}
