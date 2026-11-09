package com.pennywiseai.tracker.presentation.unrecognized

import com.pennywiseai.parser.core.AmountFormat
import com.pennywiseai.parser.core.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Tapping through a made-up message on the Teach sheet (#279). */
class TeachStateTest {
    private val body = "Payment SAR 50.00 at Juice Bar Riyadh card 1234. Balance SAR 900.00"

    /** Nothing guessed: a message with no direction word or priced amount starts empty. */
    private val blank = TeachState.forMessage("AD-ZQTBNK", body, 1L).copy(
        active = TeachField.AMOUNT, amount = null, merchant = null, account = null, type = null, currency = null
    )

    private fun TeachState.tapWord(word: String) = tap(body.indexOf(word))

    private fun TeachState.text(range: IntRange?) = range?.let { body.substring(it.first, it.last + 1) }

    @Test
    fun `tapping the amount reads its currency and moves on to the merchant`() {
        val state = blank.tapWord("50.00")
        assertEquals("50.00", state.amountText)
        assertEquals("SAR", state.currency)
        assertEquals(0, BigDecimal("50.00").compareTo(state.amountValue))
        assertEquals(TeachField.MERCHANT, state.active)
    }

    @Test
    fun `a word is not an amount`() {
        assertNull(blank.tapWord("Payment").amount)
    }

    @Test
    fun `a tap reported just past the last character still counts`() {
        val range = body.indexOf("50.00") until body.indexOf("50.00") + 5
        assertEquals(range, blank.tap(range.last + 1).amount)
    }

    @Test
    fun `a merchant grows word by word and shrinks from either end`() {
        val start = blank.copy(active = TeachField.MERCHANT).tapWord("Juice")
        assertEquals("Juice", start.merchantName)
        val grown = start.tapWord("Bar").tapWord("Riyadh")
        assertEquals("Juice Bar Riyadh", grown.merchantName)
        assertEquals("Bar Riyadh", grown.tapWord("Juice").merchantName)
        assertEquals("Juice Bar", grown.tapWord("Riyadh").merchantName)
        assertEquals("Bar", grown.tapWord("Bar").merchantName)
        assertEquals("", start.tapWord("Juice").merchantName)
        assertEquals("Payment", start.tapWord("Payment").merchantName)
    }

    @Test
    fun `tapping what another field holds goes to that field and changes nothing`() {
        val state = blank.tapWord("50.00")
        assertEquals(TeachField.MERCHANT, state.active)
        val back = state.tapWord("50.00")
        assertEquals(TeachField.AMOUNT, back.active)
        assertEquals("50.00", back.amountText)
    }

    @Test
    fun `the account takes digits and lets go when tapped again`() {
        val state = blank.copy(active = TeachField.ACCOUNT)
        assertNull(state.tapWord("card").account)
        val set = state.tapWord("1234")
        assertEquals("1234", set.accountDigits)
        assertNull(set.tapWord("1234").account)
    }

    @Test
    fun `save asks for one thing at a time`() {
        assertEquals(TeachMissing.AMOUNT, blank.missing)
        val noDirection = blank.tapWord("50.00")
        assertEquals(TeachMissing.DIRECTION, noDirection.missing)
        assertNull(noDirection.copy(type = TransactionType.EXPENSE).missing)
        val noCurrency = blank.copy(amount = blank.spans.first { it.isNumber && "50" in body.substring(it.range) }.range)
        assertEquals(TeachMissing.CURRENCY, noCurrency.missing)
    }

    @Test
    fun `a three-decimal amount waits to be told, and nothing is shown until then`() {
        val kwd = "Paid KWD 1.234 for Rent"
        val state = TeachState.forMessage("AD-ZQTBNK", kwd, 1L).tap(kwd.indexOf("1.234"))
        assertTrue(state.isFormatAmbiguous)
        assertNull(state.amountValue)
        assertEquals(TeachMissing.FORMAT, state.missing)
        val told = state.copy(amountFormat = AmountFormat.DOT_DECIMAL)
        assertFalse(told.isFormatAmbiguous)
        assertTrue("the choice stays open to change", told.canChooseFormat)
        assertEquals(0, BigDecimal("1.234").compareTo(told.amountValue))
    }

    @Test
    fun `picking another amount forgets the format chosen for the old one`() {
        val state = blank.tapWord("50.00").copy(amountFormat = AmountFormat.COMMA_DECIMAL)
        assertNull(state.copy(active = TeachField.AMOUNT).tap(body.indexOf("1234")).amountFormat)
    }

    @Test
    fun `opens on what the message already says`() {
        val text = "Purchased SAR 50.00 at Juice Bar card ending 1234. Balance SAR 900.00"
        val state = TeachState.forMessage("AD-ZQTBNK", text, 1L)
        assertEquals("50.00", state.amountText)
        assertEquals("SAR", state.currency)
        assertEquals(TransactionType.EXPENSE, state.type)
        assertEquals("of the balance", null, state.amountSource?.takeIf { "900" in it })
    }
}
