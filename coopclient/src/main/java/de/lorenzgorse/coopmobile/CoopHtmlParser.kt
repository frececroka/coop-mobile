package de.lorenzgorse.coopmobile

import de.lorenzgorse.coopmobile.client.*
import de.lorenzgorse.coopmobile.client.simple.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.slf4j.LoggerFactory
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class CoopHtmlParser {

    private val log = LoggerFactory.getLogger(javaClass)

    fun getProfile(html: Document): List<Pair<String, String>> =
        html.select("#block_my_profile .panel__list")
            .map { parseProfileItem(it) }

    private fun parseProfileItem(item: Element): Pair<String, String> {
        val label =
            item.selectFirst(".panel__list__label")!!.textNodes().joinToString(" ") { it.text() }
                .trim()
        val value = item.select(".panel__list__item").joinToString(", ") { it.text() }.trim()
        return Pair(label, value)
    }

    fun getConsumption(html: Document): List<UnitValue<Float>> {
        val creditBalance = html.selectFirst("#credit_balance")?.let { block ->
            parseUnitValueBlock(block.parent()!!, { it.toFloat() }) { it.replace(".–", "") }
        }?.let { listOf(it) }.orEmpty()
        val consumptions1 = html.select("#my_consumption .panel").map {
            parseUnitValueBlock(it, { v -> v.toFloat() })
        }
        val consumptions2 = html.select("#my_consumption.panel").map {
            parseUnitValueBlock(it, { v -> v.toFloat() })
        }
        return creditBalance + consumptions1 + consumptions2
    }

    // Tries to simplify getConsumption(), which may also help with supporting wireless users.
    fun getConsumptionGeneric(html: Document): List<UnitValue<Float>> =
        html.select(".contingent__data")
            .map { parseUnitValueBlock(it, { v -> v.toFloat() }) }

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
                    Pair(convert(sanitize(textualAmount.replace(",", "."))), unit)
                } catch (e: NumberFormatException) {
                    null
                }
            }
            .firstOrNull()
            ?: throw CoopException.HtmlChanged()

        return UnitValue(title, amount, unit)
    }

    fun parseConsumptionLog(consumption: RawConsumptionLog): List<ConsumptionLogEntry> =
        consumption.data.mapNotNull { parseConsumptionLogEntry(it) }

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

    fun getProducts(html: Document): List<Product> =
        html.select(".add_product").map { parseProductBlock(it) }

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

    fun getCorrespondeces(html: Document): List<CorrespondenceHeader> =
        html.select(".table--mail tbody tr").map { parseCorrespondenceRow(it) }

    private fun parseCorrespondenceRow(it: Element): CorrespondenceHeader {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
        val date = dateFormat.parse(it.selectFirst(".first")!!.text())!!
        val subject = it.selectFirst(".second")!!.text()
        val details = URL(coopScheme, coopHost, it.selectFirst("a")!!.attr("href"))
        return CorrespondenceHeader(date, subject, details)
    }

    fun getCorrespondenceMessage(html: Document): String =
        html.selectFirst(".panel__print__content")!!.text()

}
