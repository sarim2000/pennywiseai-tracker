package com.pennywiseai.tracker.domain.insights

import com.pennywiseai.tracker.data.database.dao.CategoryDao
import com.pennywiseai.tracker.data.database.dao.SubscriptionDao
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.domain.insights.InsightFact.BillsDue
import com.pennywiseai.tracker.domain.insights.InsightFact.BudgetOver
import com.pennywiseai.tracker.domain.insights.InsightFact.BudgetPace
import com.pennywiseai.tracker.domain.insights.InsightFact.BudgetsOnTrack
import com.pennywiseai.tracker.domain.insights.InsightFact.CardUtilisation
import com.pennywiseai.tracker.domain.insights.InsightFact.CategorySpike
import com.pennywiseai.tracker.domain.insights.InsightFact.LargeTransaction
import com.pennywiseai.tracker.domain.insights.InsightFact.PriceChange
import com.pennywiseai.tracker.domain.insights.InsightFact.SavingsRate
import com.pennywiseai.tracker.domain.review.PeriodReview
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.domain.review.buildPeriodDetail
import com.pennywiseai.tracker.domain.review.buildPeriodReview
import com.pennywiseai.tracker.domain.review.cyclesEndingWith
import com.pennywiseai.tracker.domain.review.reviewRange
import com.pennywiseai.tracker.utils.Money
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

class InsightRulesTest {
    private var nextId = 1L
    private val today = LocalDate.parse("2026-11-20")
    private val range = reviewRange(ReviewKind.MONTH, 0, today, 1, DayOfWeek.MONDAY)

    private fun tx(
        date: String,
        amount: String,
        type: TransactionType = TransactionType.EXPENSE,
        currency: String = "INR",
        category: String = "Food",
        merchant: String = "Shop",
        recurring: Boolean = false,
        bank: String? = null,
        account: String? = null,
    ) = TransactionWithSplits(
        TransactionEntity(
            id = nextId, amount = BigDecimal(amount), merchantName = merchant, category = category,
            transactionType = type, dateTime = LocalDate.parse(date).atTime(12, 0), currency = currency,
            transactionHash = "h${nextId++}", isRecurring = recurring, bankName = bank, accountNumber = account,
        ),
        emptyList()
    )

    private fun review(rows: List<TransactionWithSplits>, on: LocalDate = today): PeriodReview = runBlocking {
        val current = reviewRange(ReviewKind.MONTH, 0, on, 1, DayOfWeek.MONDAY)
        fun within(r: TransactionWithSplits, from: LocalDate, to: LocalDate) =
            r.transaction.dateTime.toLocalDate() in from..to
        buildPeriodReview(
            current,
            rows.filter { within(it, current.start, current.end) },
            rows.filter { within(it, current.previousStart, current.previousEnd) },
            creditAsExpense = false,
            parentOf = emptyMap(),
        ).copy(detail = buildPeriodDetail(cyclesEndingWith(current, 1, 6), rows, false, emptyMap()))
    }

    private fun evaluate(
        rows: List<TransactionWithSplits> = emptyList(),
        budgets: List<BudgetStatus> = emptyList(),
        subscriptions: List<SubscriptionEntity> = emptyList(),
        cards: List<AccountBalanceEntity> = emptyList(),
        on: LocalDate = today,
    ) = InsightRules.evaluate(InsightInputs(on, review(rows, on), budgets, subscriptions, cards))

    private fun budget(
        id: Long,
        spent: String,
        limit: String = "3000",
        elapsed: Int = 10,
        days: Int = 30,
        currency: String = "INR",
        incomplete: Boolean = false,
        start: String = "2026-11-01",
    ) = BudgetStatus(
        id, "Budget $id", currency, BigDecimal(spent), BigDecimal(limit), elapsed, days,
        LocalDate.parse(start), LocalDate.parse(start).plusDays(days - 1L), incomplete,
    )

