package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** A spoken entry for the quick add sheet, read without the model. */
class VoiceEntryTest {
    private val today = LocalDate.of(2026, 3, 10)
    private val categories = listOf(
        CategoryEntity(id = 1, name = "Food & Dining", color = "#000"),
        CategoryEntity(id = 2, name = "Groceries", color = "#000"),
        CategoryEntity(id = 3, name = "Transportation", color = "#000"),
        CategoryEntity(id = 4, name = "Others", color = "#000"),
        CategoryEntity(id = 5, name = "Salary", color = "#000", isIncome = true),
        CategoryEntity(id = 6, name = "Income", color = "#000", isIncome = true),
    )

    private fun parse(text: String, base: String = "JOD", label: (String) -> String = { it }) =
        VoiceEntry.parse(text, categories, base, today, label)

    private fun entry(text: String, base: String = "JOD", label: (String) -> String = { it }) =
        parse(text, base, label) ?: throw AssertionError("not an entry: $text")

    @Test
    fun `amount then what it was for, in the base currency, today`() {
        val e = entry("200 lunch cash")
        assertEquals(0, e.amount.compareTo(BigDecimal(200)))
        assertEquals("JOD", e.currency)
        assertEquals(TransactionType.EXPENSE, e.type)
        assertEquals("lunch", e.merchant)
        assertEquals("Food & Dining", e.category)
        assertEquals(today, e.date)
    }

    @Test
    fun `a named currency and day are taken out of the merchant`() {
        val e = entry("spent 45 dollars on coffee yesterday", base = "INR")
        assertEquals(0, e.amount.compareTo(BigDecimal(45)))
        assertEquals("USD", e.currency)
        assertEquals("coffee", e.merchant)
        assertEquals("Food & Dining", e.category)
        assertEquals(today.minusDays(1), e.date)
    }

    @Test
    fun `arabic with arabic-indic digits`() {
        val e = entry("دفعت ٥٠ ريال قهوة", base = "SAR")
        assertEquals(0, e.amount.compareTo(BigDecimal(50)))
        assertEquals("SAR", e.currency)
        assertEquals(TransactionType.EXPENSE, e.type)
        assertEquals("قهوة", e.merchant)
        assertEquals("Food & Dining", e.category)

        val groceries = entry("١٬٢٥٠ بقالة")
        assertEquals(0, groceries.amount.compareTo(BigDecimal(1250)))
        assertEquals("Groceries", groceries.category)
    }

    @Test
    fun `a category's own label in the user's language picks it`() {
        val e = entry("٢٠ نقل", label = { if (it == "Transportation") "نقل" else it })
        assertEquals("Transportation", e.category)
    }

    @Test
    fun `arabic words with the article or a preposition on them still find the category`() {
        assertEquals("Food & Dining", entry("دفعت ٥٠ ريال على القهوة").category)
        assertEquals("Transportation", entry("٣٠ بالتاكسي").category)
        assertEquals("Groceries", entry("٢٠٠ للبقالة").category)
        // Said bare, the label with "the" on it.
        assertEquals("Transportation", entry("٢٠ نقل", label = { if (it == "Transportation") "النقل" else it }).category)
    }

    @Test
    fun `money in is income`() {
        val salary = entry("received 5000 salary")
        assertEquals(TransactionType.INCOME, salary.type)
        assertEquals("salary", salary.merchant)
        assertEquals("Salary", salary.category)

        assertEquals(TransactionType.INCOME, entry("استلمت راتب ٣٠٠٠").type)
        // "Got paid", spoken.
        assertEquals(TransactionType.INCOME, entry("قبضت ٥٠٠ من الشغل").type)
        assertEquals(TransactionType.INCOME, entry("تقاضيت ٢٠٠٠ راتب").type)
    }

    @Test
    fun `a day's count is not a second amount`() {
        val e = entry("taxi 30 3 days ago")
        assertEquals(0, e.amount.compareTo(BigDecimal(30)))
        assertEquals("taxi", e.merchant)
        assertEquals("Transportation", e.category)
        assertEquals(today.minusDays(3), e.date)
    }

    @Test
    fun `scale words count`() {
        assertEquals(0, entry("15k rent").amount.compareTo(BigDecimal(15_000)))
    }

    @Test
    fun `words no rule knows leave the category to the user`() {
        val e = entry("200 something")
        assertEquals("something", e.merchant)
        assertNull(e.category)
        assertNull(entry("500").category)
    }

    @Test
    fun `questions are not entries`() {
        assertNull(parse("how much did I spend on lunch"))
        assertNull(parse("what did I spend 200 on"))
        assertNull(parse("lunch 200?"))
        assertNull(parse("كم صرفت على الأكل"))
        assertNull(parse("هل دفعت ٢٠٠ للإيجار"))
        assertNull(parse("صرفت ٢٠٠ على الأكل؟"))
    }

    @Test
    fun `plans are not entries`() {
        assertNull(parse("I will pay 500 rent tomorrow"))
        assertNull(parse("بكرة رح ادفع ١٠٠"))
    }

    @Test
    fun `two amounts are not one entry`() {
        assertNull(parse("lunch 200 and taxi 50"))
        assertNull(parse("غداء ٢٠٠ وتاكسي ٥٠"))
    }

    @Test
    fun `no amount, or a day it can't count, is not an entry`() {
        assertNull(parse("lunch"))
        assertNull(parse(""))
        assertNull(parse("taxi 30 last monday"))
        assertNull(parse("taxi 30 400 days ago"))
        assertNull(parse("taxi 30 60 weeks ago"))
        assertNull(parse("lunch 20 yesterday and today"))
    }
}
