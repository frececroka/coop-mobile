package de.lorenzgorse.coopmobile.client

sealed class Either<out L, out R> {
    data class Left<L>(val value: L) : Either<L, Nothing>()
    data class Right<R>(val value: R) : Either<Nothing, R>()

    fun right(): R? = when (this) {
        is Right -> value
        else -> null
    }
}
