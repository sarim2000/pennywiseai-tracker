package com.pennywiseai.tracker.domain.conscience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class ConscienceLinesTest {

    private object Words : ConscienceWords {
        override val locale: Locale = Locale.ENGLISH
        override fun days(days: Int) = "$days days"
        override fun hours(hours: Int) = "$hours hours"
        override fun category(category: ConscienceCategory) = category.name.lowercase()
        override fun weekday(day: DayOfWeek) = day.name.lowercase()
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
            { ConscienceFact.Comeback(it, 5) },
            { ConscienceFact.Repeat(it, 3, 6) },
            { ConscienceFact.LateNight(it) },
            { ConscienceFact.Hello(it) },
        ).forEach { make ->
            assertEquals(ConscienceLines.pool(make(shopping)), ConscienceLines.pool(make(fun_)))
            assertTrue(ConscienceLines.pool(make(food)).intersect(ConscienceLines.pool(make(shopping)).toSet()).isEmpty())
        }
        assertEquals(ConscienceLines.pool(ConscienceFact.Spike(food)), ConscienceLines.pool(ConscienceFact.Spike(shopping)))
    }

    @Test
    fun `every kind has a pool of at least six`() {
        ConscienceLines.sampleFacts.forEach { assertTrue("${it.kind}", ConscienceLines.pool(it).size >= 6) }
        assertEquals(ConscienceKind.entries.toSet(), ConscienceLines.sampleFacts.map { it.kind }.toSet())
    }

    @Test
    fun `samples walk every kind before repeating one, then move along the pool`() {
        val kinds = (0 until ConscienceKind.entries.size).map { ConscienceLines.sample(it).first.kind }
        assertEquals(ConscienceKind.entries.toSet(), kinds.toSet())
        val first = ConscienceLines.sample(0)
        val nextRound = ConscienceLines.sample(ConscienceLines.sampleFacts.size)
        assertEquals(first.first, nextRound.first)
        assertFalse(first.second == nextRound.second)
        assertNotNull(ConscienceLines.sample(-1))
    }

    @Test
    fun `render picks the address form and fills placeholders`() {
        val fact = ConscienceFact.Comeback(food, 5)
        val template = "back after {days}, {tired|exhausted}"
        assertEquals("Back after 5 days, tired", ConscienceLines.render(template, fact, false, Words))
        assertEquals("Back after 5 days, exhausted", ConscienceLines.render(template, fact, true, Words))
        assertEquals("Food: 25% less", ConscienceLines.render("{category}: {percent} less", ConscienceFact.Positive(food, 25, 0), false, Words))
        assertEquals("3 in 6 hours", ConscienceLines.render("{count} in {hours}", ConscienceFact.Repeat(food, 3, 6), false, Words))
        assertEquals("Friday again", ConscienceLines.render("{weekday} again", ConscienceFact.Weekly(food, DayOfWeek.FRIDAY), false, Words))
        assertNull(ConscienceLines.render("{count} on {weekday}", ConscienceFact.Repeat(food, 3, 6), false, Words))
        val hello = ConscienceFact.Hello(food)
        assertEquals("Hm. Food again? Food it is", ConscienceLines.render("hm. {category} again? {category} it is", hello, false, Words))
        assertEquals("Wait… food, and more food", ConscienceLines.render("wait… {category}, and more {category}", hello, false, Words))
    }

    @Test
    fun `a placeholder the fact can't fill, or a stray brace, drops the line`() {
        val hello = ConscienceFact.Hello(food)
        assertNull(ConscienceLines.render("after {days}", hello, false, Words))
        assertNull(ConscienceLines.render("{amount} spent", hello, false, Words))
        assertNull(ConscienceLines.render("oops {", hello, false, Words))
        assertNull(ConscienceLines.render("", hello, false, Words))
        assertEquals("Hi food", ConscienceLines.render("hi {category}", hello, false, Words))
    }

    @Test
    fun `marksAddress spots an alternation and nothing else`() {
        assertTrue(ConscienceLines.marksAddress(listOf("plain", "{back|back}")))
        assertFalse(ConscienceLines.marksAddress(listOf("{days} of {category}")))
    }

    // --- The resources themselves, every language ---

    private val res = File("src/main/res")

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
        res.listFiles().orEmpty().filter { File(it, "strings_conscience.xml").exists() }.sortedBy { it.name }
    }

    /** Every fact a line's pool can be asked to render, so each line is tried in each of its contexts. */
    private fun factsFor(line: ConscienceLine): List<ConscienceFact> {
        val all = ConscienceCategory.entries.flatMap { category ->
            listOf(
                ConscienceFact.Comeback(category, 5),
                ConscienceFact.Comeback(category, 21),
                ConscienceFact.RecordDay(category),
                ConscienceFact.Repeat(category, 3, 6),
                ConscienceFact.Repeat(category, 4, 24),
                ConscienceFact.LateNight(category),
                ConscienceFact.Spike(category),
                ConscienceFact.Positive(category, 25, 0),
                ConscienceFact.Weekly(category, DayOfWeek.THURSDAY),
                ConscienceFact.Hello(category),
            )
        }
        return all.filter { line in ConscienceLines.pool(it) }
    }

    @Test
    fun `every language has every line, and every line renders in every context and address`() {
        assertTrue("found ${localeDirs.size} languages", localeDirs.size >= 14)
        localeDirs.forEach { dir ->
            val file = File(dir, "conscience_lines.xml")
            assertTrue("${dir.name} has no conscience_lines.xml", file.exists())
            val lines = strings(file)
            assertTrue("${dir.name} names its language", lines["conscience_content_locale"].orEmpty().isNotBlank())
            ConscienceLines.all.forEach { line ->
                val template = lines["conscience_line_${line.id}"]
                assertNotNull("${dir.name} is missing ${line.id}", template)
                factsFor(line).forEach { fact ->
                    listOf(false, true).forEach { feminine ->
                        val rendered = ConscienceLines.render(template!!, fact, feminine, Words)
                        assertNotNull("${dir.name}/${line.id} doesn't render for $fact", rendered)
                    }
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
