package de.lorenzgorse.coopmobile

import de.lorenzgorse.coopmobile.client.*
import de.lorenzgorse.coopmobile.client.simple.CoopException
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements
import org.slf4j.LoggerFactory
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

class CoopHtmlParser(
    private val config: Config,
    @Suppress("unused")
    private val experiments: Experiments,
) {

    data class Experiments(
        val unused: Boolean = false,
    )

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

    fun parseConsumption(html: Document): List<LabelledAmounts> =
        html.select(".panel")
            .flatMap { panel ->
                val title = panel.select(".panel__title").exactlyOne().text()
                panel.select(".contingent")
                    .map { contingent ->
                        val subtitle = contingent.select(".panel__subtitle").atMostOne()?.text()
                        val labelledAmounts = contingent.select(".contingent__data")
                            .map(::parseLabelledAmount)
                        LabelledAmounts(subtitle ?: title, labelledAmounts, listOf())
                    }
            }
            .filter { it.labelledAmounts.isNotEmpty() }

    private fun parseLabelledAmount(block: Element): LabelledAmount {
        val title = block.selectFirst(".panel__title")?.text()
            ?: block.selectFirst(".contingent__data--legend")?.text()
            ?: block.selectFirst(".contingent__data--remaining")?.text()!!
        log.info("title = $title")

        val isCreditBalance = block.parent()?.id() == "credit_balance"

        val value = block.selectFirst(".contingent__data--value")!!.text()

        val valueParts = value.split(" ")
        log.info("valueParts.size = ${valueParts.size}")

        val candidates = buildList {
            when (valueParts.size) {
                1 -> {
                    val unit = if (isCreditBalance) "CHF" else null
                    add(Pair(valueParts[0], unit))
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
                val parsedUnit = unit?.let { AmountUnit(it) }
                Amount(parsedValue, parsedUnit)
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

        // 17.05.2022 06:00:00
        // 11.05.2022 09:08:55
        val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.GERMAN)
        val dateTime = dateTimeFormatter.parse(rawConsumptionLogEntry.date.trim(), LocalDateTime::from)
        val instant = dateTime.atZone(ZoneId.of("Europe/Zurich")).toInstant()
        val type = rawConsumptionLogEntry.type
        val amount = rawConsumptionLogEntry.amount.replace("&#39;", "").toDouble()
        return ConsumptionLogEntry(instant, type, amount)
    }

    fun parseProducts(html: Document): List<Product> =
        html.select(".add_product").map { parseProductBlock(it) }

    private fun parseProductBlock(productBlock: Element): Product {
        val content = productBlock.selectFirst(".modal-body")!!

        val nameElement = content.selectFirst(".modal__content__info")!!.also { it.remove() }
        val priceElement = nameElement.selectFirst(".modal__content__price")!!.also { it.remove() }

        val name = nameElement.text()
        val price = priceElement.text()

        val description = allTextNodes(content)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n") {
                if (!it.endsWith(".") && !it.endsWith(":")) {
                    "$it."
                } else {
                    it
                }
            }

        val form = productBlock.root().selectFirst(".js-add-option-form")!!
        val url = form.attr("action")
        val authenticityToken = form.selectFirst("input[name=authenticity_token]")!!.attr("value")
        val optionId = productBlock.selectFirst(".modal-footer .js-add-option")!!.dataset()["option-id"]!!
        val parameters = mapOf(
            "authenticity_token" to authenticityToken,
            "option_id" to optionId,
            // If this is true, the option price will be paid with the registered payment
            // method instead of the prepaid balance. For prepaid accounts, we want to use the
            // prepaid balance, so we set it to false. But for non-prepaid accounts, this might
            // have to be true?
            "paid_with_saved_cc" to "false",
        )
        val buySpec = ProductBuySpec(url, parameters)

        return Product(name, description, price, buySpec)
    }

    private fun allTextNodes(n: Node): List<String> = when (n)  {
        is TextNode -> listOf(n.text())
        else -> n.childNodes().flatMap { allTextNodes(it) }
    }

    fun parseCorrespondences(html: Document): List<CorrespondenceHeader> =
        html.select(".list-correspondence__item.js-correspondence-type")
            .map { parseCorrespondenceRow(it) }

    private fun parseCorrespondenceRow(it: Element): CorrespondenceHeader {
        val date = parseDate(it.selectFirst(".list-correspondence__data")!!.text())
        val dirtySubject = it.selectFirst(".list-correspondence__subject")!!.text()
        val subject = cleanCorrespondenceSubject(dirtySubject)
        val details = URL(URL(config.coopBase()), it.attr("link-data"))
        return CorrespondenceHeader(date, subject, details)
    }

    private fun parseDate(dateStr: String): LocalDate {
        return parseDate1(dateStr) ?: parseDate2(dateStr) ?: throw CoopException.HtmlChanged()
    }

    private fun parseDate1(dateStr: String): LocalDate? {
        val months = mapOf(
            "januar" to 1, "gennaio" to 1, "janvier" to 1,
            "februar" to 2, "febbraio" to 2, "février" to 2,
            "märz" to 3, "marzo" to 3, "mars" to 3,
            "april" to 4, "aprile" to 4, "avril" to 4,
            "mai" to 5, "maggio" to 5, "mai" to 5,
            "juni" to 6, "giugno" to 6, "juin" to 6,
            "juli" to 7, "Luglio" to 7, "juillet" to 7,
            "august" to 8, "agosto" to 8, "août" to 8,
            "september" to 9, "settembre" to 9, "septembre" to 9,
            "oktober" to 10, "ottobre" to 10, "octobre" to 10,
            "november" to 11, "novembre" to 11, "novembre" to 11,
            "dezember" to 12, "dicembre" to 12, "décembre" to 12,
        )
        val match = Regex("(\\d{2}) (\\p{L}+) (\\d{4})").matchEntire(dateStr) ?: return null
        val day = match.groups[1]!!.value.toInt()
        val month = months[match.groups[2]!!.value.lowercase()] ?: return null
        val year = match.groups[3]!!.value.toInt()
        return LocalDate.of(year, month, day)
    }

    private fun parseDate2(dateStr: String): LocalDate? {
        return try {
            LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        } catch (e: DateTimeParseException) {
            log.error("Cannot parse date: $dateStr", e)
            null
        }
    }

    private fun cleanCorrespondenceSubject(string: String): String {
        val parts = string.split(":", limit = 2)
        val subject = if (parts.size == 1) {
            string
        } else {
            parts[1]
        }
        return subject.trim()
    }

    fun getCorrespondenceMessage(html: Document): String =
        html.selectFirst(".panel__print__content")!!.text()

}

fun Elements.atMostOne(): Element? {
    if (size > 1) {
        throw IllegalArgumentException("Expected at most one element, but got $size")
    }
    return first()
}

fun Elements.exactlyOne(): Element {
    return atMostOne()
        ?: throw IllegalArgumentException("Expected exactly one element, but got $size")
}