    private fun subscription(
        id: Long,
        amount: String,
        due: String?,
        currency: String = "INR",
        merchant: String = "Streamer",
        cycle: String = "Monthly",
        bank: String? = null,
        last4: String? = null,
    ) = SubscriptionEntity(
        id = id, merchantName = merchant, amount = BigDecimal(amount),
        nextPaymentDate = due?.let(LocalDate::parse), currency = currency, billingCycle = cycle,
        bankName = bank, accountLast4 = last4,
    )

    private fun card(balance: String, limit: String?, currency: String = "INR", last4: String = "1111") =
        AccountBalanceEntity(
            bankName = "Test Bank", accountLast4 = last4, balance = BigDecimal(balance),
            timestamp = today.atStartOfDay(), creditLimit = limit?.let(::BigDecimal), isCreditCard = true,
            currency = currency,
        )

    private inline fun <reified T : InsightFact> List<Insight>.facts(): List<T> = map { it.fact }.filterIsInstance<T>()

    // --- Thin data

    @Test
    fun `a brand-new user gets nothing, not guesses`() {
        assertEquals(emptyList<Insight>(), evaluate())
    }

    @Test
    fun `zero amounts and zero limits say nothing`() {
        val insights = evaluate(
            rows = listOf(tx("2026-11-10", "0")),
            budgets = listOf(budget(1, "0", limit = "0")),
            cards = listOf(card("0", "1000"), card("500", "0", last4 = "2222")),
        )
        assertEquals(emptyList<Insight>(), insights)
    }

    // --- Budgets

    @Test
    fun `over budget is an alert, on pace to overshoot a warning with what is left per day`() {
        val insights = evaluate(budgets = listOf(budget(1, "3500"), budget(2, "1500")))
        val over = insights.single { it.fact is BudgetOver }
        assertEquals(InsightSeverity.ALERT, over.severity)
        assertEquals(InsightAction.OpenBudgets, over.action)

        val pace = insights.facts<BudgetPace>().single()
        // 1,500 in 10 of 30 days heads for 4,500; 1,500 left over the 21 days still to come,
        // in whole units as Home's budget card shows it.
        assertEquals(Money(BigDecimal("4500"), "INR"), pace.projected)
        assertEquals(Money(BigDecimal("71"), "INR"), pace.perDayLeft)
    }

    @Test
    fun `early in the window a fast start is not yet a pace warning`() {
        val insights = evaluate(budgets = listOf(budget(1, "1000", elapsed = 5)))
        assertTrue(insights.facts<BudgetPace>().isEmpty())
    }

    @Test
    fun `a fast start too early to warn still keeps its currency off on track`() {
        val budgets = listOf(budget(1, "1000", elapsed = 5), budget(2, "100", elapsed = 5))
        assertTrue(evaluate(budgets = budgets).isEmpty())
    }

    @Test
    fun `the limit reached with days to go is an alert, not nothing a day`() {
        val reached = evaluate(budgets = listOf(budget(1, "3000"))).single()
        assertEquals(InsightSeverity.ALERT, reached.severity)
        assertTrue(reached.fact is BudgetOver)
        // On the window's last day, spending exactly the limit is on budget.
        val lastDay = evaluate(budgets = listOf(budget(1, "3000", elapsed = 30))).single()
        assertEquals(BudgetsOnTrack(1), lastDay.fact)
    }

    @Test
    fun `budgets on track are said once per currency, never with an incomplete figure`() {
        val insights = evaluate(
            budgets = listOf(
                budget(1, "500"), budget(2, "200"),
                budget(3, "3500", currency = "USD"),
                budget(4, "10", currency = "AED", incomplete = true),
            ),
        )
        val onTrack = insights.filter { it.fact is BudgetsOnTrack }
        assertEquals(listOf("INR"), onTrack.map { it.currency })
        assertEquals(BudgetsOnTrack(2), onTrack.single().fact)
    }

    @Test
    fun `a new window of a weekly budget is a new insight, in the same cycle`() {
        val week1 = evaluate(budgets = listOf(budget(1, "800", limit = "700", elapsed = 5, days = 7))).single()
        val week2 = evaluate(
            budgets = listOf(budget(1, "800", limit = "700", elapsed = 5, days = 7, start = "2026-11-08")),
        ).single()
        assertTrue(week1.id != week2.id)
        assertEquals(insightCycleOf(week1.id), insightCycleOf(week2.id))
    }

