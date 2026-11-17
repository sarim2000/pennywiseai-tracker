package com.pennywiseai.tracker.data.preferences

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** A rebuild's saved account fields read back as written, so a later scan can restore them. */
class RebuildAccountFieldsStoreTest {

    private val card = AccountBalanceEntity(
        bankName = "Test Bank", accountLast4 = "0000", balance = BigDecimal("900"),
        timestamp = LocalDateTime.of(2026, 3, 20, 10, 0), isCreditCard = true, smsSource = "Sample message",
        statementDay = 12, cardDueDay = 2, cardBillAmount = BigDecimal("650.25"),
        cardBillAsOf = LocalDateTime.of(2026, 3, 14, 9, 0),
        anchorAmount = BigDecimal("1000"), anchorAt = LocalDateTime.of(2026, 1, 1, 8, 0), anchorSource = "MANUAL",
    )
    private val plain = card.copy(
        accountLast4 = "1111", isCreditCard = false, statementDay = null, cardDueDay = null, cardBillAmount = null,
        cardBillAsOf = null, anchorAmount = null, anchorAt = null, anchorSource = null,
    )

    @Test
    fun `a card's bill and anchor survive the trip to disk`() {
        val back = RebuildAccountFieldsStore.decode(RebuildAccountFieldsStore.encode(listOf(card))).single()

        assertEquals(card.copy(smsSource = null), back)
    }

    @Test
    fun `only accounts with something set are kept`() {
        assertEquals(listOf("0000"), RebuildAccountFieldsStore.decode(RebuildAccountFieldsStore.encode(listOf(card, plain))).map { it.accountLast4 })
        assertNull(RebuildAccountFieldsStore.encode(listOf(plain)))
    }

    @Test
    fun `nothing saved, or unreadable, is nothing to restore`() {
        assertTrue(RebuildAccountFieldsStore.decode(null).isEmpty())
        assertTrue(RebuildAccountFieldsStore.decode("not json").isEmpty())
    }
}
