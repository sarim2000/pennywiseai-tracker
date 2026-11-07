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
        assertEquals(TransactionType.INCOME, type("فلوسي رجعت 50", "EXPENSE"))
        // Money given back, or with no one named: the words don't decide.
        assertEquals(TransactionType.EXPENSE, type("رجعت فلوسه 100 ريال", "EXPENSE"))
        assertEquals(TransactionType.EXPENSE, type("رجعتله 100", "EXPENSE"))
        assertEquals(TransactionType.EXPENSE, type("رجعت الفلوس 100", "EXPENSE"))
        assertEquals(TransactionType.EXPENSE, type("فلوسي رجعتها 100", "EXPENSE"))
        assertEquals(TransactionType.EXPENSE, type("मिलान में कॉफी 500", "EXPENSE"))
    }

    @Test
    fun `got and mila are income only next to the amount`() {
        fun type(text: String, amount: Any = 500, model: String = "EXPENSE") =
            draft(mapOf("amount" to amount, "merchant" to "x", "category" to "Others", "type" to model), text)!!.type
        listOf(
            "got 500 from mom", "got 500 for 2 hours of tutoring", "got ₹500", "got rs.500", "got USD 500", "got back 500",
            "got my money back 500", "500 mila", "mila 500", "मुझे 500 रुपये मिले", "मिले ५०० रुपये", "got a refund for 500",
            "got JD 500 from mom", "got SR 500", "got DHS 500", "got five hundred from mom", "got some cash from mom 500", "got paid 500",
            "got a payment of 500 from a client", "got payment from a client 500", "got a tip of 500", "got an advance of 500", "got a transfer of 500 from dad", "got money for my birthday 500", "got a payment for 500 from a client"
        ).forEach { assertEquals(it, TransactionType.INCOME, type(it)) }
        assertEquals(TransactionType.INCOME, type("got 5k", 5000))
        assertEquals(TransactionType.INCOME, type("got $.50", 0.5))
        assertEquals(TransactionType.INCOME, type(".50 मिले", 0.5))
        assertEquals(TransactionType.INCOME, type("10 हजार मिले", 10000))

        listOf(
            "got a transfer to the airport for 500", "got a tip for the waiter 500", "got a phone for 500", "got tea 500", "got paid version of the app for 500", "got shoes for ₹500", "got a phone for USD 500", "got coffee 50 and tea 500", "मुझे फोन 500 में मिला",
            "मुझे फोन 500 रुपये में मिला", "phone 500 me mila", "got back home by taxi for 500"
        ).forEach { assertEquals(it, TransactionType.EXPENSE, type(it)) }
        assertEquals(TransactionType.EXPENSE, type("got 2 shirts for rs 500", model = "INCOME"))
        assertEquals(TransactionType.EXPENSE, type("phone 10k mein mila", 10000))
        assertEquals(TransactionType.EXPENSE, type("फोन 10 हजार में मिला", 10000))
        // With no word to go on, an income category from the model still counts.
        assertEquals(TransactionType.INCOME, draft(mapOf("amount" to 500, "category" to "Salary", "type" to "INCOME"), "got something for 500")!!.type)
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

    private fun turn(text: String, vararg items: Map<String, Any?>, base: String = "INR") =
        TransactionDraft.fromToolCalls(items.toList(), text, cats, emptyList(), base, today = TODAY) { null }

    private fun item(amount: Any, type: String = "EXPENSE", currency: String = "", daysAgo: Int = 0) =
        mapOf("amount" to amount, "merchant" to "x", "category" to "Others", "type" to type, "currency" to currency, "days_ago" to daysAgo)

    @Test
    fun `each row of a message takes its own direction`() {
        fun types(text: String, vararg items: Map<String, Any?>) = turn(text, *items).map { it.type }
        assertEquals(
            listOf(TransactionType.INCOME, TransactionType.EXPENSE),
            types("got 45000 salary and paid 12000 rent", item(45000), item(12000))
        )
        assertEquals(
            listOf(TransactionType.INCOME, TransactionType.EXPENSE),
            types("sold my bike for 30000, paid 500 for the ad", item(30000), item(500))
        )
        assertEquals(
            listOf(TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.EXPENSE),
            types("salary 60000, rent 15000, groceries 3000", item(60000), item(15000, "INCOME"), item(3000))
        )
        // A row with no word of its own: the model decides, but only if money came in at all.
        assertEquals(
            listOf(TransactionType.INCOME, TransactionType.INCOME),
            types("received 500 from mom and 300 from dad", item(500, "INCOME"), item(300, "INCOME"))
        )
        assertEquals(
            listOf(TransactionType.EXPENSE, TransactionType.EXPENSE),
            types("bought shoes for 300 and socks for 100", item(300), item(100, "INCOME"))
        )
        assertEquals(
            listOf(TransactionType.INCOME, TransactionType.EXPENSE),
            types("received 500 for rent and 300 was lunch", item(500), item(300))
        )
        assertEquals(
            listOf(TransactionType.INCOME, TransactionType.INCOME),
            types("got 500 from mom and 300 from dad", item(500, "INCOME"), item(300, "INCOME"))
        )
        assertEquals(
            listOf(TransactionType.INCOME, TransactionType.INCOME),
            types("500 रुपये मिले और 300 रुपये मिले", item(500, "INCOME"), item(300, "INCOME"))
        )
    }

    @Test
    fun `each row takes its own day and currency, or the one the message shares`() {
        val mixedDays = turn("movie 600 yesterday and popcorn 250 today", item(600), item(250))
        assertEquals(listOf(TODAY.minusDays(1), TODAY), mixedDays.map { it.date })
        val shared = turn("lunch 200, dinner 400, all of it yesterday", item(200), item(400))
        assertEquals(listOf(TODAY.minusDays(1), TODAY.minusDays(1)), shared.map { it.date })
        assertEquals(listOf(TODAY.minusDays(1), TODAY), turn("lunch 300 yesterday and tea 70 just now", item(300), item(70)).map { it.date })

        assertEquals(listOf("USD", "EUR"), turn("$12 for lunch and €8 for coffee", item(12), item(8)).map { it.currency })
        assertEquals(listOf("USD", "USD"), turn("in dollars: lunch 12, taxi 30", item(12), item(30)).map { it.currency })
        assertEquals(listOf("USD", "USD"), turn("coffee 12 and lunch 16, both in USD", item(12), item(16)).map { it.currency })
        // A currency right by one number stays with that number.
        // Even when the model spreads it, as Qwen2.5 does here.
        assertEquals(listOf("USD", "EUR"), turn("train 45 and hotel 200 euros", item(45, currency = "EUR"), item(200, currency = "EUR"), base = "USD").map { it.currency })
        assertEquals(listOf("EUR", "INR"), turn("€5 for tea, 300 for lunch", item(5), item(300)).map { it.currency })
        assertEquals(listOf("EUR", "INR"), turn("coffee 5 in euros and lunch 100", item(5), item(100, currency = "INR")).map { it.currency })
        assertEquals(listOf("EUR", "INR"), turn("coffee: 5 euros and lunch 100", item(5), item(100, currency = "INR")).map { it.currency })
        // A code the words don't know still counts when the model read it from the message.
        assertEquals(listOf("JPY", "JPY"), turn("in JPY: train 1200, lunch 800", item(1200, currency = "JPY"), item(800, currency = "JPY")).map { it.currency })
        assertEquals(listOf("JPY", "JPY"), turn("train 1200, lunch 800, all in JPY", item(1200, currency = "JPY"), item(800, currency = "JPY")).map { it.currency })
        assertEquals(listOf("JPY", "JPY"), turn("in JPY: train 1200, lunch 800", item(1200, currency = "JPY"), item(800)).map { it.currency })
        assertEquals(listOf("JPY", "USD"), turn("paid 1200 JPY for train and 20 USD for lunch", item(1200, currency = "JPY"), item(20, currency = "USD")).map { it.currency })
        // With equal amounts, another row's currency never spreads. "both" is about the cash here.
        assertEquals(listOf("JPY", "USD"), turn("coffee in JPY: 800, train 800 USD", item(800, currency = "JPY"), item(800)).map { it.currency })
        assertEquals(listOf("JPY", "INR"), turn("train 800 JPY, lunch 800 INR, both paid in cash", item(800, currency = "JPY"), item(800)).map { it.currency })
        assertEquals(listOf("JPY", "INR"), turn("train 800 JPY, lunch 800, both paid in cash", item(800, currency = "JPY"), item(800)).map { it.currency })
        assertEquals(listOf("JPY", "INR"), turn("train 800 JPY, lunch 800 INR all paid", item(800, currency = "JPY"), item(800)).map { it.currency })
        assertEquals(listOf("EUR", "JPY"), turn("coffee in euros: 800, train 800 JPY", item(800, currency = "EUR"), item(800, currency = "JPY")).map { it.currency })
        assertEquals(listOf("JPY", "EUR"), turn("train 800 JPY, lunch 800; euros: lunch only", item(800, currency = "JPY"), item(800, currency = "EUR")).map { it.currency })
        assertEquals(listOf("JPY", "INR"), turn("train 800 JPY, lunch 800 INR; both were reimbursed in euros", item(800, currency = "JPY"), item(800, currency = "INR")).map { it.currency })
        assertEquals(listOf("USD", "USD", "USD"), turn("Paid at three places, all in US dollars: tea 4, books 16, taxi 9", item(4), item(16), item(9)).map { it.currency })
    }

    @Test
    fun `a single row reads the whole message`() {
        val one = turn("got 500 and paid nothing back yesterday", item(500)).single()
        assertEquals(TransactionType.EXPENSE, one.type)
        // A word for a bill still beats a word for income on one row.
        listOf("Rent 500 from the salary I received yesterday", "Got 500 rupees of mobile recharge", "Subscription fee 500, refund refused").forEach {
            assertEquals(it, TransactionType.EXPENSE, turn(it, item(500)).single().type)
        }
    }

    @Test
    fun `equal amounts keep the types the model gave them`() {
        val types = turn("received 450 refund from shoe shop, then paid 450 for haircut", item(450, "EXPENSE"), item(450, "INCOME")).map { it.type }
        assertEquals(listOf(TransactionType.EXPENSE, TransactionType.INCOME), types)
    }

    @Test
    fun `a doubtful row starts unticked, and nothing is dropped`() {
        fun rows(text: String, vararg items: Map<String, Any?>) = TransactionDraft.reviewRows(turn(text, *items))
        val merged = rows("coffee 120 and lunch 300", item(420), item(300))
        assertEquals(listOf(ReviewRow.Check.AMOUNT_NOT_IN_MESSAGE, null), merged.map { it.check })
        assertEquals(listOf(false, true), merged.map { it.selected })

        val twice = rows("bought coffee twice today, 50 each", item(50), item(50))
        assertEquals(listOf(null, ReviewRow.Check.REPEATED), twice.map { it.check })
        // Two stated amounts make two honest rows, even with the same name.
        assertEquals(listOf(null, null), rows("uber 180 and uber 180", item(180), item(180)).map { it.check })
        // Same amount in opposite directions isn't a repeat.
        assertEquals(listOf(null, null), rows("received 450, paid 450", item(450), item(450)).map { it.check })

        assertEquals(TransactionDraft.MAX_ROWS, rows((1..12).joinToString(", ") { "item $it" }, *(1..12).map { item(it) }.toTypedArray()).size)
    }

    @Test
    fun `an add call in a broken wrapper is still read, a half-written one isn't`() {
        val broken = "<tool_call>\n{\"name\": \"add_transactions\", \"arguments\": {\"items\": [{\"amount\": 300, \"merchant\": \"sho]es\"}, {\"amount\": 150, \"merchant\": \"socks\"}]}};\n<tool_call>"
        val items = TransactionDraft.itemsInBrokenCall(broken)!!
        assertEquals(listOf("sho]es", "socks"), items.map { it["merchant"] })
        assertEquals(0, BigDecimal("150").compareTo(ChatMessageFacts.parseAmount(items[1]["amount"])))
        assertEquals(null, TransactionDraft.itemsInBrokenCall("{\"name\": \"add_transactions\", \"arguments\": {\"items\": [{\"amount\": 180, \"merch"))
        assertEquals(null, TransactionDraft.itemsInBrokenCall("I would add items [1, 2] here"))
        assertEquals(null, TransactionDraft.itemsInBrokenCall("Use add_transactions with {\"items\":[{\"amount\":50,\"merchant\":\"coffee\"}]}."))
        val fraction = TransactionDraft.itemsInBrokenCall("{\"name\": \"add_transactions\", \"arguments\": {\"items\": [{\"amount\": .50, \"merchant\": \"postage\"}]}}")!!
        assertEquals(0, BigDecimal("0.5").compareTo(ChatMessageFacts.parseAmount(fraction.single()["amount"])))
        assertEquals(1, TransactionDraft.itemsOfCall(mapOf("amount" to 5, "merchant" to "x")).size)
        assertEquals(0, TransactionDraft.itemsOfCall(mapOf("merchant" to "x")).size)
    }

    @Test
    fun `an account the message doesn't name is no merchant`() {
        val args = mapOf("amount" to 500, "merchant" to "HDFC Bank 1234", "category" to "Salary", "type" to "INCOME")
        assertEquals("Salary", draft(args, "got 500 salary")!!.merchant)
        assertEquals("HDFC Bank 1234", draft(args, "paid HDFC Bank 1234 500")!!.merchant)
    }

    @Test
    fun `a plan or someone else's spending starts unticked`() {
        fun check(text: String) = TransactionDraft.reviewRows(turn(text, item(500))).single().check
        listOf(
            "I plan to spend 500 on shoes", "my brother spent 500 on coffee", "should I pay 500 for gym?",
            "tomorrow I might spend 500 on lunch", "I'm going to pay 500 rent next week"
        ).forEach { assertEquals(it, ReviewRow.Check.NOT_A_PAYMENT, check(it)) }
        listOf("paid 500 for shoes", "my brother paid me 500", "spent 500 at the cafe yesterday", "paid 500 at Budget").forEach {
            assertEquals(it, null, check(it))
        }
        fun checks(text: String, vararg items: Map<String, Any?>) = TransactionDraft.reviewRows(turn(text, *items)).map { it.check }
        // Each row answers for itself, and one with no verb of its own follows the last.
        assertEquals(listOf(null, ReviewRow.Check.NOT_A_PAYMENT), checks("paid 50 for lunch and will pay 100 for rent tomorrow", item(50), item(100)))
        assertEquals(listOf(ReviewRow.Check.NOT_A_PAYMENT, ReviewRow.Check.NOT_A_PAYMENT), checks("my brother spent 200 on coffee and 400 on lunch", item(200), item(400)))
        assertEquals(listOf(ReviewRow.Check.NOT_A_PAYMENT, null), checks("will pay 100 for rent, paid 50 for lunch", item(100), item(50)))
        // In the message's order, whatever order the model wrote them in.
        assertEquals(listOf(ReviewRow.Check.NOT_A_PAYMENT, ReviewRow.Check.NOT_A_PAYMENT), checks("my brother spent 200 on coffee and 400 on lunch", item(400), item(200)))
        assertEquals(listOf(ReviewRow.Check.NOT_A_PAYMENT, ReviewRow.Check.NOT_A_PAYMENT), checks("I will pay 100 for rent and buy shoes for 200", item(100), item(200)))
    }
}
