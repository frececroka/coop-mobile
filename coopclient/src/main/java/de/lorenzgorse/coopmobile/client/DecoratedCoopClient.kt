package de.lorenzgorse.coopmobile.client

import de.lorenzgorse.coopmobile.client.simple.CoopClient

abstract class DecoratedCoopClient : CoopClient {

    override suspend fun getProfile(): Either<CoopError, List<Pair<String, String>>> =
        decorator({ it.getProfile() }, "getProfile")

    override suspend fun getConsumption(): Either<CoopError, List<UnitValue<Float>>> =
        decorator({ it.getConsumption() }, "getConsumption")

    override suspend fun getConsumptionGeneric(): Either<CoopError, List<UnitValueBlock>> =
        decorator({ it.getConsumptionGeneric() }, "getConsumptionGeneric")

    override suspend fun getConsumptionLog(): Either<CoopError, List<ConsumptionLogEntry>?> =
        decorator({ it.getConsumptionLog() }, "getConsumptionLog")

    override suspend fun getProducts(): Either<CoopError, List<Product>> =
        decorator({ it.getProducts() }, "getProducts")

    override suspend fun buyProduct(buySpec: ProductBuySpec): Either<CoopError, Boolean> =
        decorator({ it.buyProduct(buySpec) }, "buyProduct")

    override suspend fun getCorrespondeces(): Either<CoopError, List<CorrespondenceHeader>> =
        decorator({ it.getCorrespondeces() }, "getCorrespondeces")

    override suspend fun augmentCorrespondence(header: CorrespondenceHeader): Either<CoopError, Correspondence> =
        decorator({ it.augmentCorrespondence(header) }, "augmentCorrespondence")

    abstract suspend fun <T> decorator(
        loader: suspend (client: CoopClient) -> Either<CoopError, T>,
        method: String? = null
    ): Either<CoopError, T>

}