    // --- Category spike

    private fun history(food: String, others: String = "900") = listOf("2026-08-10", "2026-09-10", "2026-10-10").flatMap {
        listOf(tx(it, food), tx(it, others, category = "Rent"))
    }

    @Test
    fun `a category well above its three-cycle average is flagged`() {
        val insights = evaluate(rows = history("100") + tx("2026-11-05", "300"))
        val spike = insights.facts<CategorySpike>().single()
        assertEquals("Food", spike.category)
        assertEquals(200, spike.percentAbove)
        assertEquals(Money(BigDecimal("100.00"), "INR"), spike.usual)
    }

    @Test
    fun `uncategorised spending is never a spike`() {
        val others = listOf("2026-08-10", "2026-09-10", "2026-10-10").map { tx(it, "100", category = "Others") }
        val insights = evaluate(rows = others + tx("2026-11-05", "300", category = "Others"))
        assertTrue(insights.facts<CategorySpike>().isEmpty())
    }

    @Test
    fun `a spike needs two earlier cycles with activity`() {
        val insights = evaluate(rows = listOf(tx("2026-10-10", "100"), tx("2026-11-05", "300")))
        assertTrue(insights.facts<CategorySpike>().isEmpty())
    }

    @Test
    fun `a small category tripling is not news`() {
        val insights = evaluate(rows = history("3") + tx("2026-11-05", "9"))
        assertTrue(insights.facts<CategorySpike>().isEmpty())
    }

    @Test
    fun `history in another currency is not a baseline`() {
        val usd = listOf("2026-08-10", "2026-09-10", "2026-10-10").map { tx(it, "100", currency = "USD") }
        val insights = evaluate(rows = usd + tx("2026-11-05", "300"))
        assertTrue(insights.facts<CategorySpike>().isEmpty())
    }

    // --- Savings rate

    private fun salaryAndSpend(spent: String, currency: String = "INR") = listOf(
        tx("2026-11-01", "1000", type = TransactionType.INCOME, category = "Salary", currency = currency),
        tx("2026-11-03", spent, currency = currency),
    )

    @Test
    fun `saving a fifth or more is doing well, spending past income a warning`() {
        val good = evaluate(rows = salaryAndSpend("700")).single { it.fact is SavingsRate }
        assertEquals(InsightSeverity.GOOD, good.severity)
        assertEquals(30, (good.fact as SavingsRate).percent)

        val over = evaluate(rows = salaryAndSpend("1200")).single { it.fact is SavingsRate }
        assertEquals(InsightSeverity.WARNING, over.severity)
        assertEquals(Money(BigDecimal("-200"), "INR"), (over.fact as SavingsRate).saved)
    }

    @Test
    fun `a tiny income against huge spending is a warning, not a wrapped-around gain`() {
        val rows = listOf(
            tx("2026-11-01", "0.01", type = TransactionType.INCOME, category = "Salary"),
            tx("2026-11-03", "300000"),
        )
        val rate = evaluate(rows = rows).single { it.fact is SavingsRate }
        assertEquals(InsightSeverity.WARNING, rate.severity)
        assertEquals(-999, (rate.fact as SavingsRate).percent)
        // The summary card's figure, which is worked out on its own.
        assertEquals(-999, InsightRules.savingsPercent(BigDecimal("-299999.99"), BigDecimal("0.01")))
    }

    @Test
    fun `no savings rate without income, or in the cycle's first days`() {
        assertTrue(evaluate(rows = listOf(tx("2026-11-03", "700"))).facts<SavingsRate>().isEmpty())
        assertTrue(evaluate(rows = salaryAndSpend("700"), on = LocalDate.parse("2026-11-04")).facts<SavingsRate>().isEmpty())
    }

    @Test
    fun `each currency keeps its own savings rate`() {
        val insights = evaluate(rows = salaryAndSpend("700") + salaryAndSpend("1200", currency = "USD"))
        assertEquals(
            mapOf("INR" to 30, "USD" to -20),
            insights.filter { it.fact is SavingsRate }.associate { it.currency to (it.fact as SavingsRate).percent },
        )
    }

