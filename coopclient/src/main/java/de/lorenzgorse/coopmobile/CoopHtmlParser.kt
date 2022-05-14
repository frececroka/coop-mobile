package de.lorenzgorse.coopmobile

import de.lorenzgorse.coopmobile.client.*
import de.lorenzgorse.coopmobile.client.simple.CoopException
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.slf4j.LoggerFactory
import java.net.URL
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

class CoopHtmlParser(private val config: Config) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun parseProfile(html: Document): List<Pair<String, String>> =
        html.select("#block_my_profile .panel__list")
            .map { parseProfileItem(it) }

    private fun parseProfileItem(item: Element): Pair<String, String> {
        val label =
            item.selectFirst(".panel__list__label")!!.textNodes().joinToString(" ") { it.text() }
                .trim()
        val value = item.select(".panel__list__item").joinToString(", ") { it.text() }.trim()
        return Pair(label, value)
    }

    // Tries to simplify parseConsumption(), which may also help with supporting wireless users.
    fun parseConsumption(html: Document): List<LabelledAmounts> =
        html.select(".panel")
            .map {
                val title = it.select(".panel__title").text()
                val labelledAmounts = it.select(".contingent__data")
                    .map { parseLabelledAmount(it) }
                val kind = LabelledAmounts.Kind.fromString(title)
                LabelledAmounts(kind, title, labelledAmounts)
            }
            .filter { it.labelledAmounts.isNotEmpty() }

    private fun parseLabelledAmount(block: Element): LabelledAmount {
        val title = block.selectFirst(".panel__title")?.text()
            ?: block.selectFirst(".contingent__data--legend")?.text()
            ?: block.selectFirst(".contingent__data--remaining")?.text()!!
        log.info("title = $title")

        val value = block.selectFirst(".contingent__data--value")!!.text()

        val valueParts = value.split(" ")
        log.info("valueParts.size = ${valueParts.size}")

        val candidates = buildList {
            when (valueParts.size) {
                1 -> {
                    add(Pair(valueParts[0], null))
                }
                2 -> {
                    add(Pair(valueParts[0], valueParts[1]))
                    add(Pair(valueParts[1], valueParts[0]))
                }
            }
        }

        val (amount, unit) = candidates
            .mapNotNull { (value, unit) ->
                val parsedValue = tryParseValue(value) ?: return@mapNotNull null
                Amount(parsedValue, unit)
            }
            .firstOrNull()
            ?: throw CoopException.HtmlChanged()

        return LabelledAmount(title, Amount(amount, unit))
    }

    private fun tryParseValue(value: String): Double? = when {
        setOf("unbegrenzt", "illimitato", "illimité").contains(value) ->
            Double.POSITIVE_INFINITY
        else ->
            value.replace(".–", "").replace(",", ".").toDoubleOrNull()
    }

    fun parseConsumptionLog(consumption: RawConsumptionLog): List<ConsumptionLogEntry> =
        consumption.data.mapNotNull { parseConsumptionLogEntry(it) }

    private fun parseConsumptionLogEntry(
        rawConsumptionLogEntry: RawConsumptionLogEntry
    ): ConsumptionLogEntry? {
        if (!rawConsumptionLogEntry.isData) {
            return null
        }

        val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.GERMAN)
        val date = dateTimeFormatter.parse(rawConsumptionLogEntry.date, Instant::from)
        val type = rawConsumptionLogEntry.type
        val amount = rawConsumptionLogEntry.amount.replace("&#39;", "").toDouble()
        return ConsumptionLogEntry(date, type, amount)
    }

    fun parseProducts(html: Document): List<Product> =
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

    fun parseCorrespondences(html: Document): List<CorrespondenceHeader> =
        html.select(".table--mail tbody tr").map { parseCorrespondenceRow(it) }

    private fun parseCorrespondenceRow(it: Element): CorrespondenceHeader {
        val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)
        val instant = dateTimeFormatter.parse(it.selectFirst(".first")!!.text(), Instant::from)
        val subject = it.selectFirst(".second")!!.text()
        val details = URL(URL(config.coopBase()), it.selectFirst("a")!!.attr("href"))
        return CorrespondenceHeader(instant, subject, details)
    }

    fun getCorrespondenceMessage(html: Document): String =
        html.selectFirst(".panel__print__content")!!.text()

}
