package com.pennywiseai.tracker.navigation

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import com.pennywiseai.tracker.data.preferences.LearnedSendersStore
import com.pennywiseai.tracker.domain.model.Suggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** The Add form a suggested transaction opens (#994). Synthetic data only. */
class SuggestionDraftTest {

    private val receivedAt = LocalDateTime.of(2024, 3, 5, 14, 30, 15)
    private val learned = LearnedSendersStore.Account(bankName = "Test Bank", accountLast4 = "1234", currency = "USD")

    private fun suggestion(
        accountLast4: String? = "1234",
        currency: String = "USD",
        merchant: String? = "Test Store",
        learnedAccount: LearnedSendersStore.Account? = learned,
    ) = Suggestion(
        sms = UnrecognizedSmsEntity(id = 42, sender = "TEST-SENDER", smsBody = "Test message", receivedAt = receivedAt),
        transaction = ParsedTransaction(
            amount = BigDecimal("12.500"),
            type = TransactionType.EXPENSE,
            merchant = merchant,
            reference = null,
            accountLast4 = accountLast4,
            balance = null,
            smsBody = "Test message",
            sender = "TEST-SENDER",
            timestamp = 0L,
            bankName = "Parsed Bank",
            currency = currency,
        ),
        learnedAccount = learnedAccount,
    )

    @Test
    fun `learned account fills bank and account when last four and currency match`() {
        val draft = suggestion().draft()
        assertEquals("Test Bank", draft.bankName)
        assertEquals("1234", draft.accountLast4)
    }

    @Test
    fun `learned account is used when the SMS names no account`() {
        val draft = suggestion(accountLast4 = null).draft()
        assertEquals("Test Bank", draft.bankName)
        assertEquals("1234", draft.accountLast4)
    }

    @Test
    fun `conflicting last four drops the learned account and keeps the SMS digits`() {
        val draft = suggestion(accountLast4 = "9999").draft()
        assertNull(draft.bankName)
        assertEquals("9999", draft.accountLast4)
    }

    @Test
    fun `currency mismatch drops the learned account`() {
        val draft = suggestion(currency = "EUR").draft()
        assertNull(draft.bankName)
        assertEquals("1234", draft.accountLast4)
        assertEquals("EUR", draft.currency)
    }

    @Test
    fun `no learned account leaves the bank empty`() {
        val draft = suggestion(learnedAccount = null).draft()
        assertNull(draft.bankName)
        assertEquals("1234", draft.accountLast4)
    }

    @Test
    fun `missing merchant stays empty`() {
        assertNull(suggestion(merchant = null).draft().merchant)
    }

    @Test
    fun `amount, type, currency, SMS time and SMS id are carried over`() {
        val draft = suggestion().draft()
        assertEquals("12.500", draft.amount)
        assertEquals("Test Store", draft.merchant)
        assertEquals("EXPENSE", draft.type)
        assertEquals("USD", draft.currency)
        assertEquals(receivedAt.toString(), draft.dateTime)
        assertEquals(receivedAt, LocalDateTime.parse(draft.dateTime))
        assertEquals(42L, draft.unrecognizedSmsId)
    }
}
