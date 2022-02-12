package de.lorenzgorse.coopmobile.client.refreshing

import de.lorenzgorse.coopmobile.client.*
import de.lorenzgorse.coopmobile.client.simple.CoopClient

class RefreshingSessionCoopClient(private val coopClientFactory: CoopClientFactory) : CoopClient {

    override suspend fun getProfile(): Either<CoopError, List<Pair<String, String>>> =
        loadData { it.getProfile() }

    override suspend fun getConsumption(): Either<CoopError, List<UnitValue<Float>>> =
        loadData { it.getConsumption() }

    override suspend fun getConsumptionLog(): Either<CoopError, List<ConsumptionLogEntry>?> =
        loadData { it.getConsumptionLog() }

    override suspend fun getProducts(): Either<CoopError, List<Product>> =
        loadData { it.getProducts() }

    override suspend fun buyProduct(buySpec: ProductBuySpec): Either<CoopError, Boolean> =
        loadData { it.buyProduct(buySpec) }

    override suspend fun getCorrespondeces(): Either<CoopError, List<CorrespondenceHeader>> =
        loadData { it.getCorrespondeces() }

    override suspend fun augmentCorrespondence(header: CorrespondenceHeader): Either<CoopError, Correspondence> =
        loadData { it.augmentCorrespondence(header) }

    override suspend fun sessionId(): String? {
        return coopClientFactory.get()?.sessionId()
    }

    private suspend fun <T> loadData(loader: suspend (client: CoopClient) -> Either<CoopError, T>): Either<CoopError, T> {
        val client = coopClientFactory.get() ?: return Either.Left(CoopError.NoClient)
        return when (val result = loader(client)) {
            is Either.Right -> result
            is Either.Left -> when (result.value) {
                is CoopError.Unauthorized -> {
                    val newClient = coopClientFactory.refresh(true)
                        ?: return Either.Left(CoopError.FailedLogin)
                    loader(newClient)
                }
                else -> result
            }
        }
    }

}
