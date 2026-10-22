package com.pennywiseai.shared.domain.chat

import com.pennywiseai.shared.data.model.SharedTransaction
import com.pennywiseai.shared.data.model.SharedTransactionType
import com.pennywiseai.shared.domain.usecase.FakeAccounts
import com.pennywiseai.shared.domain.usecase.FakeCategories
import com.pennywiseai.shared.domain.usecase.FakeTransactions
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DAY = 24L * 60 * 60 * 1000
private const val NOW = 1_792_000_000_000L // 2026-10-15 UTC

class ChatAssistantTest {
    private val transactions = FakeTransactions()
    private val assistant = ChatAssistant(transactions, FakeAccounts(), FakeCategories(), TimeZone.UTC) { NOW }

    private suspend fun seed(merchant: String, minor: Long, daysAgo: Int, currency: String = "INR", category: String = "Food & Dining") =
        transactions.insert(
            SharedTransaction(
                amountMinor = minor, merchantName = merchant, category = category,
                transactionType = SharedTransactionType.EXPENSE, occurredAtEpochMillis = NOW - daysAgo * DAY, currency = currency
            )
        )

    @Test
    fun addIsOnlyAProposalUntilConfirmed() = runTest {
        val result = assistant.proposeAdd(120.0, "Starbucks", "Food & Dining", "EXPENSE", "", "coffee 120 at starbucks", "INR")

        val add = assertIs<PendingChatAction.Add>(result.action)
        assertEquals(12_000L, add.draft.amountMinor)
        assertEquals("INR", add.draft.currency)
        assertTrue(transactions.rows.isEmpty(), "nothing is written by the proposal")

        assertNull(assistant.confirm(add))
        assertEquals(1, transactions.rows.size)
        assertEquals("Starbucks", transactions.rows.single().merchantName)
    }

    @Test
    fun theUsersWordsDecideIncomeOrExpense() = runTest {
        val spend = assistant.proposeAdd(500.0, "Landlord", "Salary", "INCOME", "", "paid rent 500", "INR").action
        assertEquals(SharedTransactionType.EXPENSE, assertIs<PendingChatAction.Add>(spend).draft.type)
        val income = assistant.proposeAdd(50_000.0, "Acme", "Salary", "EXPENSE", "", "got my salary", "INR").action
        assertEquals("Salary", assertIs<PendingChatAction.Add>(income).draft.category)
        assertNull(assistant.proposeAdd(0.0, "X", "", "EXPENSE", "", "zero", "INR").action)
    }

    @Test
    fun deleteFindsTheRowTheUserMeansAndWaitsForConfirm() = runTest {
        seed("Tea Stall", 2_000, daysAgo = 0)
        val id = seed("Tea Stall", 3_000, daysAgo = 1)

        val delete = assertIs<PendingChatAction.Delete>(assistant.proposeChange(true, "the tea", 30.0, 0, "", "").action)
        assertEquals(id, delete.transaction.id) // the model's wrong "today" is dropped; the amount holds
        assertFalse(transactions.rows.first { it.id == id }.isDeleted)

        assistant.confirm(delete)
        assertTrue(transactions.rows.first { it.id == id }.isDeleted)
    }

    @Test
    fun updateChangesOnlyWhatWasAsked() = runTest {
        val id = seed("Uber", 25_000, daysAgo = 1)
        val update = assertIs<PendingChatAction.Update>(assistant.proposeChange(false, "uber", 0.0, 1, "travel", "").action)
        assertEquals("Travel", update.newCategory)
        assertNull(update.newMerchant)

        assistant.confirm(update)
        assertEquals("Travel", transactions.rows.first { it.id == id }.category)
        assertEquals("Uber", transactions.rows.first { it.id == id }.merchantName)
    }

    @Test
    fun noMatchNoCard() = runTest {
        seed("Tea Stall", 2_000, daysAgo = 0)
        assertNull(assistant.proposeChange(true, "netflix", 0.0, -1, "", "").action)
    }

    @Test
    fun totalsStayPerCurrency() = runTest {
        seed("Swiggy", 45_000, daysAgo = 0)
        seed("Swiggy", 5_000, daysAgo = 1)
        seed("Starbucks", 1_000, daysAgo = 0, currency = "USD")

        assertEquals("INR 500.00, USD 10.00 on Food & Dining this month across 3 transactions.", assistant.spendingByCategory("food"))
        assertEquals("This month: INR 500.00, USD 10.00 spent, nothing received, 3 transactions.", assistant.monthSummary())
        assertEquals("Nothing spent on Groceries this month.", assistant.spendingByCategory("groceries"))
    }

    @Test
    fun finderTreatsAStatedAmountAsAHardFilterButLoosensIt() {
        val today = LocalDate(2026, 10, 15)
        val tea = SharedTransaction(id = 1, amountMinor = 2_000, merchantName = "Tea", category = "Food", transactionType = SharedTransactionType.EXPENSE, occurredAtEpochMillis = NOW)
        assertNull(ChatTransactionFinder.findBest(listOf(tea), "tea", 9_900, null, today, TimeZone.UTC))
        val match = assertNotNull(ChatTransactionFinder.findWithFallback(listOf(tea), "tea", 9_900, null, today, TimeZone.UTC))
        assertTrue(match.loosened)
    }

    @Test
    fun promptCapsLongListsAndBudgetUsesTheModelsContext() {
        assertEquals("Food, Travel, and 1 more", ChatPrompt.promptList(listOf("Food", "Travel", "Groceries"), 14))
        val prompt = ChatPrompt.compose("2026-10-15", "nothing", List(500) { "Category $it" }, emptyList(), emptyList())
        assertTrue(ChatPrompt.fitsBudget(listOf(prompt), "hi", contextSize = 4096))
        assertFalse(ChatPrompt.fitsBudget(listOf(prompt, "x".repeat(8000)), "hi", contextSize = 4096))
    }

    @Test
    fun aClaimedChangeWithoutACardIsCalledOut() {
        assertNotNull(ChatAssistant.nothingChangedNote("I've added the coffee for you.", proposedSomething = false))
        assertNull(ChatAssistant.nothingChangedNote("I've added the coffee for you.", proposedSomething = true))
        assertNull(ChatAssistant.nothingChangedNote("You spent INR 500.00 on food.", proposedSomething = false))
    }
}
