package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** A merged or rescanned account's bill goes back on its rows, in its own currency. */
class KeepAccountFieldsTest {

    private val dao = mockk<AccountBalanceDao>()
    private val repository = AccountBalanceRepository(dao, mockk(), mockk(), mockk())

    private val source = AccountBalanceEntity(
        bankName = "Test Bank", accountLast4 = "0000", balance = BigDecimal("900"),
        timestamp = LocalDateTime.of(2026, 3, 20, 10, 0), isCreditCard = true,
        statementDay = 12, cardDueDay = 2, cardBillAmount = BigDecimal("650.25"),
        cardBillAsOf = LocalDateTime.of(2026, 3, 14, 9, 0),
    )

    @Test
    fun `a merge keeps the source's bill on the target`() = runTest {
        coEvery { dao.fillMissingAccountFields(any(), any(), any(), any(), any(), any(), any()) } returns 1

        repository.keepAccountFields(source, "Other Bank", "1111")

        coVerify {
            dao.fillMissingAccountFields("Other Bank", "1111", 12, 2, BigDecimal("650.25"), source.cardBillAsOf, "INR")
        }
    }

    @Test
    fun `the bill goes only where its currency is the row's`() = runTest {
        coEvery { dao.fillMissingAccountFields(any(), any(), any(), any(), any(), any(), any()) } returns 1

        repository.keepAccountFields(source.copy(currency = "USD"), "Other Bank", "1111")

        coVerify {
            dao.fillMissingAccountFields("Other Bank", "1111", 12, 2, BigDecimal("650.25"), source.cardBillAsOf, "USD")
        }
    }
}
