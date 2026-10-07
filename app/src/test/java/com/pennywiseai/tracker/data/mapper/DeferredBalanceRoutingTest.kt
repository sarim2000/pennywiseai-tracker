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

    @Test fun bankRuleRoutesInferredBalanceToSavedAccount() {
        val saved = parsed.toEntity().copy(bankName = "Another Bank")
        for (input in listOf(parsed, parsed.copy(balance = null))) {
            val queued = requireNotNull(input.atEnqueuedAccount(input, saved))
            assertEquals("Another Bank", queued.account.bankName)
            assertNull(queued.account.balance)
            assertEquals(BigDecimal.ONE, queued.amount)
            assertEquals(queued.account, queued.forSavedTransaction(queued.account, saved))
            assertNull(queued.forSavedTransaction(queued.account, saved.copy(bankName = "Third Bank")))
        }
    }

    @Test fun bankRuleUsesSavedTypeAndSkipsClearedBank() {
        val saved = parsed.toEntity().copy(bankName = "Another Bank",
            transactionType = com.pennywiseai.tracker.data.database.entity.TransactionType.INCOME)
        val queued = requireNotNull(parsed.atEnqueuedAccount(parsed, saved))
        assertEquals(TransactionType.INCOME, queued.type)
        assertNull(parsed.atEnqueuedAccount(parsed, saved.copy(bankName = null)))
    }

    @Test fun enqueueKeepsOriginalMaskForLaterConfirmedMerge() {
        val store = com.pennywiseai.tracker.data.preferences.BankAccountMergeStore
        val key = store.mappingKey(parsed.bankName, "INR", "000")
        val first = store.resolve(parsed, mapOf(key to "1000"))
        val queued = requireNotNull(parsed.atEnqueuedAccount(first, first.toEntity()))
        assertEquals("000", queued.account.accountLast4)
        assertEquals(BigDecimal.TEN, queued.account.balance)
        val latest = store.resolve(queued.account, mapOf(key to "2000"))
        assertEquals(latest, latest.forDeferredBalance(first.toEntity().copy(accountNumber = "2000")))
    }

    @Test fun creditRuleStillResolvesOriginalBankMask() {
        val store = com.pennywiseai.tracker.data.preferences.BankAccountMergeStore
        val key = store.mappingKey(parsed.bankName, "INR", "000")
        val mappings = mapOf(key to "1000")
        val resolved = store.resolve(parsed, mappings)
        val saved = resolved.toEntity().copy(
            transactionType = com.pennywiseai.tracker.data.database.entity.TransactionType.CREDIT)
        val queued = requireNotNull(parsed.atEnqueuedAccount(resolved, saved))
        val routed = requireNotNull(queued.forSavedTransaction(store.resolve(queued.account, mappings), saved))
        assertEquals("1000", routed.accountLast4)
        assertEquals(TransactionType.CREDIT, routed.type)
        assertEquals(BigDecimal.TEN, routed.balance)
    }

    @Test fun cardRulesPreserveIssuerAndBindingIdentity() {
        val card = parsed.copy(isFromCard = true, accountLast4 = "1000")
        for (bank in listOf("Another Bank", null)) {
            val saved = card.toEntity().copy(bankName = bank, accountNumber = "2000",
                transactionType = com.pennywiseai.tracker.data.database.entity.TransactionType.INCOME)
            val queued = requireNotNull(card.atEnqueuedAccount(card, saved))
            val routed = requireNotNull(queued.forSavedTransaction(queued.account, saved))
            assertEquals("Example Bank", routed.bankName)
            assertEquals("1000", routed.accountLast4)
            assertEquals(BigDecimal.TEN, routed.balance)
            assertEquals(TransactionType.INCOME, routed.type)
        }
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