    // --- Large transaction

    private val usualFood = listOf("2026-08-03", "2026-09-03", "2026-10-03", "2026-10-13").map { tx(it, "100") }

    @Test
    fun `a spend several times the category median is flagged`() {
        val big = tx("2026-11-12", "450", merchant = "Restaurant")
        val insights = evaluate(rows = usualFood + big)
        val large = insights.single { it.fact is LargeTransaction }
        assertEquals(InsightAction.OpenTransaction(big.transaction.id), large.action)
        assertEquals(4, (large.fact as LargeTransaction).times)
    }

    @Test
    fun `too few rows for a median, or a known subscription, is not unusual`() {
        assertTrue(evaluate(rows = usualFood.drop(1) + tx("2026-11-12", "450")).facts<LargeTransaction>().isEmpty())
        val insights = evaluate(
            rows = usualFood + tx("2026-11-12", "450", merchant = "Streamer"),
            subscriptions = listOf(subscription(1, "450", "2026-12-12")),
        )
        assertTrue(insights.facts<LargeTransaction>().isEmpty())
    }

    @Test
    fun `a subscription on another account doesn't explain a large spend`() {
        val insights = evaluate(
            rows = usualFood + tx("2026-11-12", "450", merchant = "Streamer", bank = "Bank B", account = "2222"),
            subscriptions = listOf(subscription(1, "450", "2026-12-12", bank = "Bank A", last4 = "1111")),
        )
        assertEquals(1, insights.facts<LargeTransaction>().size)
    }

    @Test
    fun `a category's median stays within its currency`() {
        val usd = usualFood.map { tx(it.transaction.dateTime.toLocalDate().toString(), "100", currency = "USD") }
        val insights = evaluate(
            rows = usd + tx("2026-11-12", "450", currency = "USD") + tx("2026-11-12", "450", currency = "INR"),
        )
        // INR has one Food row, too few for a median of its own; USD's history doesn't lend it one.
        assertEquals(listOf("USD"), insights.filter { it.fact is LargeTransaction }.map { it.currency })
        assertEquals(Money(BigDecimal("450"), "USD"), insights.facts<LargeTransaction>().single().amount)
    }

    @Test
    fun `a charge marked recurring is not unusual`() {
        val insights = evaluate(rows = usualFood + tx("2026-11-12", "450", merchant = "Insurer", recurring = true))
        assertTrue(insights.facts<LargeTransaction>().isEmpty())
    }

    // --- Subscriptions

    @Test
    fun `a charge at a new price near the due date is a price change`() {
        val insights = evaluate(
            rows = listOf(tx("2026-11-15", "649", merchant = "STREAMER")),
            subscriptions = listOf(subscription(7, "499", "2026-11-15")),
        )
        val change = insights.facts<PriceChange>().single()
        assertEquals(Money(BigDecimal("499"), "INR"), change.tracked)
        assertEquals(Money(BigDecimal("649"), "INR"), change.charged)
    }

    @Test
    fun `a charge in another currency is not a price change`() {
        val insights = evaluate(
            rows = listOf(
                tx("2026-11-15", "649", merchant = "Streamer", currency = "USD"),
                tx("2026-11-15", "12", merchant = "Cloud", currency = "USD"),
            ),
            subscriptions = listOf(
                subscription(7, "499", "2026-11-15"),
                subscription(8, "10", "2026-11-15", currency = "USD", merchant = "Cloud"),
            ),
        )
        val change = insights.single { it.fact is PriceChange }
        assertEquals("USD", change.currency)
        assertEquals(Money(BigDecimal("12"), "USD"), (change.fact as PriceChange).charged)
    }

    @Test
    fun `the same price is not a change`() {
        val insights = evaluate(
            rows = listOf(tx("2026-11-15", "499", merchant = "Streamer")),
            subscriptions = listOf(subscription(7, "499", "2026-11-15")),
        )
        assertTrue(insights.facts<PriceChange>().isEmpty())
    }

