package com.pennywiseai.tracker.aiaccess

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class AiToolLogicTest {

    private fun txn(
        merchant: String,
        amount: String,
        currency: String = "INR",
        category: String = "Food & Dining",
        type: TransactionType = TransactionType.EXPENSE,
        excluded: Boolean = false,
        day: Int = 5,
    ) = TransactionEntity(
        amount = BigDecimal(amount),
        merchantName = merchant,
        category = category,
        transactionType = type,
        dateTime = LocalDateTime.of(2026, 10, day, 12, 0),
        transactionHash = "$merchant$amount$day",
        currency = currency,
        smsBody = "SMS TEXT THAT MUST NOT LEAVE THE PHONE",
        bankName = "HDFC",
        accountNumber = "XXXX1234",
        excludedFromAnalytics = excluded,
    )

    private fun JsonObject.totals(key: String = "grand_total") =
        this[key]!!.jsonArray.associate { it.jsonObject["currency"]!!.jsonPrimitive.content to it.jsonObject["amount"]!!.jsonPrimitive.content }

    @Test
    fun `summary keeps currencies apart, counts card spends and skips excluded`() {
        val result = AiToolLogic.spendingSummary(
            listOf(
                txn("Swiggy", "412"),
                txn("Zomato", "289.5"),
                txn("Amazon US", "18.99", currency = "USD", category = "Shopping"),
                txn("Card dinner", "100", type = TransactionType.CREDIT),
                txn("Excluded", "999", excluded = true),
                txn("Salary", "85000", type = TransactionType.INCOME, category = "Salary"),
            ),
            groupBy = "category",
            type = "EXPENSE",
        )
        assertEquals(mapOf("INR" to "801.50", "USD" to "18.99"), result.totals())
        val food = result["groups"]!!.jsonArray.first { it.jsonObject["group"]!!.jsonPrimitive.content == "Food & Dining" }.jsonObject
        assertEquals(mapOf("INR" to "801.50"), food.totals("totals"))
    }

    @Test
    fun `search masks the account and never sends SMS text`() {
        val out = AiToolLogic.searchTransactions(listOf(txn("Swiggy", "412")), query = "swig", category = null, type = null, limit = 10)
        val row = out["transactions"]!!.jsonArray.single().jsonObject
        assertEquals("HDFC ••1234", row["account"]!!.jsonPrimitive.content)
        assertEquals("412.00", row["amount"]!!.jsonObject["amount"]!!.jsonPrimitive.content)
        assertFalse(out.toString().contains("SMS TEXT"))
        assertFalse(out.toString().contains("XXXX1234"))
    }

    @Test
    fun `search caps rows and reports how many matched`() {
        val many = (1..250).map { txn("Shop$it", "1", day = 1 + it % 28) }
        val out = AiToolLogic.searchTransactions(many, null, null, null, limit = 1000)
        assertEquals(250, out["total_matches"]!!.jsonPrimitive.content.toInt())
        assertEquals(AiToolLogic.MAX_ROWS, out["transactions"]!!.jsonArray.size)
    }

    @Test
    fun `accounts show credit card outstanding and hide the wallet marker`() {
        val out = AiToolLogic.accounts(
            listOf(
                AccountBalanceEntity(bankName = "ICICI", accountLast4 = "1093", balance = BigDecimal("3914"), timestamp = LocalDateTime.of(2026, 10, 12, 9, 0), isCreditCard = true),
                AccountBalanceEntity(bankName = "eMola", accountLast4 = AccountBalanceEntity.WALLET_ACCOUNT_MARKER, balance = BigDecimal("50"), timestamp = LocalDateTime.of(2026, 10, 12, 9, 0), currency = "MZN"),
            )
        )["accounts"]!!.jsonArray.map { it.jsonObject }
        assertEquals("ICICI ••1093", out[0]["account"]!!.jsonPrimitive.content)
        assertEquals("3914.00", out[0]["outstanding"]!!.jsonObject["amount"]!!.jsonPrimitive.content)
        assertEquals("eMola", out[1]["account"]!!.jsonPrimitive.content)
    }

    @Test
    fun `search pages with offset, filters by account and sorts amounts within a currency`() {
        val list = listOf(
            txn("A", "10", day = 1), txn("B", "300", day = 2), txn("C", "50", day = 3),
            txn("D", "5", currency = "USD", day = 4),
            txn("E", "999", day = 5).copy(bankName = "ICICI", accountNumber = "9999"),
        )
        val page2 = AiToolLogic.searchTransactions(list, null, null, null, limit = 2, offset = 2)
        assertEquals(listOf("C", "B"), page2["transactions"]!!.jsonArray.map { it.jsonObject["merchant"]!!.jsonPrimitive.content })
        val hdfcOnly = AiToolLogic.searchTransactions(list, null, null, null, limit = 50, account = "HDFC ••1234")
        assertEquals(4, hdfcOnly["total_matches"]!!.jsonPrimitive.content.toInt())
        // Pickers list every account, not just the chosen one.
        assertEquals(listOf("HDFC ••1234", "ICICI ••9999"), hdfcOnly["accounts"]!!.jsonArray.map { it.jsonPrimitive.content })
        val byAmount = AiToolLogic.searchTransactions(list, null, null, null, limit = 50, sort = "amount_desc")
        assertEquals(listOf("E", "B", "C", "A", "D"), byAmount["transactions"]!!.jsonArray.map { it.jsonObject["merchant"]!!.jsonPrimitive.content })
    }

    @Test
    fun `month_category groups carry month and category, totals still per currency`() {
        val out = AiToolLogic.spendingSummary(
            listOf(txn("A", "10", day = 1), txn("B", "20", day = 2), txn("C", "7", currency = "USD", day = 3, category = "Shopping")),
            groupBy = "month_category", type = "EXPENSE",
        )
        val food = out["groups"]!!.jsonArray.map { it.jsonObject }.single { it["sub"]!!.jsonPrimitive.content == "Food & Dining" }
        assertEquals("2026-10", food["group"]!!.jsonPrimitive.content)
        assertEquals(mapOf("INR" to "30.00"), food.totals("totals"))
    }

    @Test
    fun `summary counts only your part of split bills and nets refunds per currency`() {
        val all = listOf(
            txn("Dinner", "1000").copy(lentSharesTotal = BigDecimal("400")),
            txn("Lent", "500", category = "Transfers").copy(loanId = 1),
            txn("Shop", "100", category = "Shopping"),
            txn("Shop refund", "40", type = TransactionType.INCOME, category = "Refund")
                .copy(budgetImpactType = BudgetImpactType.DEDUCT_SPENT, budgetCategory = "Shopping"),
            txn("Salary", "85000", type = TransactionType.INCOME, category = "Salary"),
        )
        val spend = AiToolLogic.spendingSummary(all, groupBy = "category", type = "EXPENSE")
        assertEquals(mapOf("INR" to "660.00"), spend.totals())
        val shopping = spend["groups"]!!.jsonArray.map { it.jsonObject }.single { it["group"]!!.jsonPrimitive.content == "Shopping" }
        assertEquals(mapOf("INR" to "60.00"), shopping.totals("totals"))
        assertEquals(mapOf("INR" to "85000.00"), AiToolLogic.spendingSummary(all, groupBy = "category", type = "INCOME").totals())
    }
}
