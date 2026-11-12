package com.pennywiseai.tracker.domain.conscience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class ConscienceLinesTest {

    private object Words : ConscienceWords {
        override val locale: Locale = Locale.ENGLISH
        override fun hours(hours: Int) = "$hours hours"
        override fun category(category: ConscienceCategory) = category.name.lowercase()
        override fun percent(percent: Int) = "$percent%"
    }

    private val food = ConscienceCategory.FOOD

    @Test
    fun `ids are unique and safe for the logs`() {
        val ids = ConscienceLines.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { '|' in it || ':' in it })
        assertEquals(ConscienceLines.all.size, ConscienceLines.all.map { it.res }.toSet().size)
    }

    @Test
    fun `food gets its own pools where it has them`() {
        val shopping = ConscienceCategory.SHOPPING
        val fun_ = ConscienceCategory.ENTERTAINMENT
        listOf<(ConscienceCategory) -> ConscienceFact>(
            { ConscienceFact.Repeat(it, 3, 6) },
            { ConscienceFact.LateNight(it) },
        ).forEach { make ->
            assertEquals(ConscienceLines.pool(make(shopping)), ConscienceLines.pool(make(fun_)))
            assertTrue(ConscienceLines.pool(make(food)).intersect(ConscienceLines.pool(make(shopping)).toSet()).isEmpty())
        }
        assertEquals(ConscienceLines.pool(ConscienceFact.Positive(food, 25)), ConscienceLines.pool(ConscienceFact.Positive(shopping, 25)))
    }

    @Test
    fun `render fills placeholders and capitalizes only sentence starts`() {
        assertEquals("Food: 25% less", ConscienceLines.render("{category}: {percent} less", ConscienceFact.Positive(food, 25), Words))
        assertEquals("3 in 6 hours", ConscienceLines.render("{count} in {hours}", ConscienceFact.Repeat(food, 3, 6), Words))
        val late = ConscienceFact.LateNight(food)
        assertEquals("Hm. Food again? Food it is", ConscienceLines.render("hm. {category} again? {category} it is", late, Words))
        assertEquals("Wait… food, and more food", ConscienceLines.render("wait… {category}, and more {category}", late, Words))
    }

    @Test
    fun `a placeholder the fact can't fill or a stray brace drops the line`() {
        val late = ConscienceFact.LateNight(food)
        assertNull(ConscienceLines.render("{count} late", late, Words))
        assertNull(ConscienceLines.render("{amount} spent", late, Words))
        assertNull(ConscienceLines.render("{tired|exhausted}", late, Words))
        assertNull(ConscienceLines.render("oops {", late, Words))
        assertNull(ConscienceLines.render("", late, Words))
        assertEquals("Hi food", ConscienceLines.render("hi {category}", late, Words))
    }

    @Test
    fun `the feature only speaks an app language its lines are written in`() {
        assertTrue(ConscienceLines.speaks("en", "en"))
        assertTrue(ConscienceLines.speaks("ar", "ar"))
        // English is the fallback for every language without lines of its own.
        assertFalse(ConscienceLines.speaks("en", "de"))
        assertFalse(ConscienceLines.speaks("en", "hi"))
    }

    // --- The resources themselves, every language ---

    // Gradle runs from the module directory, an IDE may run from the repo root.
    private val res = File(if (File("settings.gradle.kts").isFile) File(".") else File(".."), "app/src/main/res")

    private fun plurals(file: File): Set<String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("plurals")
        return (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }.toSet()
    }

    private fun strings(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to
                node.textContent.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
        }
    }

    private val localeDirs: List<File> by lazy {
        // Keyed on the lines, not the UI strings: Crowdin can add strings_conscience.xml to any language.
        res.listFiles().orEmpty().filter { File(it, "conscience_lines.xml").exists() }.sortedBy { it.name }
            .also { assertTrue("no conscience_lines.xml found under ${res.absolutePath}", it.isNotEmpty()) }
    }

    /** Every fact a line's pool can be asked to render, so each line is tried in each of its contexts. */
    private fun factsFor(line: ConscienceLine): List<ConscienceFact> {
        val all = ConscienceCategory.entries.flatMap { category ->
            listOf(
                ConscienceFact.Repeat(category, 3, 6),
                ConscienceFact.Repeat(category, 4, 24),
                ConscienceFact.LateNight(category),
                ConscienceFact.Positive(category, 25),
            )
        }
        return all.filter { line in ConscienceLines.pool(it) }
    }

    @Test
    fun `every language has every line, and every line renders in every context`() {
        assertEquals(listOf("values", "values-ar-rSA"), localeDirs.map { it.name })
        localeDirs.forEach { dir ->
            val lines = strings(File(dir, "conscience_lines.xml"))
            assertTrue("${dir.name} names its language", lines["conscience_content_locale"].orEmpty().isNotBlank())
            // In the same file as the lines: from Crowdin's files, a pull could change or drop them.
            listOf("category_food", "category_entertainment", "category_shopping", "percent").forEach {
                assertTrue("${dir.name} is missing $it", lines["conscience_$it"].orEmpty().isNotBlank())
            }
            assertTrue("${dir.name} is missing conscience_hours", "conscience_hours" in plurals(File(dir, "conscience_lines.xml")))
            ConscienceLines.all.forEach { line ->
                val template = lines["conscience_line_${line.id}"]
                assertNotNull("${dir.name} is missing ${line.id}", template)
                factsFor(line).forEach { fact ->
                    assertNotNull("${dir.name}/${line.id} doesn't render for $fact", ConscienceLines.render(template!!, fact, Words))
                }
            }
        }
    }

    @Test
    fun `no line carries money, and no language falls back to another's line`() {
        val currency = Regex("""[₹$€£¥]|\b(JOD|INR|USD|AED|SAR)\b""")
        val contentLocales = localeDirs.map { strings(File(it, "conscience_lines.xml"))["conscience_content_locale"] }
        assertEquals(contentLocales.size, contentLocales.toSet().size)
        localeDirs.forEach { dir ->
            strings(File(dir, "conscience_lines.xml")).filterKeys { it.startsWith("conscience_line_") }.forEach { (name, text) ->
                assertFalse("${dir.name}/$name mentions money", currency.containsMatchIn(text))
            }
        }
    }
}
