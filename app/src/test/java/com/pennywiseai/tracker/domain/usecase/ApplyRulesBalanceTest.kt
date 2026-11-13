package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.model.rule.ActionType
import com.pennywiseai.tracker.domain.model.rule.FieldModification
import com.pennywiseai.tracker.domain.model.rule.RuleApplication
import com.pennywiseai.tracker.domain.model.rule.TransactionField
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleEngine
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** A rule moving a past transfer's leg moves the balances too, as a manual edit does (#1068). */
class ApplyRulesBalanceTest {
    private val transactions = mockk<TransactionRepository>(relaxed = true)
    private val engine = mockk<RuleEngine>()
    private val tags = mockk<TagRepository>(relaxed = true)
    private val balances = mockk<AccountBalanceRepository>(relaxed = true)
    private val useCase = ApplyRulesToPastTransactionsUseCase(
        transactions,
        mockk<RuleRepository>(relaxed = true),
        engine,
        tags,
        balances
    )

    @Test
    fun ruleChangingTransferLegShiftsBalances() = runBlocking {
        val original = TransactionEntity(
            id = 1,
            amount = BigDecimal("500"),
            merchantName = "Move",
            category = "Transfer",
            transactionType = TransactionType.TRANSFER,
            dateTime = LocalDateTime.of(2026, 10, 1, 10, 0),
            transactionHash = "h",
            fromBankName = "Cash",
            fromAccount = "",
            toBankName = "Wallet A",
            toAccount = ""
        )
        val updated = original.copy(toBankName = "Wallet B")
        val application = RuleApplication(
            ruleId = "r",
            ruleName = "To B",
            transactionId = "1",
            fieldsModified = listOf(
                FieldModification(TransactionField.TRANSFER_TO_ACCOUNT, "Wallet A||", "Wallet B||", ActionType.SET)
            )
        )
        coEvery { transactions.getAllTransactionsList() } returns listOf(original)
        every { tags.observeTransactionTagNames() } returns flowOf(emptyMap())
        every { engine.shouldBlockTransaction(any(), any(), any()) } returns null
        every { engine.evaluateRules(any(), any(), any()) } returns (updated to listOf(application))

        val result = useCase.applyAllActiveRulesToTransactions()

        assertEquals(1, result.totalUpdated)
        coVerifyOrder {
            balances.pinManualOpenings(original, updated)
            transactions.updateTransaction(updated)
            balances.reflectTransactionEdit(original, updated)
        }
    }
}
