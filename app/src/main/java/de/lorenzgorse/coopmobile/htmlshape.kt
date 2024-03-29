package de.lorenzgorse.coopmobile

import org.jsoup.nodes.Element as JsoupElement
import org.jsoup.nodes.Node as JsoupNode
import org.jsoup.nodes.TextNode as JsoupTextNode

sealed class HtmlShape {
    data class Element(
        val tag: String,
        val attrs: Map<String, String>,
        val children: List<HtmlShape>
    ) : HtmlShape()

    data class Text(val characters: List<CharacterShape>) : HtmlShape()
    data class Unknown(val fqcn: String?) : HtmlShape()
}

enum class CharacterShape {
    ALPHA,
    NUMERIC,
    WHITESPACE,
    OTHER
}

fun JsoupNode.shape(): HtmlShape? {
    return when (this) {
        is JsoupElement ->
            HtmlShape.Element(
                tagName(),
                attributes().asList()
                    .filter { isAllowedAttr(it.key) }
                    .associate { Pair(it.key, it.value) },
                childNodes().mapNotNull { it.shape() })
        is JsoupTextNode ->
            if (text().all { it.isWhitespace() }) {
                null
            } else {
                HtmlShape.Text(text().map(::characterShape))
            }
        else ->
            HtmlShape.Unknown(this::class.qualifiedName)
    }
}

val allowedAttrs = listOf("id", "class", "name", "type", "placeholder", "action")

fun isAllowedAttr(key: String): Boolean = allowedAttrs.contains(key.trim().lowercase())

fun characterShape(character: Char): CharacterShape {
    return when {
        character.isLetter() -> CharacterShape.ALPHA
        character.isDigit() -> CharacterShape.NUMERIC
        character.isWhitespace() -> CharacterShape.WHITESPACE
        else -> CharacterShape.OTHER
    }
}
