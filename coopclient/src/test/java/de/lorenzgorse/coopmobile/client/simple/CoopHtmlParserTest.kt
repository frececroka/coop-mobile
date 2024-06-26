package de.lorenzgorse.coopmobile.client.simple

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import de.lorenzgorse.coopmobile.CoopHtmlParser
import de.lorenzgorse.coopmobile.client.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.lang.reflect.Type
import java.time.Instant
import java.time.LocalDate
import java.util.*

class CoopHtmlParserTest {

    @RunWith(Parameterized::class)
    class Consumption(private val testcase: String) {

        companion object {

            @JvmStatic
            @Parameters
            fun data(): List<String> = listOf(
                "2022-04-21-prepaid-01",
                "2022-04-21-wireless-00",
                "2022-04-22-wireless-00",
                "2022-04-27-prepaid-00",
                "2022-05-02-wireless-00",
                "2022-10-07-prepaid-00",
                "2023-08-25-prepaid-00",
                "2023-08-25-prepaid-01",
                "2024-05-18-prepaid-00",
            )
        }

        private val gson = GsonBuilder()
            .registerTypeAdapter(Double::class.java, DoubleDeserializer())
            .registerTypeAdapter(LocalDate::class.java, LocalDateDeserializer())
            .create()

        @Test
        fun testParseConsumption() {
            val parser = CoopHtmlParser(LocalizedConfig(), CoopHtmlParser.Experiments())
            val input = getInput()
            getConsumption().ifPresent{
                assertThat(parser.parseConsumption(input), equalTo(it)) }
            getCorrespondences().ifPresent{
                assertThat(parser.parseCorrespondences(input), equalTo(it)) }
            getProducts().ifPresent {
                assertThat(parser.parseProducts(input), equalTo(it)) }
        }

        private fun getInput(): Document {
            val inputHtml = getResource("input.html").get()
            return Jsoup.parse(inputHtml)
        }

        private fun getConsumption(): Optional<List<LabelledAmounts>> {
            val type =
                TypeToken.getParameterized(List::class.java, LabelledAmounts::class.java).type
            return getJson("consumption", type)
        }

        private fun getCorrespondences(): Optional<List<CorrespondenceHeader>> {
            val type =
                TypeToken.getParameterized(List::class.java, CorrespondenceHeader::class.java).type
            return getJson("correspondences", type)
        }

        private fun getProducts(): Optional<List<Product>> {
            val type = TypeToken.getParameterized(List::class.java, Product::class.java).type
            return getJson("products", type)
        }

        private fun <T> getJson(kind: String, type: Type): Optional<T> =
            getResource("$kind.json").map { gson.fromJson(it, type) }

        private fun getResource(name: String): Optional<String> {
            val path = "testdata/$testcase/$name"
            val resource = Consumption::class.java.classLoader.getResourceAsStream(path)
                ?: return Optional.empty()
            return Optional.of(resource.readAllBytes().decodeToString())
        }

        @Suppress("unused")
        private fun <T> peekJson(value: T) =
            value.also { println(Gson().toJson(value)) }

    }

    class ConsumptionLog {

        private val parser = CoopHtmlParser(LocalizedConfig(), CoopHtmlParser.Experiments())

        @Test
        fun test() {
            val consumptionLog = parser.parseConsumptionLog(
                RawConsumptionLog(
                    listOf(
                        RawConsumptionLogEntry(
                            " 17.05.2022 06:00:00 ",
                            true,
                            "type",
                            "12&#39;34"
                        )
                    )
                )
            )
            assertThat(
                consumptionLog, equalTo(
                    listOf(
                        ConsumptionLogEntry(
                            Instant.parse("2022-05-17T04:00:00Z"),
                            "type",
                            1234.0
                        )
                    )
                )
            )
        }

    }

}

private class DoubleDeserializer : JsonDeserializer<Double> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ) = when (val double = json.asDouble) {
        9999.0 -> Double.POSITIVE_INFINITY
        else -> double
    }
}

private class LocalDateDeserializer : JsonDeserializer<LocalDate> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): LocalDate {
        val jsonObject = json.asJsonObject
        val year = jsonObject.get("year").asInt
        val month = jsonObject.get("month").asInt
        val day = jsonObject.get("day").asInt
        return LocalDate.of(year, month, day)
    }
}
