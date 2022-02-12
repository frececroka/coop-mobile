package de.lorenzgorse.coopmobile.backend

import de.lorenzgorse.coopmobile.coopclient.*
import java.io.IOException

@Suppress("BlockingMethodInNonBlockingContext")
class CoopClientProxy(private val coopClientFactory: CoopClientFactory) {

    suspend fun getProfile(): Either<CoopError, List<Pair<String, String>>> =
        loadData { it.getProfile() }

    suspend fun getConsumption(): Either<CoopError, List<UnitValue<Float>>> =
        loadData { it.getConsumption() }

    suspend fun getConsumptionLog(): Either<CoopError, List<ConsumptionLogEntry>?> =
        loadData { it.getConsumptionLog() }

    suspend fun getProducts(): Either<CoopError, List<Product>> = loadData { it.getProducts() }

    suspend fun buyProduct(buySpec: ProductBuySpec): Either<CoopError, Boolean> =
        loadData { it.buyProduct(buySpec) }

    suspend fun getCorrespondeces(): Either<CoopError, List<CorrespondenceHeader>> =
        loadData { it.getCorrespondeces() }

    suspend fun augmentCorrespondence(header: CorrespondenceHeader): Either<CoopError, Correspondence> =
        loadData { it.augmentCorrespondence(header) }

    private suspend fun <T> loadData(loader: suspend (client: CoopClient) -> T): Either<CoopError, T> {
        return try {
            val client = coopClientFactory.get()
            if (client == null) {
                Either.Left(CoopError.NoClient)
            } else {
                try {
                    Either.Right(loader(client))
                } catch (e: CoopException.Unauthorized) {
                    val newClient = coopClientFactory.refresh(true)
                    if (newClient != null) {
                        try {
                            Either.Right(loader(newClient))
                        } catch (e: CoopException.Unauthorized) {
                            Either.Left(CoopError.Unauthorized)
                        }
                    } else {
                        Either.Left(CoopError.FailedLogin)
                    }
                }
            }
        } catch (e: IOException) {
            Either.Left(CoopError.NoNetwork)
        } catch (e: CoopException.PlanUnsupported) {
            Either.Left(CoopError.PlanUnsupported)
        } catch (e: CoopException.BadHtml) {
            Either.Left(CoopError.BadHtml(e))
        } catch (e: CoopException.HtmlChanged) {
            Either.Left(CoopError.HtmlChanged(e))
        }
    }

}
