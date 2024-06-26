package de.lorenzgorse.coopmobile.client

import arrow.core.Either
import de.lorenzgorse.coopmobile.client.simple.CoopClient

abstract class DecoratedCoopClient : CoopClient {

    override suspend fun getProfile(): Either<CoopError, List<ProfileItem>> =
        decorator({ it.getProfile() }, "getProfile")

    override suspend fun getConsumption(): Either<CoopError, List<LabelledAmounts>> =
        decorator({ it.getConsumption() }, "getConsumption")

    override suspend fun getConsumptionLog(): Either<CoopError, List<ConsumptionLogEntry>?> =
        decorator({ it.getConsumptionLog() }, "getConsumptionLog")

    override suspend fun getProducts(): Either<CoopError, List<Product>> =
        decorator({ it.getProducts() }, "getProducts")

    override suspend fun buyProduct(buySpec: ProductBuySpec): Either<CoopError, Boolean> =
        decorator({ it.buyProduct(buySpec) }, "buyProduct")

    override suspend fun getCorrespondences(): Either<CoopError, List<CorrespondenceHeader>> =
        decorator({ it.getCorrespondences() }, "getCorrespondeces")

    override suspend fun augmentCorrespondence(header: CorrespondenceHeader): Either<CoopError, Correspondence> =
        decorator({ it.augmentCorrespondence(header) }, "augmentCorrespondence")

    abstract suspend fun <T> decorator(
        loader: suspend (client: CoopClient) -> Either<CoopError, T>,
        method: String? = null
    ): Either<CoopError, T>

}
