package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class DetectBalanceDiscrepancyTransferPairTest {
    private val time = LocalDateTime.of(2026, 1, 1, 12, 0)

    private fun row(id: Long, type: TransactionType, account: String, minutes: Long, amount: String) = TransactionEntity(
        id = id, amount = BigDecimal(amount), merchantName = "Synthetic", category = "Others", transactionType = type,
        dateTime = time.plusMinutes(minutes), transactionHash = "h$id", bankName = "Bank $account", accountNumber = account,
        currency = "JOD"
    )

    @Test
    fun `the other row of a pair arriving late is no gap in this account`() = runBlocking {
        // The source's SMS lands after the destination's snapshot, so its row falls
        // in the destination's next window, naming the destination as its TO.
        val lateSourceLeg = row(1, TransactionType.TRANSFER, "1111", 1, "100").copy(
            fromAccount = "1111", toAccount = "2222", fromBankName = "Bank 1111", toBankName = "Bank 2222",
            transferPairId = "pair"
        )
        val purchase = row(2, TransactionType.EXPENSE, "2222", 30, "10").copy(balanceAfter = BigDecimal("990"))
        val snapshot = AccountBalanceEntity(
            id = 1, bankName = "Bank 2222", accountLast4 = "2222", balance = BigDecimal("1000"), timestamp = time,
            currency = "JOD"
        )
        val balances = mockk<AccountBalanceRepository> {
            coEvery { getBalanceHistoryForAccount(any(), any()) } returns listOf(snapshot)
            coEvery { getAllLatestBalancesOnce() } returns listOf(snapshot)
        }
        val transactions = mockk<TransactionRepository> {
            every { getTransactionsBetweenDates(any<LocalDateTime>(), any<LocalDateTime>()) } returns
                flowOf(listOf(lateSourceLeg, purchase))
        }

        assertNull(DetectBalanceDiscrepancyUseCase(balances, transactions).execute(purchase))
    }
}
