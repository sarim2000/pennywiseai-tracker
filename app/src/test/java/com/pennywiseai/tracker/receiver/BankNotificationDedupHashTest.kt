package com.pennywiseai.tracker.receiver

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.Test

class BankNotificationDedupHashTest {

    private fun tx(body: String, timestamp: Long) = ParsedTransaction(
        amount = BigDecimal("500"),
        type = TransactionType.EXPENSE,
        merchant = "Store",
        reference = null,
        accountLast4 = null,
        balance = null,
        smsBody = body,
        sender = "FaysalBank",
        timestamp = timestamp,
        bankName = "FaysalBank"
    )

    @Test
    fun `two legit charges same amount different bodies produce distinct hashes`() {
        val t0 = 1_700_000_000_000L
        val first = tx("Rs 500 spent at Coffee Co card x1234", t0)
        val second = tx("Rs 500 spent at Grocery Co card x5678", t0 + 60_000)
        assertNotEquals(first.generateTransactionId(), second.generateTransactionId())
    }

    @Test
    fun `carrier redelivery identical body produces identical hash`() {
        val body = "Rs 500 spent at Coffee Co card x1234"
        assertEquals(tx(body, 1_700_000_000_000L).generateTransactionId(),
                     tx(body, 1_700_005_000_000L).generateTransactionId())
    }
}
