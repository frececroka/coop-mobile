package de.lorenzgorse.coopmobile.client.simple

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import de.lorenzgorse.coopmobile.CoopHtmlParser
import de.lorenzgorse.coopmobile.client.Config
import de.lorenzgorse.coopmobile.client.LabelledAmounts
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.lang.reflect.Type

class CoopHtmlParserTest {

    @RunWith(Parameterized::class)
    class Consumption(
        private val input: Document,
        private val consumption: List<LabelledAmounts>,
    ) {

        companion object {
            private val gson = GsonBuilder()
                .registerTypeAdapter(Double::class.java, DoubleDeserializer())
                .create()

            @JvmStatic
            @Parameters
            fun data(): List<Array<Any>> = listOf(
                "2022-04-21-prepaid-00",
                "2022-04-21-prepaid-01",
                "2022-04-21-wireless-00",
                "2022-04-22-wireless-00",
                "2022-04-27-prepaid-00",
                "2022-05-02-wireless-00",
            ).map {
                val input = getInput(it)
                val consumption = getConsumption(it)
                arrayOf(input, consumption)
            }

            private fun getInput(name: String): Document {
                val inputHtml = getResource("testdata/$name/input.html")
                return Jsoup.parse(inputHtml)
            }

            private fun getConsumption(it: String): List<LabelledAmounts> {
                val json = getResource("testdata/$it/consumption.json")
                val type =
                    TypeToken.getParameterized(List::class.java, LabelledAmounts::class.java).type
                return gson.fromJson(json, type)
            }

            private fun getResource(name: String): String {
                val resource = Consumption::class.java.classLoader.getResourceAsStream(name)
                    ?: throw  Exception("resource $name not found")
                return resource.readAllBytes().decodeToString()
            }
        }

        private val parser = CoopHtmlParser(Config())

        @Test
        fun testParseConsumption() {
            assertThat(parser.parseConsumption(input), equalTo(consumption))
        }

        @Suppress("unused")
        private fun <T> peekJson(value: T) =
            value.also { println(Gson().toJson(value)) }

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
