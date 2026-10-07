package com.pennywiseai.tracker.data.mapper

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class DeferredBalanceRoutingTest {
    private val parsed = ParsedTransaction(amount = BigDecimal.ONE, type = TransactionType.EXPENSE,
        merchant = "Example Shop", reference = null, accountLast4 = "000", balance = BigDecimal.TEN,
        smsBody = "Synthetic transaction", sender = "TEST", timestamp = 0, bankName = "Example Bank")

    @Test fun skipsSourceBalanceAfterIncompatibleOrCrossBankMerge() {
        val saved = parsed.toEntity().copy(bankName = "Another Bank", accountNumber = "1999")
        assertNull(parsed.forDeferredBalance(saved))
        assertNull(parsed.forDeferredBalance(saved.copy(bankName = parsed.bankName)))
    }

    @Test fun confirmedAliasBalanceStillMatchesCurrentSavedAccount() {
        val key = com.pennywiseai.tracker.data.preferences.BankAccountMergeStore.mappingKey(parsed.bankName, "INR", "000")
        val resolved = com.pennywiseai.tracker.data.preferences.BankAccountMergeStore.resolve(parsed, mapOf(key to "2000"))
        val saved = parsed.toEntity().copy(accountNumber = "2000")
        assertEquals(resolved, resolved.forDeferredBalance(saved))
    }

    @Test fun skipsDeletedUnassignedAndCurrencyChangedTransactions() {
        val saved = parsed.toEntity()
        assertNull(parsed.forDeferredBalance(saved.copy(isDeleted = true)))
        assertNull(parsed.forDeferredBalance(saved.copy(accountNumber = null)))
        assertNull(parsed.forDeferredBalance(saved.copy(currency = "USD")))
    }

    @Test fun transferSelectionMovesBankAndSuffixWithoutChangingOtherLeg() {
        val tx = parsed.toEntity().copy(accountNumber = "3000", bankName = "Source Bank",
            fromAccount = "3000", fromBankName = "Source Bank", toAccount = "1000", toBankName = "Example Bank")
        val incoming = tx.withTransferAccount("2000", "Another Bank", incoming = true)
        assertEquals("2000", incoming.toAccount)
        assertEquals("Another Bank", incoming.toBankName)
        assertEquals("Source Bank", incoming.bankName)
        val outgoing = incoming.withTransferAccount("4000", "New Source Bank", incoming = false)
        assertEquals("4000", outgoing.fromAccount)
        assertEquals("New Source Bank", outgoing.fromBankName)
        assertEquals("4000", outgoing.accountNumber)
        assertEquals("New Source Bank", outgoing.bankName)
        assertEquals("Another Bank", outgoing.toBankName)
        val typed = outgoing.withTransferAccount("5000", null, incoming = true)
        assertNull(typed.toBankName)
        val cleared = typed.withTransferAccount(null, null, incoming = true)
        assertNull(cleared.toAccount)
        assertNull(cleared.toBankName)
        val clearedSource = outgoing.withTransferAccount(null, null, incoming = false)
        val replacement = clearedSource.withTransferAccount("6000", "Replacement Bank", incoming = false)
        assertEquals("6000", replacement.accountNumber)
        assertEquals("Replacement Bank", replacement.bankName)
        val incomingOwner = tx.copy(accountNumber = "1000", bankName = "Example Bank")
        val reselected = incomingOwner.withTransferAccount(null, null, incoming = true)
            .withTransferAccount("7000", "Replacement Bank", incoming = true)
        assertEquals("7000", reselected.accountNumber)
        assertEquals("Replacement Bank", reselected.bankName)
    }

    @Test fun cardSuffixRemainsAvailableForCardBinding() {
        val card = parsed.copy(isFromCard = true)
        assertEquals(card, card.forDeferredBalance(card.toEntity().copy(accountNumber = "1999")))
    }
}
