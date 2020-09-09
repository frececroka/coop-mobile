package de.lorenzgorse.coopmobile.coopclient

import com.google.gson.JsonSyntaxException
import de.lorenzgorse.coopmobile.coopclient.CoopException.*
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

    @Throws(IOException::class, CoopException::class)
    suspend fun getData(): CoopData

    @Throws(IOException::class, CoopException::class)
    suspend fun getConsumptionLog(): List<ConsumptionLogEntry>?

    @Throws(IOException::class, CoopException::class)
    suspend fun getProducts(): List<Product>

    @Throws(IOException::class, CoopException::class)
    suspend fun buyProduct(buySpec: ProductBuySpec): Boolean

    @Throws(IOException::class, CoopException::class)
    suspend fun getCorrespondeces(): List<CorrespondenceHeader>

    @Throws(IOException::class, CoopException::class)
    suspend fun augmentCorrespondence(header: CorrespondenceHeader): Correspondence

    fun sessionId(): String

}

class RealCoopClient(private val sessionId: String) : CoopClient {

    private val log = LoggerFactory.getLogger(javaClass)

    private var client = HttpClient(StaticCookieJar(sessionId))

    override suspend fun getData(): CoopData {
        val html = getHtml(coopBaseAccount)
        return safeHtml {
            val creditBalance = html.selectFirst("#credit_balance")?.let { block ->
                parseUnitValueBlock(block.parent(), { it.toFloat() }) { it.replace(".–", "") }
            }?.let { listOf(it) }.orEmpty()
            val consumptions1 = html.select("#my_consumption .panel").map {
                parseUnitValueBlock(it, { v -> v.toFloat() })
            }
            val consumptions2 = html.select("#my_consumption.panel").map {
                parseUnitValueBlock(it, { v -> v.toFloat() })
            }
            CoopData(creditBalance + consumptions1 + consumptions2)
        }
    }

    private fun <T> parseUnitValueBlock(
        block: Element,
        convert: (String) -> T,
        sanitize: (String) -> String = { it }
    ): UnitValue<T> {
        val title = block.selectFirst(".panel__title").text()
        val value = block.selectFirst(".panel__consumption__data--value").text()

        val valueParts = value.split(" ")
        if (valueParts.size != 2) {
            throw HtmlChangedException(null)
        }

        val (amount, unit) = try {
            Pair(convert(sanitize(valueParts[0])), valueParts[1])
        } catch (e: NumberFormatException) {
            try {
                Pair(convert(sanitize(valueParts[1])), valueParts[0])
            } catch (e: NumberFormatException) {
                throw HtmlChangedException(null)
            }
        }

        return UnitValue(title, amount, unit)
    }

    override suspend fun getConsumptionLog(): List<ConsumptionLogEntry>? {
        return try {
            val consumption = getJson<RawConsumptionLog>(
                "https://myaccount.coopmobile.ch/$country/ajax_load_cdr")
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

    override suspend fun getProducts(): List<Product> {
        val html = getHtml("$coopBaseAccount/add_product")
        return safeHtml { html.select(".add_product").map { parseProductBlock(it) } }
    }

    private fun parseProductBlock(productBlock: Element): Product {
        val content = productBlock.selectFirst(".modal-body")

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

        val form = productBlock.selectFirst("form")
        val url = form.attr("action")
        val parameters = form.select("input")
            .map { Pair(it.attr("name"), it.attr("value")) }
            .toMap()
        val buySpec = ProductBuySpec(url, parameters)

        return Product(name, description, price, buySpec)
    }

    override suspend fun buyProduct(buySpec: ProductBuySpec): Boolean {
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
        return response.isRedirect || response.isSuccessful
    }

    override suspend fun getCorrespondeces(): List<CorrespondenceHeader> {
        val html = getHtml("$coopBaseAccount/my_correspondence/index?limit=30")
        return safeHtml { html.select(".table--mail tbody tr").map { parseCorrespondenceRow(it) } }
    }

    private fun parseCorrespondenceRow(it: Element): CorrespondenceHeader {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
        val date = dateFormat.parse(it.selectFirst(".first").text())!!
        val subject = it.selectFirst(".second").text()
        val details = URL(coopScheme, coopHost, it.selectFirst("a").attr("href"))
        return CorrespondenceHeader(date, subject, details)
    }

    override suspend fun augmentCorrespondence(header: CorrespondenceHeader): Correspondence {
        return Correspondence(header, getCorrespondenceMessage(header.details))
    }

    private suspend fun getCorrespondenceMessage(url: URL): String {
        val html = getHtml(url.toString())
        return safeHtml { html.selectFirst(".panel__print__content").text() }
    }

    override fun sessionId() = sessionId

    private suspend fun getHtml(url: String) = client.getHtml(url, ::assertResponseSuccessful)
    private suspend inline fun <reified T> getJson(url: String): T =
        client.getJson(url, ::assertResponseSuccessful)

    companion object {

        private val signInRegex =
            Regex("https://myaccount\\.coopmobile\\.ch/eCare/([^/]+)/users/sign_in")
        private val planRegex =
            Regex("https://myaccount\\.coopmobile\\.ch/eCare/([^/]+)/.+")

        fun assertResponseSuccessful(response: Response) {
            val location = response.request.url.toString()
            if (signInRegex.matches(location)) {
                throw UnauthorizedException(location)
            } else {
                val matchResult = planRegex.matchEntire(location)
                if (matchResult != null) {
                    val plan = matchResult.groups[1]?.value
                    if (!listOf("prepaid", "wireless").contains(plan) ) {
                        throw PlanUnsupported(plan)
                    }
                }
            }
        }

    }

}
