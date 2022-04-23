package de.lorenzgorse.coopmobile.client.simple

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.lorenzgorse.coopmobile.CoopHtmlParser
import de.lorenzgorse.coopmobile.client.UnitValue
import de.lorenzgorse.coopmobile.client.UnitValueBlock
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.lang.Exception

class CoopHtmlParserTest {

    @RunWith(Parameterized::class)
    class Consumption(
        private val input: Document,
        private val output: List<UnitValue<Float>>,
        private val outputGeneric: List<UnitValueBlock>,
    ) {

        companion object {
            @JvmStatic
            @Parameters
            fun data(): List<Array<Any>> = listOf(
                "2022-04-21-prepaid-00",
                "2022-04-21-prepaid-01",
                "2022-04-21-wireless-00",
                "2022-04-22-wireless-00",
            ).map {
                val input = getInput(it)
                val consumption = getConsumption(it)
                val consumptionGeneric = getConsumptionGeneric(it)
                arrayOf(input, consumption, consumptionGeneric)
            }

            private fun getInput(name: String): Document {
                val inputHtml = getResource("testdata/$name/input.html")
                return Jsoup.parse(inputHtml)
            }

            private fun getConsumption(name: String): List<UnitValue<Float>> {
                val outputJson = getResource("testdata/$name/consumption.json")
                val type = TypeToken.getParameterized(
                    List::class.java,
                    TypeToken.getParameterized(
                        UnitValue::class.java, java.lang.Float::class.java
                    ).type
                ).type
                return Gson().fromJson(outputJson, type)
            }

            private fun getConsumptionGeneric(it: String): List<UnitValueBlock> {
                val outputGenericJson = getResource("testdata/$it/consumption-generic.json")
                val typeGeneric =
                    TypeToken.getParameterized(List::class.java, UnitValueBlock::class.java).type
                return Gson().fromJson(outputGenericJson, typeGeneric)
            }

            private fun getResource(name: String): String {
                val resource = Consumption::class.java.classLoader.getResourceAsStream(name)
                    ?: throw  Exception("resource $name not found")
                return resource.readAllBytes().decodeToString()
            }
        }

        private val parser = CoopHtmlParser()

        @Test
        fun testGetConsumption() {
            assertThat(parser.getConsumption(input), equalTo(output))
        }

        @Test
        fun testGetConsumptionGeneric() {
            assertThat(parser.getConsumptionGeneric(input), equalTo(outputGeneric))
        }

        @Suppress("unused")
        private fun <T> peekJson(value: T) =
            value.also { println(Gson().toJson(value)) }

    }

}
