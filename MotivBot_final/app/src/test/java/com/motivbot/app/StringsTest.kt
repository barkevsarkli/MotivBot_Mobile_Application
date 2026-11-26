package com.motivbot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Checks the Turkish (values/) and Persian (values-fa/) strings against each other, the way the iOS
 * Localizable.strings files are kept in step, and the reviewed wording of the reflection scale.
 */
class StringsTest {

    private fun strings(folder: String): Map<String, String> {
        val file = File("src/main/res/$folder/strings.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val element = nodes.item(index) as Element
            element.getAttribute("name") to element.textContent
        }
    }

    /** What the user sees: resource escapes resolved, soft hyphens (line-break hints only) removed. */
    private fun visible(text: String) = text
        .replace("\\u00AD", "")
        .replace("­", "")
        .replace("\\'", "'")
        .replace("\\\"", "\"")
        .replace("\\n", "\n")

    private val formatSpecifier = Regex("%(\\d+\\$)?[sdf]")

    private val turkish = strings("values")
    private val persian = strings("values-fa")

    @Test
    fun bothLanguagesHaveTheSameKeys() {
        assertEquals("only in values/", emptySet<String>(), turkish.keys - persian.keys)
        assertEquals("only in values-fa/", emptySet<String>(), persian.keys - turkish.keys)
    }

    @Test
    fun formatArgumentsMatch() {
        for ((key, value) in turkish) {
            val expected = formatSpecifier.findAll(value).map { it.value }.sorted().toList()
            val actual = formatSpecifier.findAll(persian.getValue(key)).map { it.value }.sorted().toList()
            assertEquals(key, expected, actual)
        }
    }

    @Test
    fun noStringIsEmpty() {
        for ((key, value) in turkish + persian.mapKeys { "fa:${it.key}" }) {
            assertTrue(key, value.isNotBlank())
        }
    }

    /** The wording requested in the professor's review, in both languages. */
    @Test
    fun reflectionLabelsUseReviewedWording() {
        val keys = (1..5).map { "reflection_rating_$it" }
        assertEquals(
            listOf("Çok daha kötü", "Biraz daha kötü", "Aynı / Değişmedi", "Biraz daha iyi", "Çok daha iyi"),
            keys.map { visible(turkish.getValue(it)) },
        )
        assertEquals("Mutluluk ve umut", visible(turkish.getValue("reflection_topic_happiness_and_hope")))
        assertEquals(
            listOf("خیلی بدتر", "کمی بدتر", "مثل قبل / تغییری نکرده", "کمی بهتر", "خیلی بهتر"),
            keys.map { visible(persian.getValue(it)) },
        )
    }

    @Test
    fun demographicsAskOnlyAgeAndGender() {
        // The diagnosis and treatment questions were removed from the questionnaire.
        assertTrue(turkish.keys.none { it == "qa_5" || it == "qa_6" })
    }
}
