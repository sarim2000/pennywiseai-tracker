package com.pennywiseai.tracker.presentation.add

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** The quick cash add: what the amount field accepts and the order of its category chips. */
class QuickAddActivityTest {

    @Test
    fun `digits from an Arabic keyboard become an amount`() {
        assertEquals("3.5", QuickAddActivity.normalizeAmount("٣٫٥", decimalComma = false))
        assertEquals("1250", QuickAddActivity.normalizeAmount("١٬٢٥٠", decimalComma = false))
        assertEquals("", QuickAddActivity.normalizeAmount("", decimalComma = false))
    }

    @Test
    fun `a comma is a decimal point only where the locale writes it so`() {
        // en-IN: "1,250" is twelve hundred and fifty, as in the full form, not 1.25.
        assertEquals("1250", QuickAddActivity.normalizeAmount("1,250", decimalComma = false))
        assertEquals("12.750", QuickAddActivity.normalizeAmount("12,750", decimalComma = true))
        assertNull(QuickAddActivity.normalizeAmount("1,2,3", decimalComma = true))
    }

    @Test
    fun `a second decimal point or a letter is refused`() {
        assertNull(QuickAddActivity.normalizeAmount("1.2.3", decimalComma = false))
        assertNull(QuickAddActivity.normalizeAmount("1٫2.", decimalComma = false))
        assertNull(QuickAddActivity.normalizeAmount("-5", decimalComma = false))
        assertNull(QuickAddActivity.normalizeAmount("5a", decimalComma = false))
    }

    @Test
    fun `most-used categories lead and Others trails`() {
        val categories = listOf("Food", "Others", "Transport", "Groceries", "Bills")
            .map { CategoryEntity(name = it, color = "#000000") }
        val ranked = QuickAddActivity.rankCategories(categories, listOf("Others", "Groceries", "Food"), others = "Others")
        assertEquals(listOf("Groceries", "Food", "Transport", "Bills", "Others"), ranked.map { it.name })
    }

    @Test
    fun `cash comes from the selected profile, last used first`() {
        val day = LocalDateTime.of(2026, 1, 10, 9, 0)
        fun cash(name: String, balance: Int, usedDaysAgo: Long, profile: Long = 1, currency: String = "JOD", type: String = "CASH") =
            AccountBalanceEntity(
                bankName = name, accountLast4 = "0000", balance = BigDecimal(balance),
                timestamp = day.minusDays(usedDaysAgo), accountType = type, currency = currency, profileId = profile
            )
        // Sorted by balance, as the DAO returns them: the biggest isn't the one in use.
        val accounts = listOf(
            cash("Business cash", 900, 0, profile = 2),
            cash("Home box", 300, 5),
            cash("Wallet", 40, 1),
            cash("Travel", 200, 0, currency = "USD"),
            cash("Bank", 1000, 0, type = "SAVINGS")
        )
        assertEquals(listOf("Wallet", "Home box"), QuickAddActivity.cashAccounts(accounts, "JOD", profileId = 1).map { it.bankName })
        assertEquals(listOf("Business cash"), QuickAddActivity.cashAccounts(accounts, "JOD", profileId = 2).map { it.bankName })
        // All profiles: any profile's cash, still last used first.
        assertEquals(
            listOf("Business cash", "Wallet", "Home box"),
            QuickAddActivity.cashAccounts(accounts, "JOD", profileId = null).map { it.bankName }
        )
    }
}
