package de.lorenzgorse.coopmobile.client.simple

import com.google.gson.JsonSyntaxException
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
    suspend fun getConsumptionGeneric(): Either<CoopError, List<UnitValue<Float>>>
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

    override suspend fun getProfile(): Either<CoopError, List<Pair<String, String>>> =
        translateExceptions {
            val html = getHtml(coopBaseAccount)
            html.safe {
                val profile = selectFirst("#block_my_profile")!!
                profile.select(".panel__list").map { parseProfileItem(it) }
            }
        }

    private fun parseProfileItem(item: Element): Pair<String, String> {
        val label =
            item.selectFirst(".panel__list__label")!!.textNodes().joinToString(" ") { it.text() }
                .trim()
        val value = item.select(".panel__list__item").joinToString(", ") { it.text() }.trim()
        return Pair(label, value)
    }

    override suspend fun getConsumption(): Either<CoopError, List<UnitValue<Float>>> =
        translateExceptions {
            val html = getHtml(coopBaseAccount)
            html.safe {
                val creditBalance = selectFirst("#credit_balance")?.let { block ->
                    parseUnitValueBlock(block.parent()!!, { it.toFloat() }) { it.replace(".–", "") }
                }?.let { listOf(it) }.orEmpty()
                val consumptions1 = select("#my_consumption .panel").map {
                    parseUnitValueBlock(it, { v -> v.toFloat() })
                }
                val consumptions2 = select("#my_consumption.panel").map {
                    parseUnitValueBlock(it, { v -> v.toFloat() })
                }
                creditBalance + consumptions1 + consumptions2
            }
        }

    // Tries to simplify getConsumption(), which may also help with supporting wireless users.
    override suspend fun getConsumptionGeneric(): Either<CoopError, List<UnitValue<Float>>> =
        translateExceptions {
            val html = getHtml(coopBaseAccount)
            html.safe {
                select(".contingent__data")
                    .map { parseUnitValueBlock(it, { v -> v.toFloat() }) }
            }
        }

    private fun <T> parseUnitValueBlock(
        block: Element,
        convert: (String) -> T,
        sanitize: (String) -> String = { it }
    ): UnitValue<T> {
        val title = block.selectFirst(".panel__title")?.text()
            ?: block.selectFirst(".contingent__data--legend")?.text()!!
        log.info("title = $title")

        val value = block.selectFirst(".contingent__data--value")!!.text()

        val valueParts = value.split(" ")
        log.info("valueParts.size = ${valueParts.size}")

        val candidates = buildList {
            when (valueParts.size) {
                1 -> add(Pair(valueParts[0], "-"))
                2 -> {
                    add(Pair(valueParts[0], valueParts[1]))
                    add(Pair(valueParts[1], valueParts[0]))
                }
            }
        }

        val (amount, unit) = candidates
            .mapNotNull { (textualAmount, unit) ->
                try {
                    Pair(convert(sanitize(textualAmount)), unit)
                } catch (e: NumberFormatException) {
                    null
                }
            }
            .firstOrNull()
            ?: throw CoopException.HtmlChanged()

        return UnitValue(title, amount, unit)
    }

    override suspend fun getConsumptionLog(): Either<CoopError, List<ConsumptionLogEntry>?> =
        translateExceptions {
            try {
                val consumption = getJson<RawConsumptionLog>(
                    "https://myaccount.coopmobile.ch/$country/ajax_load_cdr"
                )
                consumption.data.mapNotNull { parseConsumptionLogEntry(it) }
            } catch (e: JsonSyntaxException) {
                null
            }
        }

    private fun parseConsumptionLogEntry(
        rawConsumptionLogEntry: RawConsumptionLogEntry
    ): ConsumptionLogEntry? {
        if (!rawConsumptionLogEntry.isData) {
            return null
        }

        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMAN)
        val date = dateFormat.parse(rawConsumptionLogEntry.date)!!
        val type = rawConsumptionLogEntry.type
        val amount = rawConsumptionLogEntry.amount.replace("&#39;", "").toDouble()
        return ConsumptionLogEntry(date, type, amount)
    }

    override suspend fun getProducts(): Either<CoopError, List<Product>> = translateExceptions {
        val html = getHtml("$coopBaseAccount/add_product")
        html.safe { select(".add_product").map { parseProductBlock(it) } }
    }

    private fun parseProductBlock(productBlock: Element): Product {
        val content = productBlock.selectFirst(".modal-body")!!

        var name = ""
        var price = ""
        val descriptions = arrayListOf<String>()

        for (child in content.childNodes()) {
            when (child) {
                is TextNode -> descriptions.add(child.text())
                is Element ->
                    when {
                        child.hasClass("modal__content__info") ->
                            name = child.text()
                        child.hasClass("modal__content__price") ->
                            price = child.text()
                        else ->
                            descriptions.add(child.text())
                    }
            }
        }

        val description = descriptions
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n") {
                if (!it.endsWith(".") && !it.endsWith(":")) {
                    "$it."
                } else {
                    it
                }
            }

        val form = productBlock.selectFirst("form")!!
        val url = form.attr("action")
        val parameters = form.select("input").associate { Pair(it.attr("name"), it.attr("value")) }
        val buySpec = ProductBuySpec(url, parameters)

        return Product(name, description, price, buySpec)
    }

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
            val html = getHtml("$coopBaseAccount/my_correspondence")
            html.safe { select(".table--mail tbody tr").map { parseCorrespondenceRow(it) } }
        }

    private fun parseCorrespondenceRow(it: Element): CorrespondenceHeader {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
        val date = dateFormat.parse(it.selectFirst(".first")!!.text())!!
        val subject = it.selectFirst(".second")!!.text()
        val details = URL(coopScheme, coopHost, it.selectFirst("a")!!.attr("href"))
        return CorrespondenceHeader(date, subject, details)
    }

    override suspend fun augmentCorrespondence(header: CorrespondenceHeader): Either<CoopError, Correspondence> =
        translateExceptions {
            Correspondence(header, getCorrespondenceMessage(header.details))
        }

    private suspend fun getCorrespondenceMessage(url: URL): String {
        val html = getHtml(url.toString())
        return html.safe { selectFirst(".panel__print__content")!!.text() }
    }

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
