package com.pennywiseai.tracker.data.statement

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.model.rule.ActionType
import com.pennywiseai.tracker.domain.model.rule.ConditionOperator
import com.pennywiseai.tracker.domain.model.rule.RuleAction
import com.pennywiseai.tracker.domain.model.rule.RuleCondition
import com.pennywiseai.tracker.domain.model.rule.TransactionField
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import com.pennywiseai.tracker.domain.service.RuleEngine
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import com.pennywiseai.parser.core.TransactionType as ParsedTransactionType

private const val ROOM_COMPAT = "com.pennywiseai.tracker.data.database.RoomCompatKt"

/** A commit pins and re-derives the accounts its rows land on once rules have moved them. */
class ImportStatementCommitTest {
    private val transactions = mockk<TransactionRepository>(relaxed = true) {
        coEvery { getTransactionsByHashes(any()) } returns emptyList()
        coEvery { getTransactionsBetweenDatesList(any(), any()) } returns emptyList()
        coEvery { insertTransactions(any()) } answers { firstArg<List<TransactionEntity>>().indices.map { 10L + it } }
    }
    private val balances = mockk<AccountBalanceRepository>(relaxed = true)
    private val database = mockk<PennyWiseDatabase>()
    private val toWallet = TransactionRule(
        name = "Coffee to wallet",
        conditions = listOf(RuleCondition(TransactionField.MERCHANT, ConditionOperator.CONTAINS, "Coffee")),
        actions = listOf(RuleAction(TransactionField.BANK_NAME, ActionType.SET, "Cash Wallet"))
    )
    private val blockSpam = TransactionRule(
        name = "Block spam",
        conditions = listOf(RuleCondition(TransactionField.MERCHANT, ConditionOperator.CONTAINS, "Spam")),
        actions = listOf(RuleAction(TransactionField.CATEGORY, ActionType.BLOCK, ""))
    )
    private val importRules = mockk<ImportRulesSource>(relaxed = true) {
        coEvery { load() } returns ImportRules(emptyMap(), emptyMap(), listOf(toWallet, blockSpam), RuleEngine())
    }
    private val useCase = ImportStatementUseCase(transactions, balances, mockk(relaxed = true), database, importRules, mockk())

    @Before
    fun setUp() {
        mockkStatic(ROOM_COMPAT)
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    @After
    fun tearDown() = unmockkStatic(ROOM_COMPAT)

    private fun row(merchant: String, hash: String, bank: String = "Sample Bank") = ParsedTransaction(
        amount = BigDecimal("120.00"),
        type = ParsedTransactionType.EXPENSE,
        merchant = merchant,
        reference = null,
        accountLast4 = "2468",
        balance = null,
        smsBody = "$merchant 120.00",
        sender = StatementTableParser.STATEMENT_SENDER,
        timestamp = LocalDateTime.of(2026, 1, 15, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        bankName = bank,
        transactionHash = hash,
        currency = "INR"
    )

    @Test
    fun `an account a rule moves a row to is pinned before the insert and recomputed after`() = runBlocking {
        val pinned = mutableListOf<List<String?>>()
        coEvery { balances.pinManualOpenings(*anyVararg()) } answers {
            pinned += (args.first() as Array<*>).map { (it as TransactionEntity).bankName }
        }

        val result = useCase.commit(
            listOf(row("Coffee Hut", "a", bank = "Old Bank"), row("Book Nook", "b"), row("Spam Shop", "c", bank = "Spare Bank")),
            keep = setOf(0, 1, 2)
        )

        assertEquals(2, result.imported)
        coVerifyOrder {
            balances.pinManualOpenings(*anyVararg())
            transactions.insertTransactions(any())
            balances.recomputeManualBalances(
                match { rows -> rows.map { it.bankName } == listOf("Cash Wallet", "Sample Bank") }
            )
        }
        // Once, as the rules left the rows: neither the account the moved row came
        // from nor the blocked row's account gets an opening for a row it never got.
        assertEquals(listOf(listOf("Cash Wallet", "Sample Bank")), pinned)
    }
}
