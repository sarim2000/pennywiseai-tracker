package com.pennywiseai.tracker.aiaccess

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
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
}
