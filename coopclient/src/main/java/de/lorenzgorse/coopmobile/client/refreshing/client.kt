package de.lorenzgorse.coopmobile.client.refreshing

import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.DecoratedCoopClient
import de.lorenzgorse.coopmobile.client.Either
import de.lorenzgorse.coopmobile.client.simple.CoopClient

class RefreshingSessionCoopClient(
    private val coopClientFactory: CoopClientFactory
) : DecoratedCoopClient() {

    override suspend fun <T> decorator(
        loader: suspend (client: CoopClient) -> Either<CoopError, T>,
        method: String?,
    ): Either<CoopError, T> {
        val client = when (val client = coopClientFactory.get()) {
            is Either.Left -> return client
            is Either.Right -> client.value
        }
        return when (val result = loader(client)) {
            is Either.Right -> result
            is Either.Left -> when (result.value) {
                is CoopError.Unauthorized -> {
                    val newClient = when (val newClient = coopClientFactory.refresh(client)) {
                        is Either.Left -> return newClient
                        is Either.Right -> newClient.value
                    }
                    loader(newClient)
                }
                else -> result
            }
        }
    }

}
