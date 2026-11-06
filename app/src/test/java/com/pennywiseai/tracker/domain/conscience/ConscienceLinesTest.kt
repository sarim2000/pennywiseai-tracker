package com.pennywiseai.tracker.domain.conscience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConscienceLinesTest {

    private val voices = ConscienceVoice.entries
    private val addresses = ConscienceAddress.entries

    private val foodComeback = ConscienceFact.Comeback(ConscienceCategory.FOOD, 5)
    private val genericComebacks = listOf(
        ConscienceFact.Comeback(ConscienceCategory.ENTERTAINMENT, 5),
        ConscienceFact.Comeback(ConscienceCategory.SHOPPING, 5),
    )
    private val recordDays = ConscienceCategory.entries.map { ConscienceFact.RecordDay(it) }

    private fun distinctPools(): List<List<ConscienceLine>> = voices.flatMap { voice ->
        listOf(foodComeback, genericComebacks.first(), recordDays.first()).map { ConscienceLines.pool(voice, it) }
    }

    private fun allLines() = distinctPools().flatten()

    private fun everyFact(days: Int): List<ConscienceFact> =
        ConscienceCategory.entries.map { ConscienceFact.Comeback(it, days) } + recordDays

    private fun jordanianLine(fact: ConscienceFact, id: String) =
        ConscienceLines.pool(ConscienceVoice.JORDANIAN, fact).single { it.id == id }

    @Test
    fun `ids are unique across pools and safe for the log`() {
        val ids = allLines().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { '|' in it })
    }

    @Test
    fun `each pool has at least twelve lines`() {
        assertEquals(6, distinctPools().size)
        distinctPools().forEach { assertTrue("pool of ${it.size}", it.size >= 12) }
    }

    @Test
    fun `each fact gets the right pool`() {
        voices.forEach { voice ->
            val food = ConscienceLines.pool(voice, foodComeback)
            val generic = ConscienceLines.pool(voice, genericComebacks[0])
            val record = ConscienceLines.pool(voice, recordDays[0])
            assertEquals(generic, ConscienceLines.pool(voice, genericComebacks[1]))
            recordDays.forEach { assertEquals(record, ConscienceLines.pool(voice, it)) }
            assertEquals(3, setOf(food, generic, record).size)
        }
    }

    @Test
    fun `every line renders with no leftover placeholder`() {
        voices.forEach { voice ->
            addresses.forEach { address ->
                everyFact(days = 5).forEach { fact ->
                    ConscienceLines.pool(voice, fact).forEach { line ->
                        val text = ConscienceLines.render(line, voice, address, fact)
                        assertFalse(text, '{' in text || '}' in text)
                    }
                }
            }
        }
    }

    @Test
    fun `comeback lines render days with the right plural`() {
        val expected = mapOf(
            ConscienceVoice.JORDANIAN to mapOf(3 to "3 أيام", 10 to "10 أيام", 11 to "11 يوم", 30 to "30 يوم"),
            ConscienceVoice.ENGLISH to mapOf(3 to "3 days", 10 to "10 days", 11 to "11 days", 30 to "30 days"),
        )
        expected.forEach { (voice, phrases) ->
            phrases.forEach { (days, phrase) ->
                ConscienceCategory.entries.forEach { category ->
                    val fact = ConscienceFact.Comeback(category, days)
                    addresses.forEach { address ->
                        ConscienceLines.pool(voice, fact).forEach { line ->
                            val text = ConscienceLines.render(line, voice, address, fact)
                            assertTrue("$phrase missing from: $text", phrase in text)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `category nouns render in the voice`() {
        val line = ConscienceLine("test", "{category}")
        val fact = ConscienceFact.RecordDay(ConscienceCategory.SHOPPING)
        assertEquals("تسوق", ConscienceLines.render(line, ConscienceVoice.JORDANIAN, ConscienceAddress.MASCULINE, fact))
        assertEquals("shopping", ConscienceLines.render(line, ConscienceVoice.ENGLISH, ConscienceAddress.MASCULINE, fact))
    }

    @Test
    fun `marked Jordanian lines differ by address`() {
        val record = recordDays.first()
        val marked = listOf(
            jordanianLine(foodComeback, "jo_food_comeback_01"),
            jordanianLine(genericComebacks.first(), "jo_comeback_04"),
            jordanianLine(genericComebacks.first(), "jo_comeback_06"),
            jordanianLine(record, "jo_record_day_01"),
            jordanianLine(record, "jo_record_day_09"),
        )
        marked.forEach { assertNotEquals(it.masculine, it.feminine) }

        val keepGoing = jordanianLine(record, "jo_record_day_01")
        val render = { address: ConscienceAddress ->
            ConscienceLines.render(keepGoing, ConscienceVoice.JORDANIAN, address, record)
        }
        assertEquals("كمل كمل 😂", render(ConscienceAddress.MASCULINE))
        assertEquals("كملي كملي 😂", render(ConscienceAddress.FEMININE))
    }

    @Test
    fun `English lines have no gendered form`() {
        listOf(foodComeback, genericComebacks.first(), recordDays.first()).forEach { fact ->
            ConscienceLines.pool(ConscienceVoice.ENGLISH, fact).forEach { assertEquals(it.masculine, it.feminine) }
        }
    }

    @Test
    fun `no line uses a banned word or a digit`() {
        val banned = listOf("سجل", "أرشيف", "ملف", "record", "delivery", "دليفري")
        allLines().flatMap { listOf(it.masculine, it.feminine) }.forEach { text ->
            banned.forEach { word -> assertFalse("$word in: $text", text.contains(word, ignoreCase = true)) }
            assertFalse("digit in: $text", text.any { it.isDigit() })
        }
    }

    @Test
    fun `lines stay short with the longest facts`() {
        voices.forEach { voice ->
            addresses.forEach { address ->
                everyFact(days = 365).forEach { fact ->
                    ConscienceLines.pool(voice, fact).forEach { line ->
                        val text = ConscienceLines.render(line, voice, address, fact)
                        assertTrue("${text.length}: $text", text.length < 90)
                    }
                }
            }
        }
    }

    @Test
    fun `samples are three finished lines`() {
        voices.forEach { voice ->
            addresses.forEach { address ->
                val samples = ConscienceLines.samples(voice, address)
                assertEquals(3, samples.size)
                assertEquals(3, samples.toSet().size)
                samples.forEach { assertFalse(it, '{' in it || it.isBlank()) }
            }
        }
    }

    @Test
    fun `the lock screen text says nothing about spending`() {
        voices.forEach { voice ->
            val text = ConscienceLines.lockScreenText(voice)
            val allNouns = listOf("أكل", "ترفيه", "تسوق", "food", "fun", "shopping", "شهرين", "months")
            allNouns.forEach { assertFalse(it, it in text) }
        }
    }
}
