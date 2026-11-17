package com.pennywiseai.tracker.worker

import com.pennywiseai.tracker.data.database.dao.LoanDao
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.LoanRepository
import com.pennywiseai.tracker.utils.Money
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import com.pennywiseai.tracker.data.preferences.DAILY_SUMMARY_DEFAULT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** The evening recap's "Spent" is Home's, and it lands at 9 PM local. */
class DailySummaryWorkerTest {

    private fun tx(amount: String, type: TransactionType, currency: String = "JOD", impact: BudgetImpactType? = null) =
        TransactionEntity(
            id = 0, amount = BigDecimal(amount), merchantName = "M", category = "Others",
            transactionType = type, dateTime = LocalDateTime.now(), currency = currency,
            transactionHash = "h$amount$type", budgetImpactType = impact
        )

    @Test
    fun `the recap is off until the user turns it on`() {
        // Owner decision on #968: opt-in from Settings → Notifications.
        assertFalse(DAILY_SUMMARY_DEFAULT)
    }

    @Test
    fun `a loan forgiven today is spent today, even with no other rows`() {
        // Home adds forgiven lent balances back to "Spent" (#938); the recap must too,
        // and must not stay quiet on a day whose only spend was the write-off.
        assertEquals(
            mapOf("JOD" to Money(BigDecimal("10"), "JOD")),
            DailySummaryWorker.daySpent(emptyList(), creditAsExpense = false, settlementLoss = mapOf("JOD" to BigDecimal("10")))
        )
    }

    @Test
    fun `spend nets refunds per currency and adds that currency's loan loss`() {
        val spent = DailySummaryWorker.daySpent(
            listOf(
                tx("30", TransactionType.EXPENSE),
                tx("5", TransactionType.INCOME, impact = BudgetImpactType.DEDUCT_SPENT),
                tx("8", TransactionType.CREDIT),
                tx("100", TransactionType.INCOME),
                tx("12", TransactionType.EXPENSE, currency = "USD")
            ),
            creditAsExpense = true,
            settlementLoss = mapOf("JOD" to BigDecimal("2"))
        )!!
        assertEquals(0, BigDecimal("35").compareTo(spent.getValue("JOD").amount))
        assertEquals(0, BigDecimal("12").compareTo(spent.getValue("USD").amount))
    }

    @Test
    fun `a day with nothing is quiet`() {
        assertNull(DailySummaryWorker.daySpent(emptyList(), creditAsExpense = false, settlementLoss = emptyMap()))
    }

    @Test
    fun `the next run is 9 PM local, across a DST change`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // Saturday before the October switch from CEST (+2) to CET (+1).
        val evening = ZonedDateTime.of(2026, 10, 24, 22, 0, 0, 0, berlin)
        val next = DailySummaryWorker.nextRun(evening)
        assertEquals(ZonedDateTime.of(2026, 10, 25, 21, 0, 0, 0, berlin), next)
        assertEquals(21, next.hour)
        assertEquals(
            ZonedDateTime.of(2026, 10, 24, 21, 0, 0, 0, berlin),
            DailySummaryWorker.nextRun(evening.withHour(9))
        )
    }

    @Test
    fun `settlement loss counts only loans whose source passes the filter`() = runBlocking {
        val loanDao = mockk<LoanDao>(relaxed = true)
        val repo = LoanRepository(loanDao, mockk(relaxed = true), mockk(relaxed = true))
        fun loan(id: Long) = LoanEntity(
            id = id, personName = "Pal", direction = LoanDirection.LENT, originalAmount = BigDecimal("10"),
            remainingAmount = BigDecimal.ZERO, currency = "JOD", status = LoanStatus.SETTLED
        )
        val mine = tx("10", TransactionType.EXPENSE).copy(id = 1, accountNumber = "1111")
        val theirs = tx("10", TransactionType.EXPENSE).copy(id = 2, accountNumber = "2222")
        coEvery { loanDao.getOriginalTransactionForLoan(1) } returns mine
        coEvery { loanDao.getOriginalTransactionForLoan(2) } returns theirs
        coEvery { loanDao.getOriginalTransactionForLoan(3) } returns null
        coEvery { loanDao.getFirstSharedTransactionForLoan(3) } returns null
        coEvery { loanDao.getLinkedTransactions(any()) } returns emptyList()

        val loss = repo.settlementLossByCurrency(listOf(loan(1), loan(2), loan(3))) { it.accountNumber == "1111" }

        assertEquals(mapOf("JOD" to BigDecimal("10")), loss)
    }
}
