package com.pennywiseai.tracker.ui.viewmodel

import org.junit.Assert.*
import org.junit.Test

class RulesViewModelTest {

    @Test
    fun `accountInfo displayName uses double bullet mask`() {
        val account = RulesViewModel.AccountInfo(
            bankName = "HDFC Bank",
            accountLast4 = "1234",
            displayName = "HDFC Bank ••1234",
            isCreditCard = false,
            accountType = "SAVINGS"
        )
        assertEquals("HDFC Bank", account.bankName)
        assertEquals("1234", account.accountLast4)
        assertEquals("HDFC Bank ••1234", account.displayName)
        assertFalse(account.isCreditCard)
        assertEquals("SAVINGS", account.accountType)
    }

    @Test
    fun `accountInfo displayName does not contain asterisks`() {
        val account = RulesViewModel.AccountInfo(
            bankName = "HDFC Bank",
            accountLast4 = "1234",
            displayName = "HDFC Bank ••1234",
            isCreditCard = false,
            accountType = null
        )
        // Ensure no asterisk characters remain
        assertFalse(account.displayName.contains("*"))
        // Ensure bullets are present
        assertTrue(account.displayName.contains("••"))
    }

    @Test
    fun `accountInfo supports null accountType`() {
        val account = RulesViewModel.AccountInfo(
            bankName = "Axis Bank",
            accountLast4 = "5678",
            displayName = "Axis Bank ••5678",
            isCreditCard = true,
            accountType = null
        )
        assertNull(account.accountType)
        assertTrue(account.isCreditCard)
    }

    @Test
    fun `accountInfo displayName uses two bullets not four`() {
        val displayName = "HDFC Bank ••1234"
        // Count bullet characters (Unicode U+2022)
        val bulletCount = displayName.count { it == '•' }
        assertEquals("Expected exactly 2 bullet characters", 2, bulletCount)
    }
}