    @Test
    fun `monthly subscription cost is totalled within a currency only`() {
        val subscriptions = listOf(
            subscription(1, "100", null),
            subscription(2, "1200", null, cycle = "Annual"),
            subscription(3, "10", null, currency = "USD"),
        )
        // A running figure for the summary, not a heads-up every cycle.
        assertEquals(emptyList<Insight>(), evaluate(subscriptions = subscriptions))
        val monthly = InsightRules.subscriptionsMonthly(subscriptions)
        assertEquals(setOf("INR", "USD"), monthly.keys)
        assertEquals(0, BigDecimal("200").compareTo(monthly.getValue("INR").amount))
        assertEquals(0, BigDecimal("10").compareTo(monthly.getValue("USD").amount))
    }

    @Test
    fun `bills due within a week, one line per currency`() {
        val insights = evaluate(
            subscriptions = listOf(
                subscription(2, "50", "2026-11-26", merchant = "Gym"),
                subscription(1, "100", "2026-11-22"),
                subscription(3, "10", "2026-11-25", currency = "USD"),
                subscription(4, "999", "2026-12-10"),
            ),
        )
        val bills = insights.filter { it.fact is BillsDue }.associate { it.currency to it.fact as BillsDue }
        assertEquals(
            BillsDue(2, Money(BigDecimal("150"), "INR"), LocalDate.parse("2026-11-22"), 7, listOf("Streamer", "Gym")),
            bills["INR"],
        )
        assertEquals(1, bills.getValue("USD").count)
    }

    @Test
    fun `bills due are on the dates the Subscriptions screen shows`() {
        val insights = evaluate(
            subscriptions = listOf(
                subscription(1, "100", "2026-11-20"),
                subscription(2, "50", "2026-10-22", merchant = "Gym"),
            ),
        )
        // Due today stays today; last month's unpaid date moves on to Nov 22, as on the screen.
        val bills = insights.single { it.fact is BillsDue }.fact as BillsDue
        assertEquals(LocalDate.parse("2026-11-20"), bills.firstDue)
        assertEquals(listOf("Streamer", "Gym"), bills.names)
    }

    @Test
    fun `a bill already charged is not due, even at a new price or before it was linked`() {
        val insights = evaluate(
            rows = listOf(
                tx("2026-11-19", "100", merchant = "STREAMER"),
                tx("2026-11-18", "65", merchant = "Gym"),
                // Last month's charge doesn't pay this one.
                tx("2026-10-24", "20", merchant = "Cloud"),
            ),
            subscriptions = listOf(
                subscription(1, "100", "2026-11-20"),
                subscription(2, "50", "2026-11-22", merchant = "Gym"),
                subscription(3, "20", "2026-11-24", merchant = "Cloud"),
            ),
        )
        val bills = insights.single { it.fact is BillsDue }.fact as BillsDue
        assertEquals(listOf("Cloud"), bills.names)
    }

    @Test
    fun `a card charge pays its bill even with card spending left out of expenses`() {
        // The review counts only EXPENSE as spending here (creditAsExpense = false).
        val insights = evaluate(
            rows = listOf(
                tx("2026-11-19", "100", type = TransactionType.CREDIT, merchant = "Streamer"),
                tx("2026-11-18", "649", type = TransactionType.CREDIT, merchant = "Cloud"),
            ),
            subscriptions = listOf(
                subscription(1, "100", "2026-11-20"),
                subscription(2, "499", "2026-11-18", merchant = "Cloud"),
            ),
        )
        assertTrue(insights.none { it.fact is BillsDue })
        assertEquals(Money(BigDecimal("649"), "INR"), insights.facts<PriceChange>().single().charged)
    }

