package com.sarim.app

import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The three `strings.xml` files must stay at key parity: every screen string resolves in the
 * Arabic default, Portuguese and English, and a missing key in one file would otherwise only
 * surface when a user runs that language. This is the test RUN_AND_TEST.md claims is in place.
 */
class StringsParityTest {

    private val locales =
        listOf("values" to "en", "values-ar" to "ar", "values-pt" to "pt")

    private fun keys(languageDir: String, tag: String): Set<String> {
        val file = File("src/main/res/$languageDir/strings.xml")
        if (!file.exists()) fail("missing strings file: ${file.path}")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName(tag)
        return (0 until nodes.length).map {
            nodes.item(it).attributes.getNamedItem("name").nodeValue
        }.toSet()
    }

    private fun stringKeys(languageDir: String): Set<String> = keys(languageDir, "string")

    private fun pluralKeys(languageDir: String): Set<String> = keys(languageDir, "plurals")

    @Test
    fun `en, ar and pt strings files hold the same keys`() {
        val byLocale = locales.associate { (dir, code) -> code to stringKeys(dir) }

        val english = byLocale.getValue("en")
        byLocale.getValue("ar") shouldBe english
        byLocale.getValue("pt") shouldBe english
    }

    @Test
    fun `en, ar and pt files hold the same plurals`() {
        val byLocale = locales.associate { (dir, code) -> code to pluralKeys(dir) }

        val english = byLocale.getValue("en")
        byLocale.getValue("ar") shouldBe english
        byLocale.getValue("pt") shouldBe english
    }

    @Test
    fun `the english file holds the full set, not a stub`() {
        // Counts every UI resource entry, strings and plurals together: a count message that
        // becomes a plural keeps the total, so this still catches a truncated file. 318 is the
        // whole UI of a porn-blocking app — focus mode, time limits, usage warnings, app groups,
        // colour filters, impulse lock and the activity blocker were removed with their strings.
        // Raise this when adding entries; lower it only when a feature is deliberately removed,
        // never to make a deletion pass green.
        (stringKeys("values").size + pluralKeys("values").size) shouldBe 318
    }
}
