package de.lorenzgorse.coopmobile.client

import de.lorenzgorse.coopmobile.client.simple.CoopException

sealed class CoopError {
    object NoNetwork : CoopError()
    object NoClient: CoopError()
    object Unauthorized : CoopError()
    object FailedLogin : CoopError()
    data class BadHtml(val ex: CoopException.BadHtml) : CoopError()
    data class HtmlChanged(val ex: CoopException.HtmlChanged) : CoopError()
    object PlanUnsupported : CoopError()
    data class Other(val message: String) : CoopError()
}

sealed class Either<out L, out R> {
    data class Left<L>(val value: L): Either<L, Nothing>()
    data class Right<R>(val value: R): Either<Nothing, R>()
    fun right(): R? = when (this) {
        is Right -> value
        else -> null
    }
}
