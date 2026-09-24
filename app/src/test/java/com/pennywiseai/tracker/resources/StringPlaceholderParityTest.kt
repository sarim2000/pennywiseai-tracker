package com.pennywiseai.tracker.resources

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Every translated string must use the same format placeholders as the English
 * source (#832).
 *
 * A translator localising `%1$dh` to `%1$ч` drops the conversion character, and
 * `String.format` throws `UnknownFormatConversionException` at runtime — that
 * shipped a crash on the SMS-scan screen for every Russian user. The quieter
 * variant is `%1$2s`, which is *legal* (argument 1, width 2, as string) and so
 * compiles and runs, but silently drops the unit: "1m 30" instead of "1m 30s".
 *
 * Nothing else catches either one. Translations arrive by automated PR, and the
 * compiler is happy with both.
 */
class StringPlaceholderParityTest {

    private val resDir = File("src/main/res").takeIf { it.isDirectory }
        ?: File("app/src/main/res")

    /** `%`, optional `N$` index, flags/width/precision, then the conversion. */
    private val format = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z])""")

    private fun placeholders(value: String): List<String> =
        format.findAll(value.replace("%%", "")).map { it.value }.sorted().toList()

    private fun stringsIn(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = mutableMapOf<String, String>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            val name = el.getAttribute("name").takeIf { it.isNotBlank() } ?: continue
            when (el.tagName) {
                "string" -> out[name] = el.textContent
                "plurals" -> {
                    val items = el.childNodes
                    for (j in 0 until items.length) {
                        val item = items.item(j) as? Element ?: continue
                        out["$name[${item.getAttribute("quantity")}]"] = item.textContent
                    }
                }
            }
        }
        return out
    }

    @Test
    fun `translated strings use the same placeholders as English`() {
        val base = File(resDir, "values")
        assertTrue("Could not find res/values at ${resDir.absolutePath}", base.isDirectory)

        val english = base.listFiles { f -> f.name.endsWith(".xml") }
            .orEmpty()
            .associate { it.name to stringsIn(it) }

        val problems = mutableListOf<String>()
        resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }
            .orEmpty()
            .sortedBy { it.name }
            .forEach { localeDir ->
                localeDir.listFiles { f -> f.name.endsWith(".xml") }.orEmpty().sortedBy { it.name }
                    .forEach { file ->
                        val source = english[file.name] ?: return@forEach
                        stringsIn(file).forEach { (key, translated) ->
                            val expected = source[key]?.let(::placeholders) ?: return@forEach
                            val actual = placeholders(translated)
                            if (expected != actual) {
                                problems += "${localeDir.name}/${file.name} — $key: " +
                                    "English has $expected, translation has $actual  ($translated)"
                            }
                        }
                    }
            }

        assertTrue(
            "Translations whose format placeholders don't match English. A mismatch " +
                "either crashes String.format or drops text the user should see. Fix " +
                "these in Crowdin, not only in the XML, or the next sync reverts it:\n" +
                problems.joinToString("\n"),
            problems.isEmpty()
        )
    }
}
