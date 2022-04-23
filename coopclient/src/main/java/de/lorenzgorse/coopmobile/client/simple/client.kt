package de.lorenzgorse.coopmobile.client.simple

import com.google.gson.JsonSyntaxException
import de.lorenzgorse.coopmobile.CoopHtmlParser
import de.lorenzgorse.coopmobile.client.*
import okhttp3.FormBody
import okhttp3.Response
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

interface CoopClient {
    suspend fun getProfile(): Either<CoopError, List<Pair<String, String>>>
    suspend fun getConsumption(): Either<CoopError, List<UnitValue<Float>>>
    suspend fun getConsumptionGeneric(): Either<CoopError, List<UnitValueBlock>>
    suspend fun getConsumptionLog(): Either<CoopError, List<ConsumptionLogEntry>?>
    suspend fun getProducts(): Either<CoopError, List<Product>>
    suspend fun buyProduct(buySpec: ProductBuySpec): Either<CoopError, Boolean>
    suspend fun getCorrespondeces(): Either<CoopError, List<CorrespondenceHeader>>
    suspend fun augmentCorrespondence(header: CorrespondenceHeader): Either<CoopError, Correspondence>
    suspend fun sessionId(): String?
}

class StaticSessionCoopClient(
    private val sessionId: String,
    clientFactory: HttpClientFactory
) : CoopClient {

    private val log = LoggerFactory.getLogger(javaClass)
    private val client = clientFactory(StaticCookieJar(sessionId))
    private val parser = CoopHtmlParser()

    override suspend fun getProfile(): Either<CoopError, List<Pair<String, String>>> =
        translateExceptions { getHtml(coopBaseAccount).safe { parser.getProfile(it) } }

    override suspend fun getConsumption(): Either<CoopError, List<UnitValue<Float>>> =
        translateExceptions { getHtml(coopBaseAccount).safe { parser.getConsumption(it) } }

    // Tries to simplify getConsumption(), which may also help with supporting wireless users.
    override suspend fun getConsumptionGeneric(): Either<CoopError, List<UnitValueBlock>> =
        translateExceptions { getHtml(coopBaseAccount).safe { parser.getConsumptionGeneric(it) } }

    override suspend fun getConsumptionLog(): Either<CoopError, List<ConsumptionLogEntry>?> =
        translateExceptions {
            try {
                val consumption = getJson<RawConsumptionLog>(
                    "https://myaccount.coopmobile.ch/$country/ajax_load_cdr"
                )
                parser.parseConsumptionLog(consumption)
            } catch (e: JsonSyntaxException) {
                null
            }
        }

    override suspend fun getProducts(): Either<CoopError, List<Product>> =
        translateExceptions { getHtml("$coopBaseAccount/add_product").safe { parser.getProducts(it) } }

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
            val response = client.post(URL(coopScheme, coopHost, buySpec.url), body)
            log.info("Buy request completed with status code: ${response.code}")
            response.isRedirect || response.isSuccessful
        }

    override suspend fun getCorrespondeces(): Either<CoopError, List<CorrespondenceHeader>> =
        translateExceptions {
            getHtml("$coopBaseAccount/my_correspondence").safe {
                parser.getCorrespondeces(it)
            }
        }

    override suspend fun augmentCorrespondence(header: CorrespondenceHeader): Either<CoopError, Correspondence> =
        translateExceptions {
            Correspondence(header, getCorrespondenceMessage(header.details))
        }

    private suspend fun getCorrespondenceMessage(url: URL) =
        getHtml(url.toString()).safe { parser.getCorrespondenceMessage(it) }

    override suspend fun sessionId() = sessionId

    private suspend fun getHtml(url: String) = client.getHtml(url, ::assertResponseSuccessful)
    private suspend inline fun <reified T> getJson(url: String): T =
        client.getJson(url, T::class.java, ::assertResponseSuccessful)

    companion object {

        private val signInRegex =
            Regex("https://myaccount\\.coopmobile\\.ch/eCare/([^/]+)/users/sign_in")
        private val planRegex =
            Regex("https://myaccount\\.coopmobile\\.ch/eCare/([^/]+)/.+")

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
