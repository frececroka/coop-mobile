package de.lorenzgorse.coopmobile.client.refreshing

import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.DecoratedCoopClient
import de.lorenzgorse.coopmobile.client.Either
import de.lorenzgorse.coopmobile.client.simple.CoopClient

class RefreshingSessionCoopClient(
    private val coopClientFactory: CoopClientFactory
) : DecoratedCoopClient() {

    override suspend fun sessionId(): String? {
        return coopClientFactory.get()?.sessionId()
    }

    override suspend fun <T> decorator(
        loader: suspend (client: CoopClient) -> Either<CoopError, T>,
        method: String?,
    ): Either<CoopError, T> {
        val client = coopClientFactory.get() ?: return Either.Left(CoopError.NoClient)
        return when (val result = loader(client)) {
            is Either.Right -> result
            is Either.Left -> when (result.value) {
                is CoopError.Unauthorized -> {
                    val newClient = coopClientFactory.refresh(client)
                        ?: return Either.Left(CoopError.FailedLogin)
                    loader(newClient)
                }
                else -> result
            }
        }
    }

}
