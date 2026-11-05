package com.pennywiseai.tracker.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ChatMessageFactsTest {
    private fun assertAmount(expected: String, value: Any?) =
        assertEquals(value.toString(), 0, BigDecimal(expected).compareTo(ChatMessageFacts.parseAmount(value)))

    @Test
    fun `amount arguments are read in any script and form`() {
        assertAmount("120", 120)
        assertAmount("15000", -15000.0) // The model writes a spend as negative.
        assertAmount("1500", "١٬٥٠٠")
        assertAmount("1500", "१५००")
        assertAmount("1500", "1.5k")
        assertAmount("150000", "1.5 lakh")
        assertAmount("150000", "1,50,000")
        assertAmount("1200", "₹1,200")
        assertAmount("1234567", "1,234,567")
        assertAmount("1234567", "١٬٢٣٤٬٥٦٧")
        assertAmount("500", "Rs. 500")
    }

    @Test
    fun `unusable amounts are refused rather than guessed`() {
        assertNull(ChatMessageFacts.parseAmount("12,50")) // 12.5 or 1250?
        assertNull(ChatMessageFacts.parseAmount("15 and 20"))
        assertNull(ChatMessageFacts.parseAmount(""))
        assertNull(ChatMessageFacts.parseAmount(0))
        assertNull(ChatMessageFacts.parseAmount(Double.NaN))
        assertNull(ChatMessageFacts.parseAmount(null))
        assertAmount("500", "500 kg") // "kg" is not "k".
    }

    private fun states(message: String, amount: String) = ChatMessageFacts.statesAmount(message, BigDecimal(amount))

    @Test
    fun `an amount the message contains passes the guard`() {
        assertTrue(states("coffee 120 at Starbucks", "120.00"))
        assertTrue(states("قهوة ١٥ ريال", "15"))
        assertTrue(states("rent 1,500", "1500"))
        assertTrue(states("bonus 1.5k", "1500"))
        assertTrue(states("2 lakh ka loan", "200000"))
        assertTrue(states("-300 for lunch", "300"))
    }

    @Test
    fun `an amount the message lacks is flagged`() {
        assertFalse(states("coffee 120 and lunch 300", "420")) // Merged.
        assertFalse(states("coffee 120", "1200"))
        assertFalse(states("coffee fifty", "50"))
        assertFalse(states("rent 15k", "15")) // The scale is part of the number.
        assertFalse(states("الإيجار ١٥ ألف", "15"))
        assertFalse(states("किराया १५ हजार", "15"))
        assertTrue(states("الإيجار ١٥ ألف", "15000"))
        assertFalse(states("coffee 12,50", "1250")) // Ambiguous: checked, whichever reading.
        assertFalse(states("coffee 12,50", "12.5"))
        assertTrue(states("coffee 120, lunch 300", "120"))
        assertFalse(states("coffee .50", "50")) // Half a unit, not 50.
        assertTrue(states("coffee .50", "0.5"))
        assertTrue(states("Rs.500 for lunch", "500"))
    }

    @Test
    fun `the guard is not a guarantee`() {
        assertTrue(states("قهوة 15 وغدا 40 وتاكسي 25", "40")) // Right digits, maybe the wrong item.
        assertTrue(states("coffee 15, lunch 40, total 55", "55")) // A stated total.
        assertTrue(states("Shop 27 lunch 90", "27")) // A digit from a name.
    }

    private fun currency(message: String, model: String?, base: String = "INR") = ChatMessageFacts.currency(message, model, base)

    @Test
    fun `an invented currency falls back to the base`() {
        assertEquals("INR", currency("coffee 120 at Starbucks", "USD"))
        assertEquals("SAR", currency("قهوة 15", "DIRHAMS", "SAR"))
        assertEquals("INR", currency("coffee 120", ""))
        assertEquals("INR", currency("coffee 120", "((("))
    }

    @Test
    fun `a named currency counts even when the model missed it`() {
        assertEquals("USD", currency("paid $5 for coffee", ""))
        assertEquals("AED", currency("hotel in Dubai 450 dirhams", "DIRHAMS"))
        assertEquals("USD", currency("اشتريت كتاب بـ 20 دولار", "SAR", "SAR"))
        assertEquals("AED", currency("دفعت 100 درهم في مول دبي", null, "SAR"))
        assertEquals("EUR", currency("amazon 25 euros", "EUR"))
        assertEquals("USD", currency("5 डॉलर की कॉफी", ""))
    }

    @Test
    fun `a family word means the user's own currency`() {
        assertEquals("EGP", currency("دفعت 300 جنيه", "", "EGP"))
        assertEquals("JOD", currency("7 دنانير", "", "JOD"))
        assertEquals("KWD", currency("7 دنانير", "JOD", "KWD"))
        assertEquals("CAD", currency("20 dollars", "USD", "CAD"))
        assertEquals("OMR", currency("5 ريال", "SAR", "OMR"))
    }

    @Test
    fun `a code or a one-meaning symbol beats the user's own currency`() {
        assertEquals("USD", currency("coffee USD 5", "", "CAD"))
        assertEquals("USD", currency("coffee usd 5", "", "CAD"))
        assertEquals("INR", currency("coffee ₹120", "INR", "PKR"))
        assertEquals("EUR", currency("coffee €5", ""))
        assertEquals("INR", currency("I was mad, coffee 50", "MAD"))
        assertEquals("USD", currency("coffee USD $5", "", "CAD")) // A redundant symbol changes nothing.
        assertEquals("INR", currency("₹120 rupees", "PKR", "PKR"))
    }

    @Test
    fun `with two currencies named the model picks between them`() {
        val message = "lunch 300 rupees and souvenir 20 dollars"
        assertEquals("USD", currency(message, "USD"))
        assertEquals("INR", currency(message, "INR"))
        assertEquals("INR", currency(message, ""))
        assertEquals("INR", currency(message, "EUR"))
    }

    @Test
    fun `a code counts only as an upper-case real currency`() {
        assertEquals("CHF", currency("hotel 50 CHF", "CHF"))
        assertEquals("INR", currency("coffee cup 50", "cup"))
        assertEquals("INR", currency("KFC 50", "KFC"))
    }

    private fun days(message: String, model: Any?) = ChatMessageFacts.daysAgo(message, model)

    @Test
    fun `an invented date falls back to today`() {
        assertEquals(0, days("coffee 120", 3))
        assertEquals(0, days("coffee 120", null))
        assertEquals(0, days("निकला 50", 1)) // "कल" inside a longer word isn't "yesterday".
        assertEquals(0, days("خامس 50", 1)) // Nor is "امس" inside this one.
        assertEquals(0, days("paid 50 at 24/7", 3)) // A shop's hours aren't a date.
        assertEquals(0, days("May I record coffee 50?", 3)) // Nor is a word that spells a month.
        assertEquals(0, days("coffee 50 at May Cafe", 3))
    }

    @Test
    fun `a stated day counts even when the model missed it`() {
        assertEquals(1, days("yesterday I paid 300 for lunch", 0))
        assertEquals(3, days("3 days ago uber 250", 0))
        assertEquals(1, days("امس دفعت 50 ريال بنزين", 0))
        assertEquals(1, days("وامس قهوة 5", 0))
        assertEquals(2, days("قبل يومين اشتريت قهوة بـ 12", 1))
        assertEquals(3, days("قبل ٣ أيام قهوة 12", 1))
        assertEquals(1, days("kal 200 ka petrol dalwaya", 0))
        assertEquals(1, days("कल 200 का पेट्रोल", 0))
        assertEquals(2, days("day before yesterday lunch 50", 1))
        assertEquals(2, days("परसों 50 दिए", 0))
        assertEquals(7, days("a week ago lunch 50", 0))
        assertEquals(14, days("قبل أسبوعين قهوة 15", 14))
        assertEquals(14, days("two weeks ago lunch 50", 0))
        assertEquals(14, days("2 weeks ago lunch 50", 0))
        assertEquals(84, days("coffee 50 at Starbucks 12 weeks ago", 84)) // Not "2 weeks ago".
        assertEquals(84, days("coffee 50 at Starbucks 12 weeks ago", 0))
        assertEquals(21, days("قبل 3 أسابيع قهوة 15", 0))
        assertEquals(14, days("2 hafte pehle chai 50", 0))
        assertEquals(0, days("today lunch 50", 2))
    }

    @Test
    fun `two stated days leave the choice to the model`() {
        val message = "coffee 100 yesterday and lunch 200 today"
        assertEquals(1, days(message, 1))
        assertEquals(0, days(message, 0))
        assertEquals(0, days(message, 5))
    }

    @Test
    fun `a day the code can't count takes the model's reading`() {
        assertEquals(2, days("paid rent on monday 500", 2))
        assertEquals(4, days("يوم الأحد دفعت 50", 4))
        assertEquals(4, days("coffee 50 on November 1", 4))
        assertEquals(4, days("coffee 50 on 1st nov", 4))
        assertEquals(4, days("قهوة 50 في 1 نوفمبر", 4))
        assertEquals(4, days("1 नवंबर को कॉफी 50", 4))
        assertEquals(4, days("coffee 50 on 1/11", 4))
        assertEquals(4, days("coffee 50 1/11/2026", 4))
        assertEquals(90, days("3 months ago lunch 50", 90))
        assertEquals(30, days("دفعت 50 الشهر الماضي", 30))
        assertEquals(0, days("99999999999 weeks ago lunch 50", 3)) // Absurd, and it must not overflow.
        assertEquals(0, days("paid rent on monday 500", -1))
        assertEquals(0, days("paid rent on monday 500", 9999))
    }

    @Test
    fun `states a day`() {
        assertTrue(ChatMessageFacts.statesADay("delete the tea from yesterday"))
        assertTrue(ChatMessageFacts.statesADay("احذف القهوة اليوم"))
        assertTrue(ChatMessageFacts.statesADay("the rent on monday"))
        assertFalse(ChatMessageFacts.statesADay("delete the tea"))
    }
}
