package com.pennywiseai.tracker.domain.model

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity.Companion.BUSINESS_ID
import com.pennywiseai.tracker.data.database.entity.ProfileEntity.Companion.PERSONAL_ID
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** Pins the effective-profile rule: explicit override > account's profile > Personal. */
class ProfileFiltersTest {

    private val now = LocalDateTime.of(2026, 1, 1, 12, 0)

    private fun account(bank: String, last4: String, profile: Long) =
        AccountBalanceEntity(bankName = bank, accountLast4 = last4, balance = BigDecimal.ZERO, timestamp = now, profileId = profile)

    private fun tx(id: Long, bank: String?, account: String?, profile: Long? = null) = TransactionEntity(
        id = id, amount = BigDecimal.TEN, merchantName = "Shop", category = "Food",
        transactionType = TransactionType.EXPENSE, dateTime = now,
        bankName = bank, accountNumber = account, profileId = profile, transactionHash = "h$id",
    )

    private val keys = buildProfileAccountKeys(
        listOf(account("Bank A", "1111", PERSONAL_ID), account("Bank B", "2222", BUSINESS_ID)),
    )

    private val rows = listOf(
        tx(1, "Bank A", "1111"),
        tx(2, "Bank B", "2222"),
        tx(3, "Bank B", "2222", profile = PERSONAL_ID),
        tx(4, null, null),
        tx(5, "Bank C", "3333"),
    )

    @Test
    fun `keys group accounts by profile`() {
        assertEquals(mapOf(PERSONAL_ID to setOf("Bank A_1111"), BUSINESS_ID to setOf("Bank B_2222")), keys)
    }

    @Test
    fun `null profile keeps every row`() {
        assertEquals(rows, filterTransactionsByProfile(rows, null, keys))
    }

    @Test
    fun `override beats account, unknown accounts default to Personal`() {
        assertEquals(listOf(1L, 3L, 4L, 5L), filterTransactionsByProfile(rows, PERSONAL_ID, keys).map { it.id })
        assertEquals(listOf(2L), filterTransactionsByProfile(rows, BUSINESS_ID, keys).map { it.id })
    }
}
