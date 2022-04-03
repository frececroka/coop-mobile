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
                    <h1 class="c1" id="id1">Text</h1>
                    <form action="/signin">
                        <input type="text" name="username" placeholder="Username" />
                        <input type="password" value="sensitive" />
                    </form>
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
                            attrs = mapOf("class" to "c1", "id" to "id1"),
                            children = listOf(
                                HtmlShape.Text(
                                    characters = testCharacterShape("AAAA")
                                )
                            )
                        ),
                        HtmlShape.Element(
                            tag = "form",
                            attrs = mapOf("action" to "/signin"),
                            children = listOf(
                                HtmlShape.Element(
                                    tag = "input",
                                    attrs = mapOf(
                                        "type" to "text",
                                        "name" to "username",
                                        "placeholder" to "Username",
                                    ),
                                    children = listOf()
                                ),
                                HtmlShape.Element(
                                    tag = "input",
                                    attrs = mapOf("type" to "password"),
                                    children = listOf()
                                ),
                            )
                        ),
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
