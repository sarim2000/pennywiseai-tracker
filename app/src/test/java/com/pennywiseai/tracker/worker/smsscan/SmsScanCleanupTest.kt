package com.pennywiseai.tracker.worker.smsscan

import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.LlmRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.data.repository.UnrecognizedSmsRepository
import com.pennywiseai.tracker.domain.repository.RuleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class SmsScanCleanupTest {

    private val transactions = mockk<TransactionRepository>(relaxed = true)
    private val balances = mockk<AccountBalanceRepository>(relaxed = true)
    private val rules = mockk<RuleRepository>(relaxed = true)
    private val llm = mockk<LlmRepository>(relaxed = true)
    private val database = mockk<PennyWiseDatabase>()
    private val cleanup = SmsScanCleanup(
        unrecognizedSmsRepository = mockk<UnrecognizedSmsRepository>(relaxed = true),
        transactionRepository = transactions,
        accountBalanceRepository = balances,
        ruleRepository = rules,
        llmRepository = llm,
        database = database,
    )

    @Before
    fun setUp() {
        mockkStatic(ROOM_COMPAT)
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
        coEvery { transactions.getActiveSmsDuplicateRows() } returns emptyList()
        coEvery { transactions.findGPayDuplicatesForCleanup() } returns emptyMap()
        coEvery { transactions.deleteDuplicate(any(), any()) } returns true
        coEvery { balances.getAccountLast4sForBank(any()) } returns emptyList()
    }

    @After
    fun tearDown() = unmockkStatic(ROOM_COMPAT)

    @Test
    fun `one SMS stored twice keeps the oldest row and repairs its amount`() = runBlocking {
        val oldest = transactionRow(id = 1L, amount = "500.00")
        val duplicate = transactionRow(id = 2L, amount = "500.00")
        val newest = transactionRow(id = 3L, amount = "500.75")
        coEvery { transactions.getActiveSmsDuplicateRows() } returns listOf(newest, oldest, duplicate)

        cleanup.run(savedAny = false)

        coVerify(exactly = 1) { transactions.correctParsedAmount(oldest, newest) }
        coVerify(exactly = 1) { transactions.deleteDuplicate(2L, 1L) }
        coVerify(exactly = 1) { transactions.deleteDuplicate(3L, 1L) }
        coVerify(exactly = 0) { transactions.deleteDuplicate(1L, any()) }
        coVerify(exactly = 1) { balances.deleteBalancesForTransaction(2L) }
        coVerify(exactly = 1) { rules.deleteRuleApplicationsForTransaction("3") }
    }

    @Test
    fun `a duplicate that cannot be removed is kept`() = runBlocking {
        coEvery { transactions.getActiveSmsDuplicateRows() } returns listOf(transactionRow(1L), transactionRow(2L))
        coEvery { transactions.deleteDuplicate(2L, 1L) } returns false

        cleanup.run(savedAny = false)

        coVerify(exactly = 0) { balances.deleteBalancesForTransaction(any()) }
        coVerify(exactly = 0) { rules.deleteRuleApplicationsForTransaction(any()) }
    }

    @Test
    fun `phantom GPay partner-bank accounts are swept`() = runBlocking {
        coEvery { balances.getAccountLast4sForBank("State Bank of India") } returns listOf("1111", "2222")

        cleanup.run(savedAny = false)

        coVerify(exactly = 1) { balances.deletePhantomGPayAccountIfQualifies("State Bank of India", "1111") }
        coVerify(exactly = 1) { balances.deletePhantomGPayAccountIfQualifies("State Bank of India", "2222") }
    }

    @Test
    fun `the AI prompt is refreshed only when something was saved`() = runBlocking {
        cleanup.run(savedAny = false)
        coVerify(exactly = 0) { llm.updateSystemPrompt() }

        cleanup.run(savedAny = true)
        coVerify(exactly = 1) { llm.updateSystemPrompt() }
    }
}
