package de.lorenzgorse.coopmobile.client.refreshing

import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.DecoratedCoopClient
import de.lorenzgorse.coopmobile.client.Either
import de.lorenzgorse.coopmobile.client.simple.CoopClient
import org.slf4j.LoggerFactory

class RefreshingSessionCoopClient(
    private val coopClientFactory: CoopClientFactory
) : DecoratedCoopClient() {

    private val log = LoggerFactory.getLogger(RefreshingSessionCoopClient::class.java)

    override suspend fun <T> decorator(
        loader: suspend (client: CoopClient) -> Either<CoopError, T>,
        method: String?,
    ): Either<CoopError, T> {
        val client = when (val client = coopClientFactory.get()) {
            is Either.Left -> {
                log.info("Failed to obtain client: $client")
                return client
            }
            is Either.Right -> client.value
        }
        return when (val result = loader(client)) {
            is Either.Right -> result
            is Either.Left -> when (result.value) {
                is CoopError.Unauthorized -> {
                    log.info("Failed to load data, because session is expired")
                    val newClient = when (val newClient = coopClientFactory.refresh(client)) {
                        is Either.Left -> {
                            log.info("Failed to refresh client: $client")
                            return newClient
                        }
                        is Either.Right -> newClient.value
                    }
                    loader(newClient)
                }
                else -> result
            }
        }
    }

}
