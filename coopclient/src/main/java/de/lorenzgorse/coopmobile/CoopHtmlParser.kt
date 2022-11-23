package de.lorenzgorse.coopmobile

import de.lorenzgorse.coopmobile.client.*
import de.lorenzgorse.coopmobile.client.simple.CoopException
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.slf4j.LoggerFactory
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
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
        html.select(".list-correspondence__item").map { parseCorrespondenceRow(it) }

    private fun parseCorrespondenceRow(it: Element): CorrespondenceHeader {
        val date = parseDate(it.selectFirst(".list-correspondence__data")!!.text())
        val subject = it.selectFirst(".list-correspondence__subject")!!.text()
        val details = URL(URL(config.coopBase()), it.attr("link-data"))
        return CorrespondenceHeader(date, subject, details)
    }

    private fun parseDate(dateStr: String): LocalDate {
        return parseDate1(dateStr) ?: parseDate2(dateStr) ?: throw CoopException.HtmlChanged()
    }

    private fun parseDate1(dateStr: String): LocalDate? {
        val months = mapOf(
            "Januar" to 1, "Gennaio" to 1, "Janvier" to 1,
            "Februar" to 2, "Febbraio" to 2, "Février" to 2,
            "März" to 3, "Marzo" to 3, "Mars" to 3,
            "April" to 4, "Aprile" to 4, "Avril" to 4,
            "Mai" to 5, "Maggio" to 5, "Mai" to 5,
            "Juni" to 6, "Giugno" to 6, "Juin" to 6,
            "Juli" to 7, "Luglio" to 7, "Juillet" to 7,
            "August" to 8, "Agosto" to 8, "Août" to 8,
            "September" to 9, "Settembre" to 9, "Septembre" to 9,
            "Oktober" to 10, "Ottobre" to 10, "Octobre" to 10,
            "November" to 11, "Novembre" to 11, "Novembre" to 11,
            "Dezember" to 12, "Dicembre" to 12, "Décembre" to 12,
        )
        val match = Regex("(\\d{2}) (\\p{L}+) (\\d{4})").matchEntire(dateStr) ?: return null
        val day = match.groups[1]!!.value.toInt()
        val month = months[match.groups[2]!!.value] ?: return null
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

    fun getCorrespondenceMessage(html: Document): String =
        html.selectFirst(".panel__print__content")!!.text()

}
