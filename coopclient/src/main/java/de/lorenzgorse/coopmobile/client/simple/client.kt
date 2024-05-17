package de.lorenzgorse.coopmobile.client.simple

import arrow.core.Either
import com.google.gson.JsonSyntaxException
import de.lorenzgorse.coopmobile.CoopHtmlParser
import de.lorenzgorse.coopmobile.client.*
import okhttp3.FormBody
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URL

interface CoopClient {
    suspend fun getProfile(): Either<CoopError, List<Pair<String, String>>>
    suspend fun getProfileNoveau(): Either<CoopError, List<ProfileItem>>
    suspend fun getConsumption(): Either<CoopError, List<LabelledAmounts>>
    suspend fun getConsumptionLog(): Either<CoopError, List<ConsumptionLogEntry>?>
    suspend fun getProducts(): Either<CoopError, List<Product>>
    suspend fun buyProduct(buySpec: ProductBuySpec): Either<CoopError, Boolean>
    suspend fun getCorrespondences(): Either<CoopError, List<CorrespondenceHeader>>
    suspend fun augmentCorrespondence(header: CorrespondenceHeader): Either<CoopError, Correspondence>
}

class StaticSessionCoopClient(
    private val config: Config,
    parserExperiments: CoopHtmlParser.Experiments,
    sessionId: String,
    clientFactory: HttpClientFactory
) : CoopClient {

    private val log = LoggerFactory.getLogger(javaClass)
    private val client = clientFactory(StaticCookieJar(sessionId))
    private val parser = CoopHtmlParser(config, parserExperiments)

    override suspend fun getProfile(): Either<CoopError, List<Pair<String, String>>> =
        translateExceptions { getHtml(config.overviewUrl()).safe { parser.parseProfile(it) } }

    override suspend fun getProfileNoveau(): Either<CoopError, List<ProfileItem>> =
        getProfile().map { profileItems ->
            profileItems.map {
                val (description, value) = it
                ProfileItem(description, value)
            }
        }

    override suspend fun getConsumption(): Either<CoopError, List<LabelledAmounts>> =
        translateExceptions { getHtml(config.overviewUrl()).safe { parser.parseConsumption(it) } }

    override suspend fun getConsumptionLog(): Either<CoopError, List<ConsumptionLogEntry>?> =
        translateExceptions {
            try {
                val consumption = getJson<RawConsumptionLog>(config.consumptionLogUrl())
                parser.parseConsumptionLog(consumption)
            } catch (e: JsonSyntaxException) {
                null
            }
        }

    override suspend fun getProducts(): Either<CoopError, List<Product>> =
        translateExceptions { getHtml(config.productsUrl()).safe { parser.parseProducts(it) } }

    override suspend fun buyProduct(buySpec: ProductBuySpec): Either<CoopError, Boolean> =
        translateExceptions {
            log.info("Buying according to $buySpec")
            val body = buySpec.parameters.let {
                val builder = FormBody.Builder()
                for ((name, value) in it) {
                    builder.add(name, value)
                }
                builder.build()
            }
            val response = client.post(URL(URL(config.coopBase()), buySpec.url), body)
            log.info("Buy request completed with " +
                    "status code ${response.code} and " +
                    "URL ${response.request.url}")
            response.request.url.toString().endsWith("/my_consumption")
        }

    override suspend fun getCorrespondences(): Either<CoopError, List<CorrespondenceHeader>> =
        translateExceptions {
            getHtml(config.correspondencesUrl()).safe {
                parser.parseCorrespondences(it)
            }
        }

    override suspend fun augmentCorrespondence(header: CorrespondenceHeader): Either<CoopError, Correspondence> =
        translateExceptions {
            Correspondence(header, getCorrespondenceMessage(header.details))
        }

    private suspend fun getCorrespondenceMessage(url: URL) =
        getHtml(url.toString()).safe { parser.getCorrespondenceMessage(it) }

    private suspend fun getHtml(url: String) = client.getHtml(url, ::assertResponseSuccessful)
    private suspend inline fun <reified T> getJson(url: String): T =
        client.getJson(url, T::class.java, ::assertResponseSuccessful)

    private val signInRegex = Regex(config.loginUrlRegex())
    private val planRegex = Regex(config.planRegex())

    fun assertResponseSuccessful(response: Response) {
        val location = response.request.url.toString()
        if (signInRegex.matches(location)) {
            throw CoopException.Unauthorized(location)
        } else {
            val matchResult = planRegex.matchEntire(location)
            if (matchResult != null) {
                val plan = matchResult.groups[1]?.value
                if (!listOf("prepaid", "wireless").contains(plan)) {
                    throw CoopException.PlanUnsupported(plan)
                }
            }
        }
    }
}

suspend fun <T> translateExceptions(block: suspend () -> T): Either<CoopError, T> = try {
    Either.Right(block())
} catch (e: IOException) {
    Either.Left(CoopError.NoNetwork)
} catch (e: CoopException) {
    Either.Left(
        when (e) {
            is CoopException.Unauthorized -> CoopError.Unauthorized
            is CoopException.PlanUnsupported -> CoopError.PlanUnsupported
            is CoopException.BadHtml -> CoopError.BadHtml(e)
            is CoopException.HtmlChanged -> CoopError.HtmlChanged(e)
        }
    )
}
