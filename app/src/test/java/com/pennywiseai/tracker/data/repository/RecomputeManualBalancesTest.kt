package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** Rows an import adds re-derive every manual account they move, as the balance sum counts them. */
class RecomputeManualBalancesTest {
    private val time = LocalDateTime.of(2026, 1, 15, 12, 0)
    private val balanceDao = mockk<AccountBalanceDao>(relaxed = true) {
        coEvery { getLatestBalance(any(), any()) } answers {
            AccountBalanceEntity(
                bankName = firstArg(), accountLast4 = secondArg(), balance = BigDecimal("100"), timestamp = time,
                sourceType = AccountBalanceRepository.SOURCE_MANUAL, currency = "INR"
            )
        }
    }
    private val repo = spyk(AccountBalanceRepository(balanceDao, mockk(relaxed = true), mockk(), mockk(relaxed = true)))

    private fun row(
        id: Long,
        bank: String,
        account: String,
        currency: String = "INR",
        type: TransactionType = TransactionType.EXPENSE
    ) = TransactionEntity(
        id = id, amount = BigDecimal("10"), merchantName = "Synthetic", category = "Others",
        transactionType = type, dateTime = time, transactionHash = "h$id",
        bankName = bank, accountNumber = account, currency = currency
    )

    @Test
    fun `each account a row lands on is recomputed once, transfer legs included`() = runBlocking {
        coEvery { repo.recomputeManualBalance(any(), any()) } just Runs

        repo.recomputeManualBalances(
            listOf(
                row(1, "Wallet", "1111"),
                row(2, "Wallet", "1111"),
                row(3, "Bank", "2222", type = TransactionType.TRANSFER)
                    .copy(fromBankName = "Bank", fromAccount = "2222", toBankName = "Cash", toAccount = "3333")
            )
        )

        coVerify(exactly = 1) { repo.recomputeManualBalance("Wallet", "1111") }
        coVerify(exactly = 1) { repo.recomputeManualBalance("Bank", "2222") }
        coVerify(exactly = 1) { repo.recomputeManualBalance("Cash", "3333") }
    }

    @Test
    fun `a transfer moves only its legs, never the account it was filed under`() = runBlocking {
        coEvery { repo.recomputeManualBalance(any(), any()) } just Runs
        coEvery { repo.ensureManualOpening(any(), any()) } just Runs
        // A rule turned a wallet row into a transfer between two other accounts.
        val transfer = row(1, "Wallet", "1111", type = TransactionType.TRANSFER)
            .copy(fromBankName = "Bank", fromAccount = "2222", toBankName = "Cash", toAccount = "3333")

        repo.pinManualOpenings(transfer)
        repo.recomputeManualBalances(listOf(transfer))

        coVerify(exactly = 0) { repo.ensureManualOpening("Wallet", "1111") }
        coVerify(exactly = 0) { repo.recomputeManualBalance("Wallet", "1111") }
        coVerify(exactly = 1) { repo.recomputeManualBalance("Bank", "2222") }
        coVerify(exactly = 1) { repo.recomputeManualBalance("Cash", "3333") }
    }

    @Test
    fun `a marked pair's leg moves only its own account`() = runBlocking {
        coEvery { repo.recomputeManualBalance(any(), any()) } just Runs
        val leg = row(1, "Bank", "2222", type = TransactionType.TRANSFER).copy(
            fromBankName = "Bank", fromAccount = "2222", toBankName = "Cash", toAccount = "3333", transferPairId = "pair"
        )

        repo.recomputeManualBalances(listOf(leg))

        coVerify(exactly = 1) { repo.recomputeManualBalance("Bank", "2222") }
        coVerify(exactly = 0) { repo.recomputeManualBalance("Cash", "3333") }
    }
}
