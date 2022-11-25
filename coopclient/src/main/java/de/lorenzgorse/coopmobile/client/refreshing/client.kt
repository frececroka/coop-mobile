package de.lorenzgorse.coopmobile.client.refreshing

import arrow.core.Either
import arrow.core.flatMap
import de.lorenzgorse.coopmobile.client.CoopError
import de.lorenzgorse.coopmobile.client.DecoratedCoopClient
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
        return loadWithRetry(null, loader)
    }

    private suspend fun <T> loadWithRetry(
        oldClient: CoopClient?,
        loader: suspend (client: CoopClient) -> Either<CoopError, T>,
    ): Either<CoopError, T> {
        val clientOrError =
            if (oldClient == null) coopClientFactory.get()
            else coopClientFactory.refresh(oldClient)
        return clientOrError
            .tapLeft { log.info("Failed to obtain client: $it") }
            .flatMap { client ->
                loader(client)
                    .tapLeft { log.info("Failed to load data: $it") }
                    .swap()
                    .flatMap { error ->
                        if (oldClient == null && error == CoopError.Unauthorized) {
                            loadWithRetry(client, loader).swap()
                        } else {
                            Either.Right(error)
                        }
                    }
                    .swap()
            }
    }

}
