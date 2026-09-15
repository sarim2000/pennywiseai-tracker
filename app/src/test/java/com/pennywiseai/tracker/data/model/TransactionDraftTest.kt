package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** #170: the tool-call → draft normaliser. The model's words are advisory; the user's text decides type. */
class TransactionDraftTest {
    private val cats = listOf(
        CategoryEntity(id = 1, name = "Food & Dining", color = "#000"),
        CategoryEntity(id = 2, name = "Groceries", color = "#000"),
        CategoryEntity(id = 3, name = "Others", color = "#000"),
        CategoryEntity(id = 4, name = "Salary", color = "#000", isIncome = true),
        CategoryEntity(id = 5, name = "Refund", color = "#000", isIncome = true)
    )
    private val accounts = listOf(
        AccountBalanceEntity(bankName = "HDFC Bank", accountLast4 = "1234", balance = BigDecimal.TEN, timestamp = LocalDateTime.now()),
        AccountBalanceEntity(bankName = "Kotak Bank", accountLast4 = "5678", balance = BigDecimal.TEN, timestamp = LocalDateTime.now(), alias = "Salary a/c")
    )
    private fun draft(args: Map<String, Any?>, text: String) =
        TransactionDraft.fromToolArgs(args, text, cats, accounts) { m -> if (m.contains("starbucks", true)) "Food & Dining" else null }

    @Test
    fun `plain spend stays EXPENSE even when the model says INCOME`() {
        val d = draft(mapOf("amount" to 2000.0, "merchant" to "petrol", "category" to "Transportation", "type" to "INCOME", "account" to "cash"), "petrol 2000 cash")!!
        assertEquals(TransactionType.EXPENSE, d.type)
        assertEquals("Others", d.category)          // unknown category → Others
        assertNull(d.bankName)                      // cash → manual
        assertEquals(0, BigDecimal("2000").compareTo(d.amount))
    }

    @Test
    fun `received money is INCOME with an income category`() {
        val d = draft(mapOf("amount" to 50000, "merchant" to "Infosys", "category" to "Salary", "type" to "EXPENSE", "account" to ""), "got 50000 salary from infosys")!!
        assertEquals(TransactionType.INCOME, d.type)
        assertEquals("Salary", d.category)
    }

    @Test
    fun `merchant mapping fills a bad category and the account matches a bank`() {
        val d = draft(mapOf("amount" to 120.0, "merchant" to "Starbucks", "category" to "Shopping", "type" to "EXPENSE", "account" to "hdfc"), "coffee 120 at starbucks paid with hdfc")!!
        assertEquals("Food & Dining", d.category)
        assertEquals("HDFC Bank", d.bankName); assertEquals("1234", d.accountLast4)
    }

    @Test
    fun `alias and last4 also match an account`() {
        assertEquals("5678", draft(mapOf("amount" to 1.0, "merchant" to "x", "category" to "", "type" to "", "account" to "salary a/c"), "x 1")!!.accountLast4)
        assertEquals("5678", draft(mapOf("amount" to 1.0, "merchant" to "x", "category" to "", "type" to "", "account" to "5678"), "x 1")!!.accountLast4)
    }

    @Test
    fun `no usable amount means no draft`() {
        assertNull(draft(mapOf("amount" to 0, "merchant" to "x"), "x"))
        assertNull(draft(mapOf("merchant" to "x"), "x"))
    }
}
