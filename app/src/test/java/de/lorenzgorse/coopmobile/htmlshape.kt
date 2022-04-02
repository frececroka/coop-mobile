package de.lorenzgorse.coopmobile

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.jsoup.Jsoup
import org.junit.Test

class HtmlShapeTest {

    @Test
    fun testJsoupElementToHtmlShape() {
        val document = Jsoup.parse(
            """
            <html>
                <body>
                    Text
                    <h1 attr="value">Text</h1>
                </body>
            </html>
        """.trimIndent()
        )
        val shape = document.body().shape()
        assertThat(
            shape, equalTo(
                HtmlShape.Element(
                    tag = "body",
                    attrs = mapOf(),
                    children = listOf(
                        HtmlShape.Text(characters = testCharacterShape("_AAAA_")),
                        HtmlShape.Element(
                            tag = "h1",
                            attrs = mapOf("attr" to "value"),
                            children = listOf(
                                HtmlShape.Text(
                                    characters = testCharacterShape("AAAA")
                                )
                            )
                        ),
                        HtmlShape.Text(characters = testCharacterShape("_")),
                        HtmlShape.Text(characters = testCharacterShape("_"))
                    )
                )
            )
        )
    }

    private fun testCharacterShape(pattern: String): List<CharacterShape> = pattern.map {
        when (it) {
            'A' -> CharacterShape.ALPHA
            '0' -> CharacterShape.NUMERIC
            '_' -> CharacterShape.WHITESPACE
            '?' -> CharacterShape.OTHER
            else -> throw IllegalArgumentException()
        }
    }

}
