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

/** A merged account's bill moves to the target; its balance anchor doesn't. */
class KeepAccountFieldsTest {

    private val dao = mockk<AccountBalanceDao>()
    private val repository = AccountBalanceRepository(dao, mockk(), mockk(), mockk())

    private val source = AccountBalanceEntity(
        bankName = "Test Bank", accountLast4 = "0000", balance = BigDecimal("900"),
        timestamp = LocalDateTime.of(2026, 3, 20, 10, 0), isCreditCard = true,
        statementDay = 12, cardDueDay = 2, cardBillAmount = BigDecimal("650.25"),
        cardBillAsOf = LocalDateTime.of(2026, 3, 14, 9, 0),
        anchorAmount = BigDecimal("1000"), anchorAt = LocalDateTime.of(2026, 1, 1, 8, 0), anchorSource = "MANUAL",
    )

    @Test
    fun `a merge keeps the source's bill on the target, not its anchor`() = runTest {
        coEvery { dao.fillMissingAccountFields(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns 1

        repository.keepAccountFields(source, "Other Bank", "1111", withAnchor = false)

        coVerify {
            dao.fillMissingAccountFields(
                "Other Bank", "1111", 12, 2, BigDecimal("650.25"), source.cardBillAsOf, null, null, null,
            )
        }
    }

    @Test
    fun `a rescan keeps the anchor too`() = runTest {
        coEvery { dao.fillMissingAccountFields(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns 1

        repository.keepAccountFields(source)

        coVerify {
            dao.fillMissingAccountFields(
                "Test Bank", "0000", 12, 2, BigDecimal("650.25"), source.cardBillAsOf,
                BigDecimal("1000"), source.anchorAt, "MANUAL",
            )
        }
    }
}