    @Test
    fun `a charge pays one bill, on an account it could be from`() {
        val onA = subscription(1, "100", "2026-11-20", bank = "Bank A", last4 = "1111")
        val onB = subscription(2, "100", "2026-11-21", bank = "Bank B", last4 = "2222")
        fun dueAfter(vararg rows: TransactionWithSplits) =
            evaluate(rows = rows.toList(), subscriptions = listOf(onB, onA)).facts<BillsDue>().let { assertTrue(it.size <= 1); it.singleOrNull()?.firstDue }

        // Paid from Bank A: Bank B's bill is still due.
        assertEquals(LocalDate.parse("2026-11-21"), dueAfter(tx("2026-11-19", "100", merchant = "Streamer", bank = "Bank A", account = "1111")))
        // A charge that doesn't say its account, or names only the bank, pays neither: both stay due.
        fun dueCount(row: TransactionWithSplits) =
            evaluate(rows = listOf(row), subscriptions = listOf(onA, onB)).facts<BillsDue>().single().count
        assertEquals(2, dueCount(tx("2026-11-19", "100", merchant = "Streamer")))
        assertEquals(2, dueCount(tx("2026-11-19", "100", merchant = "Streamer", bank = "Bank A")))
        // A bill that names no account can be paid from any.
        assertTrue(evaluate(rows = listOf(tx("2026-11-19", "100", merchant = "Streamer", bank = "Bank A", account = "1111")), subscriptions = listOf(subscription(3, "100", "2026-11-20"))).facts<BillsDue>().isEmpty())
        // One for each pays both.
        assertEquals(
            null,
            dueAfter(
                tx("2026-11-19", "100", merchant = "Streamer", bank = "Bank B", account = "2222"),
                tx("2026-11-19", "100", merchant = "Streamer", bank = "Bank A", account = "1111"),
            ),
        )
    }

    @Test
    fun `a charge without its account is no new price for an account-tied bill, which stays due`() {
        val insights = evaluate(
            rows = listOf(tx("2026-11-19", "130", merchant = "Streamer", bank = "Bank A")),
            subscriptions = listOf(subscription(1, "100", "2026-11-20", bank = "Bank A", last4 = "1111")),
        )
        assertTrue(insights.facts<PriceChange>().isEmpty())
        assertEquals(
            BillsDue(1, Money(BigDecimal("100"), "INR"), LocalDate.parse("2026-11-20"), 7, listOf("Streamer")),
            insights.facts<BillsDue>().single(),
        )
    }

    @Test
    fun `a charge goes to the bill at its exact amount before another's new price`() {
        // Two plans at one merchant, on one account; only the 120 one has been charged.
        val plans = listOf(
            subscription(1, "100", "2026-11-22", bank = "Bank A", last4 = "1111"),
            subscription(2, "120", "2026-11-22", bank = "Bank A", last4 = "1111"),
        )
        val insights = evaluate(
            rows = listOf(tx("2026-11-19", "120", merchant = "Streamer", bank = "Bank A", account = "1111")),
            subscriptions = plans,
        )
        assertTrue(insights.facts<PriceChange>().isEmpty())
        assertEquals(Money(BigDecimal("100"), "INR"), insights.facts<BillsDue>().single().total)
    }

    @Test
    fun `a charge ingestion recorded against one bill does not also settle another`() = runBlocking {
        // Two bills at one merchant: one on an account, one that could be paid from any.
        val stored = mutableListOf(
            subscription(1, "100", "2026-11-19", bank = "Bank A", last4 = "1111"),
            subscription(2, "110", "2026-11-22"),
        )
        val dao = mockk<SubscriptionDao>()
        coEvery { dao.getSubscriptionsByStateList(SubscriptionState.ACTIVE) } answers { stored.toList() }
        coEvery { dao.getSubscriptionById(any()) } answers { stored.firstOrNull { it.id == firstArg<Long>() } }
        coEvery { dao.markPaid(any(), any(), any()) } answers {
            val i = stored.indexOfFirst { it.id == firstArg<Long>() }
            stored[i] = stored[i].copy(lastPaidAt = secondArg(), nextPaymentDate = thirdArg())
        }
        val repository = SubscriptionRepository(dao, mockk<CategoryDao>())

        // The SMS arrives: ingestion matches it to the first bill and moves that one on.
        val row = tx("2026-11-19", "100", merchant = "Streamer", bank = "Bank A", account = "1111")
        val matched = repository.matchTransactionToSubscription(row.transaction)
        assertEquals(1L, matched?.id)
        repository.recordCharge(matched!!, row.transaction.dateTime.toLocalDate())
        val ingested = row.copy(transaction = row.transaction.copy(isRecurring = true))

        // The second bill is still due, at its own price: one payment pays one bill.
        val insights = evaluate(rows = listOf(ingested), subscriptions = stored)
        assertEquals(
            BillsDue(1, Money(BigDecimal("110"), "INR"), LocalDate.parse("2026-11-22"), 7, listOf("Streamer")),
            insights.facts<BillsDue>().single(),
        )
        assertTrue(insights.facts<PriceChange>().isEmpty())
    }

