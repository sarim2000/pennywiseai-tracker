package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/** #170: the tool-call → draft normaliser. The model's words are advisory; the user's text decides type. */
class TransactionDraftTest {
    private val TODAY = LocalDate.of(2026, 11, 4)
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
        TransactionDraft.fromToolArgs(args, text, cats, accounts, baseCurrency = "INR", today = TODAY) { m -> if (m.contains("starbucks", true)) "Food & Dining" else null }

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
    fun `no verb at all - the model's INCOME counts only with an income-only category`() {
        assertEquals(TransactionType.INCOME, draft(mapOf("amount" to 5000.0, "merchant" to "client", "category" to "Salary", "type" to "INCOME", "account" to ""), "5000 from client via neft")!!.type)
        assertEquals(TransactionType.EXPENSE, draft(mapOf("amount" to 90.0, "merchant" to "ice cream", "category" to "Others", "type" to "INCOME", "account" to ""), "ice cream 90")!!.type)
    }

    @Test
    fun `a spend verb beats an income word in the same sentence`() {
        val d = draft(mapOf("amount" to 18000.0, "merchant" to "landlord", "category" to "Others", "type" to "INCOME", "account" to ""), "spent my salary on rent 18000")!!
        assertEquals(TransactionType.EXPENSE, d.type)
        assertEquals(TransactionType.EXPENSE, draft(mapOf("amount" to 300.0, "merchant" to "x", "category" to "", "type" to "INCOME", "account" to ""), "paid 300 using cashback")!!.type)
    }

    @Test
    fun `an ambiguous account is left unlinked`() {
        val two = accounts + AccountBalanceEntity(bankName = "HDFC Bank", accountLast4 = "9999", balance = BigDecimal.TEN, timestamp = LocalDateTime.now())
        val d = TransactionDraft.fromToolArgs(mapOf("amount" to 1.0, "merchant" to "x", "category" to "", "type" to "", "account" to "hdfc"), "x 1", cats, two, baseCurrency = "INR") { null }!!
        assertNull(d.bankName)
        assertEquals("No account (say which — last 4 digits)", d.accountLabel)
    }

    @Test
    fun `last4 picks the right one of two same-bank accounts`() {
        val two = accounts + AccountBalanceEntity(bankName = "HDFC Bank", accountLast4 = "9999", balance = BigDecimal.TEN, timestamp = LocalDateTime.now())
        val acc = { a: String -> TransactionDraft.fromToolArgs(mapOf("amount" to 1.0, "merchant" to "x", "account" to a), "x 1", cats, two, baseCurrency = "INR") { null }!! }
        assertEquals("1234", acc("HDFC 1234").accountLast4)
        assertEquals("9999", acc("hdfc bank 9999").accountLast4)
        assertEquals("9999", acc("9999").accountLast4)                 // last-4 alone
        assertEquals("5678", acc("Salary a/c").accountLast4)           // alias
        assertNull(acc("HDFC").accountLast4)                           // two HDFCs → ambiguous
        assertNull(acc("HDFC 5678").accountLast4)                      // 5678 is Kotak, not HDFC
        assertNull(acc("HDFC 4321").accountLast4)                      // stated digits match nothing
    }

    @Test
    fun `no usable amount means no draft`() {
        assertNull(draft(mapOf("amount" to 0, "merchant" to "x"), "x"))
        assertNull(draft(mapOf("merchant" to "x"), "x"))
    }

    @Test
    fun `currency and date default unless the message states them`() {
        val invented = draft(mapOf("amount" to 120.0, "merchant" to "tea", "currency" to "USD", "days_ago" to 3), "tea 120")!!
        assertEquals("INR", invented.currency)
        assertEquals(TODAY, invented.date)
        val stated = draft(mapOf("amount" to 5.0, "merchant" to "tea", "currency" to "USD", "days_ago" to 1), "paid $5 for tea yesterday")!!
        assertEquals("USD", stated.currency)
        assertEquals(TODAY.minusDays(1), stated.date)
    }

    @Test
    fun `the card knows when the amount is not in the message`() {
        assertEquals(true, draft(mapOf("amount" to 120.0, "merchant" to "x"), "x 120")!!.amountInMessage)
        assertEquals(false, draft(mapOf("amount" to 420.0, "merchant" to "x"), "coffee 120 lunch 300")!!.amountInMessage)
    }

    @Test
    fun `a negative amount is the amount`() {
        assertEquals(0, BigDecimal("15000").compareTo(draft(mapOf("amount" to -15000.0, "merchant" to "x"), "x 15000")!!.amount))
    }

    @Test
    fun `the user's words decide the type in Arabic and Hindi too`() {
        fun type(text: String, modelType: String) = draft(mapOf("amount" to 1.0, "merchant" to "x", "category" to "Others", "type" to modelType), text)!!.type
        assertEquals(TransactionType.INCOME, type("نزل الراتب 12000", "EXPENSE"))
        assertEquals(TransactionType.INCOME, type("رجعولي 150 ريال من نون", "EXPENSE"))
        assertEquals(TransactionType.INCOME, type("सैलरी आई 45000", "EXPENSE"))
        assertEquals(TransactionType.INCOME, type("salary aa gayi 60000", "EXPENSE"))
        assertEquals(TransactionType.EXPENSE, type("دفعت 230 ريال فاتورة الكهرباء", "INCOME"))
        assertEquals(TransactionType.EXPENSE, type("दूध के लिए 60 रुपये दिए", "INCOME"))
        assertEquals(TransactionType.INCOME, type("نزلت الراتب", "EXPENSE"))
        // Part of a longer word isn't the word.
        assertEquals(TransactionType.EXPENSE, type("مرجع برمجة 50", "EXPENSE"))
        assertEquals(TransactionType.EXPENSE, type("مراتب 50", "EXPENSE"))
        assertEquals(TransactionType.EXPENSE, type("تذكرة رجعة 50", "EXPENSE"))
        assertEquals(TransactionType.EXPENSE, type("رجعت البيت بتاكسي 50", "EXPENSE")) // "I came home", not a refund.
        assertEquals(TransactionType.EXPENSE, type("رجعت ليلا بتاكسي 50", "EXPENSE")) // "At night", not "to me".
        assertEquals(TransactionType.INCOME, type("محل الورق رجعلي 260 جنيه", "EXPENSE"))
        assertEquals(TransactionType.INCOME, type("رجعت لي 50", "EXPENSE"))
        assertEquals(TransactionType.INCOME, type("رجعولنا 50", "EXPENSE"))
        assertEquals(TransactionType.INCOME, type("رجعوا فلوسي 50", "EXPENSE"))
        assertEquals(TransactionType.INCOME, type("الفلوس رجعت 50", "EXPENSE"))
        assertEquals(TransactionType.EXPENSE, type("मिलान में कॉफी 500", "EXPENSE"))
    }

    @Test
    fun `a transaction links only to an account in its own currency`() {
        val args = mapOf("amount" to 20.0, "merchant" to "lunch", "account" to "hdfc")
        assertEquals("1234", draft(args, "paid 20 for lunch using hdfc")!!.accountLast4)
        val foreign = draft(args, "paid USD 20 for lunch using hdfc")!!
        assertEquals("USD", foreign.currency)
        assertNull(foreign.accountLast4) // Its INR balance would drop by a raw 20.
        assertEquals("Cash / manual", foreign.accountLabel)
    }
}