    @Test
    fun `a bill that could take either charge leaves the one only another bill can use`() {
        // One bill names only the bank, the other only the account: Bank A/1111 suits both.
        val bills = listOf(
            subscription(1, "100", "2026-11-20", bank = "Bank A"),
            subscription(2, "100", "2026-11-21", last4 = "1111"),
        )
        val insights = evaluate(
            rows = listOf(
                tx("2026-11-19", "100", merchant = "Streamer", bank = "Bank A", account = "1111"),
                tx("2026-11-17", "100", merchant = "Streamer", bank = "Bank A", account = "2222"),
            ),
            subscriptions = bills,
        )
        assertTrue(insights.facts<BillsDue>().isEmpty())
    }

    @Test
    fun `a recorded payment stays its bill's after the bill's price is edited`() {
        // Bill 1 was paid 100 on the 19th and moved on; then its price was edited to 150.
        val edited = subscription(1, "150", "2026-12-19").copy(lastPaidAt = LocalDate.parse("2026-11-19"))
        val other = subscription(2, "100", "2026-11-22")
        val insights = evaluate(
            rows = listOf(tx("2026-11-19", "100", merchant = "Streamer", recurring = true)),
            subscriptions = listOf(edited, other),
        )
        assertEquals(LocalDate.parse("2026-11-22"), insights.facts<BillsDue>().single().firstDue)
    }

    @Test
    fun `a plan due months away does not keep another's new price from showing`() {
        // An annual plan at 649 isn't due till June: a 649 charge now is the monthly plan's new price.
        val insights = evaluate(
            rows = listOf(tx("2026-11-15", "649", merchant = "Streamer")),
            subscriptions = listOf(
                subscription(1, "499", "2026-11-15"),
                subscription(2, "649", "2027-06-10", cycle = "Annual"),
            ),
        )
        val change = insights.facts<PriceChange>().single()
        assertEquals(Money(BigDecimal("499"), "INR"), change.tracked)
        // Due around the charge, the same plan would claim it as its own payment.
        val dueNow = evaluate(
            rows = listOf(tx("2026-11-15", "649", merchant = "Streamer")),
            subscriptions = listOf(
                subscription(1, "499", "2026-11-15"),
                subscription(2, "649", "2026-11-14", cycle = "Annual"),
            ),
        )
        assertTrue(dueNow.facts<PriceChange>().isEmpty())
    }

    @Test
    fun `a new price is checked against the payment around the charge, not a stale stored date`() {
        val changes = evaluate(
            rows = listOf(tx("2026-11-15", "130", merchant = "Streamer")),
            subscriptions = listOf(subscription(1, "100", "2026-10-15")),
        ).facts<PriceChange>()
        assertEquals(Money(BigDecimal("130"), "INR"), changes.single().charged)
    }

    @Test
    fun `a new price is news only for the subscription on the charge's account`() {
        val changes = evaluate(
            rows = listOf(tx("2026-11-15", "649", merchant = "Streamer", bank = "Bank B", account = "2222")),
            subscriptions = listOf(
                subscription(1, "499", "2026-11-15", bank = "Bank A", last4 = "1111"),
                subscription(2, "499", "2026-11-15", bank = "Bank B", last4 = "2222"),
                subscription(3, "499", "2026-11-15"),
            ),
        ).filter { it.fact is PriceChange }
        assertEquals("price_change:v2:2026-11-01:all:INR:2", changes.single().id)
    }

    @Test
    fun `bills due move to a new insight once the first one has gone`() {
        val subscriptions = listOf(subscription(1, "100", "2026-11-22"), subscription(2, "50", "2026-11-26"))
        val before = evaluate(subscriptions = subscriptions).single { it.fact is BillsDue }
        val after = evaluate(subscriptions = subscriptions.drop(1)).single { it.fact is BillsDue }
        assertEquals("bills_due:v2:2026-11-01:all:INR:next7@1@2026-11-22,2@2026-11-26", before.id)
        assertTrue(before.id != after.id)
    }

    @Test
    fun `a bill newly due shows again after the others were dismissed`() {
        val first = subscription(1, "100", "2026-11-22")
        val before = evaluate(subscriptions = listOf(first)).single { it.fact is BillsDue }
        val after = evaluate(subscriptions = listOf(first, subscription(2, "50", "2026-11-26"))).single { it.fact is BillsDue }
        assertNotEquals(before.id, after.id)
    }

    @Test
    fun `another bill on a dismissed bill's date shows again`() {
        val before = evaluate(subscriptions = listOf(subscription(1, "100", "2026-11-22"))).single { it.fact is BillsDue }
        val after = evaluate(subscriptions = listOf(subscription(2, "100", "2026-11-22", merchant = "Gym"))).single { it.fact is BillsDue }
        assertNotEquals(before.id, after.id)
    }

    // --- Cards

    @Test
    fun `card use from half its limit is a warning, near the limit an alert`() {
        val insights = evaluate(
            cards = listOf(
                card("950", "1000", last4 = "1111"),
                card("600", "1000", last4 = "2222"),
                card("200", "1000", last4 = "3333"),
                card("900", null, last4 = "4444"),
            ),
        )
        val cards = insights.filter { it.fact is CardUtilisation }
        assertEquals(listOf(InsightSeverity.ALERT, InsightSeverity.WARNING), cards.map { it.severity })
        assertEquals(listOf(95, 60), cards.map { (it.fact as CardUtilisation).percent })
        assertEquals(InsightAction.OpenAccount("Test Bank", "1111"), cards.first().action)
    }

    @Test
    fun `dismissing a warning leaves a later alert, for cards and savings rate`() {
        fun cardId(balance: String) = evaluate(cards = listOf(card(balance, "1000"))).single { it.fact is CardUtilisation }.id
        assertNotEquals(cardId("550"), cardId("900"))
        fun rateId(spent: String) = evaluate(rows = salaryAndSpend(spent)).single { it.fact is SavingsRate }.id
        assertNotEquals(rateId("700"), rateId("1200"))
    }

    // --- Ordering and ids

    @Test
    fun `most severe first, with ids stable for the cycle`() {
        fun run() = evaluate(
            rows = salaryAndSpend("700"),
            budgets = listOf(budget(1, "1500"), budget(2, "3500")),
            cards = listOf(card("950", "1000")),
        )
        val insights = run()
        assertEquals(insights.map { it.severity }.sorted(), insights.map { it.severity })
        assertEquals(InsightSeverity.ALERT, insights.first().severity)
        assertEquals(InsightSeverity.GOOD, insights.last().severity)
        assertEquals("budget_over:v2:2026-11-01:all:INR:2@2026-11-01", insights.first().id)
        assertEquals(insights.map { it.id }, run().map { it.id })
    }

    @Test
    fun `each profile's insights have their own ids, so a dismissal stays in its profile`() {
        val cards = listOf(card("950", "1000"))
        fun idIn(profileId: Long?) =
            InsightRules.evaluate(InsightInputs(today, review(emptyList()), creditCards = cards, profileId = profileId)).single().id
        assertEquals("card_use:v2:2026-11-01:all:INR:Test Bank_1111:ALERT", idIn(null))
        assertEquals("card_use:v2:2026-11-01:p1:INR:Test Bank_1111:ALERT", idIn(1))
        assertEquals("card_use:v2:2026-11-01:p2:INR:Test Bank_1111:ALERT", idIn(2))
        assertEquals(setOf("2026-11-01"), listOf(idIn(null), idIn(1), idIn(2)).map(::insightCycleOf).toSet())
    }
}
