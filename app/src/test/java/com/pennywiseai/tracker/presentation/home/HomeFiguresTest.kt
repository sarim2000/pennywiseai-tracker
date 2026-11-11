package com.pennywiseai.tracker.presentation.home

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.TransactionRepository.MonthlyBreakdown
import com.pennywiseai.tracker.data.ledger.forTotals
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class HomeFiguresTest {
    private fun account(balance: String, currency: String, limit: String? = null, card: Boolean = false) =
        AccountBalanceEntity(
            bankName = "Bank",
            accountLast4 = "0000",
            balance = BigDecimal(balance),
            timestamp = LocalDateTime.of(2026, 10, 1, 12, 0),
            currency = currency,
            creditLimit = limit?.let(::BigDecimal),
            isCreditCard = card,
        )

    @Test fun `balance never adds another currency`() {
        val t = balanceTotals(
            accounts = listOf(account("500", "INR"), account("10", "USD")),
            creditCards = listOf(account("100", "INR", "1000", card = true), account("5", "USD", "50", card = true)),
            currency = "INR",
        )
        assertEquals(0, BigDecimal("500").compareTo(t.balance))
        assertEquals(0, BigDecimal("900").compareTo(t.availableCredit))
        assertTrue(t.hasOtherCurrencies)
    }

    @Test fun `single currency is not flagged`() =
        assertFalse(balanceTotals(listOf(account("1", "INR")), emptyList(), "INR").hasOtherCurrencies)

    // --- breakdownByCurrency ---

    private fun assertBreakdown(total: String, income: String, expenses: String, actual: MonthlyBreakdown?) {
        requireNotNull(actual)
        assertAmount(total, actual.total)
        assertAmount(income, actual.income)
        assertAmount(expenses, actual.expenses)
    }

    @Test fun `refund nets off spend and is not income`() {
        val b = breakdownByCurrency(
            listOf(
                tx("1000", TransactionType.EXPENSE),
                tx("200", TransactionType.INCOME, impact = BudgetImpactType.DEDUCT_SPENT),
                tx("5000", TransactionType.INCOME),
            )
        )
        assertBreakdown(total = "4200", income = "5000", expenses = "800", actual = b["INR"])
    }

    @Test fun `extra budget income stays income`() {
        val b = breakdownByCurrency(
            listOf(
                tx("100", TransactionType.EXPENSE),
                tx("300", TransactionType.INCOME, impact = BudgetImpactType.ADD_TO_LIMIT),
            )
        )
        assertBreakdown(total = "200", income = "300", expenses = "100", actual = b["INR"])
    }

    @Test fun `refunds beyond spend floor spend at zero`() {
        val b = breakdownByCurrency(
            listOf(
                tx("100", TransactionType.EXPENSE),
                tx("250", TransactionType.INCOME, impact = BudgetImpactType.DEDUCT_SPENT),
                tx("50", TransactionType.INCOME),
            )
        )
        assertBreakdown(total = "50", income = "50", expenses = "0", actual = b["INR"])
    }

    @Test fun `card spend counts only when opted in`() {
        val rows = listOf(tx("100", TransactionType.EXPENSE), tx("400", TransactionType.CREDIT))
        assertBreakdown(total = "-100", income = "0", expenses = "100", actual = breakdownByCurrency(rows)["INR"])
        assertBreakdown(
            total = "-500", income = "0", expenses = "500",
            actual = breakdownByCurrency(rows, countCreditAsExpense = true)["INR"],
        )
    }

    @Test fun `currencies are never added together`() {
        val b = breakdownByCurrency(
            listOf(
                tx("500", TransactionType.EXPENSE, currency = "INR"),
                tx("10", TransactionType.EXPENSE, currency = "USD"),
                tx("20", TransactionType.INCOME, currency = "USD"),
            )
        )
        assertEquals(setOf("INR", "USD"), b.keys)
        assertBreakdown(total = "-500", income = "0", expenses = "500", actual = b["INR"])
        assertBreakdown(total = "10", income = "20", expenses = "10", actual = b["USD"])
    }

    @Test fun `excluded and fully loan-linked rows don't count, a partly lent bill counts its own share`() {
        val b = breakdownByCurrency(
            listOf(
                tx("999", TransactionType.EXPENSE, excluded = true),
                tx("1000", TransactionType.EXPENSE, loanId = 1),
                tx("1000", TransactionType.EXPENSE, loanId = 2, loanContribution = "400"),
            )
        )
        assertBreakdown(total = "-600", income = "0", expenses = "600", actual = b["INR"])
    }

    @Test fun `transfers and investments are neither income nor spend`() {
        val b = breakdownByCurrency(
            listOf(tx("100", TransactionType.TRANSFER), tx("200", TransactionType.INVESTMENT))
        )
        assertBreakdown(total = "0", income = "0", expenses = "0", actual = b["INR"])
    }

    @Test fun `no transactions give no currencies`() =
        assertTrue(breakdownByCurrency(emptyList()).isEmpty())

    // --- combinedBreakdown / combinedTotal ---

    private val usdToInr = fakeConvert(mapOf("USD>INR" to "83"))

    @Test fun `combined breakdown converts each currency and skips one with no rate`() = runTest {
        val combined = combinedBreakdown(
            mapOf(
                "INR" to MonthlyBreakdown(total = BigDecimal("100"), income = BigDecimal("300"), expenses = BigDecimal("200")),
                "USD" to MonthlyBreakdown(total = BigDecimal("10"), income = BigDecimal("20"), expenses = BigDecimal("10")),
                "XYZ" to MonthlyBreakdown(total = BigDecimal("7"), income = BigDecimal("9"), expenses = BigDecimal("2")),
            ),
            "INR",
            usdToInr,
        )
        assertBreakdown(total = "930", income = "1960", expenses = "1030", actual = combined)
    }

    @Test fun `combined breakdown adds zero for a figure that alone fails to convert`() = runTest {
        // The total converts, so the currency counts; its income has no conversion and adds nothing.
        val incomeFails: ConvertAmount = { amount, from, to ->
            if (amount.compareTo(BigDecimal("20")) == 0) null else usdToInr(amount, from, to)
        }
        val combined = combinedBreakdown(
            mapOf("USD" to MonthlyBreakdown(total = BigDecimal("10"), income = BigDecimal("20"), expenses = BigDecimal("10"))),
            "INR",
            incomeFails,
        )
        assertBreakdown(total = "830", income = "0", expenses = "830", actual = combined)
    }

    @Test fun `combined breakdown of nothing is zero`() = runTest {
        assertBreakdown(total = "0", income = "0", expenses = "0", actual = combinedBreakdown(emptyMap(), "INR", usdToInr))
    }

    @Test fun `combined total keeps the target as is and skips a never-rated pair`() = runTest {
        val total = combinedTotal(
            mapOf("INR" to BigDecimal("100"), "USD" to BigDecimal("2"), "XYZ" to BigDecimal("5")),
            "INR",
            usdToInr,
        )
        assertAmount("266", total)
        assertAmount("0", combinedTotal(emptyMap(), "INR", usdToInr))
    }

    // --- moneyInMotion ---

    @Test fun `transfer pair counts once`() {
        val motion = moneyInMotion(
            listOf(
                tx("500", TransactionType.TRANSFER, transferPairId = "pair-1"),
                tx("500", TransactionType.TRANSFER, transferPairId = "pair-1"),
                tx("70", TransactionType.TRANSFER),
            )
        ) { it.amount }
        assertAmount("570", motion.transfer)
    }

    @Test fun `a lone leg of a pair still counts`() {
        // Native mode filters to one currency first, which can leave one leg of a pair.
        val motion = moneyInMotion(listOf(tx("500", TransactionType.TRANSFER, transferPairId = "pair-1"))) { it.amount }
        assertAmount("500", motion.transfer)
    }

    @Test fun `income and expense are not motion`() {
        val motion = moneyInMotion(
            listOf(
                tx("100", TransactionType.INCOME),
                tx("50", TransactionType.EXPENSE),
                tx("30", TransactionType.CREDIT),
                tx("70", TransactionType.INVESTMENT),
            )
        ) { it.amount }
        assertEquals(MotionTotals(BigDecimal("30"), BigDecimal.ZERO, BigDecimal("70")), motion)
    }

    @Test fun `row with no amount is skipped, and every row is asked for one`() = runTest {
        val convert = fakeConvert(mapOf("USD>INR" to "83"))
        val asked = mutableListOf<String>()
        val motion = moneyInMotion(
            listOf(
                tx("10", TransactionType.CREDIT, currency = "USD"),
                tx("5", TransactionType.CREDIT, currency = "XYZ"),
                tx("40", TransactionType.EXPENSE, currency = "USD"),
            )
        ) {
            asked += it.currency
            convert(it.amount, it.currency, "INR")
        }
        assertAmount("830", motion.credit)
        assertEquals(listOf("USD", "XYZ", "USD"), asked)
    }

    @Test fun `excluded rows are dropped by forTotals before the call`() {
        val motion = moneyInMotion(
            listOf(tx("30", TransactionType.CREDIT, excluded = true), tx("20", TransactionType.CREDIT)).forTotals()
        ) { it.amount }
        assertAmount("20", motion.credit)
    }

    // --- samePointCutoff ---

    private fun d(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    @Test fun `cutoff is as many days into the previous cycle`() =
        assertEquals(d(2, 10), samePointCutoff(d(2, 1) to d(2, 28), cycleStart = d(3, 1), today = d(3, 10)))

    @Test fun `cutoff stops at the previous cycle's end`() {
        assertEquals(d(2, 28), samePointCutoff(d(2, 1) to d(2, 28), cycleStart = d(3, 1), today = d(3, 31)))
        assertEquals(
            d(2, 29, 2028),
            samePointCutoff(d(2, 1, 2028) to d(2, 29, 2028), cycleStart = d(3, 1, 2028), today = d(3, 31, 2028)),
        )
    }

    @Test fun `cutoff in a cycle that starts on the 25th`() =
        assertEquals(d(10, 4), samePointCutoff(d(9, 25) to d(10, 24), cycleStart = d(10, 25), today = d(11, 3)))

    @Test fun `today before cycle start clamps to the previous start`() =
        assertEquals(d(2, 1), samePointCutoff(d(2, 1) to d(2, 28), cycleStart = d(3, 1), today = d(2, 27)))

    // --- convertAccounts ---

    private val usdRate: suspend (String, String) -> BigDecimal? = { from, to ->
        if (from == "USD" && to == "INR") BigDecimal("83.5") else null
    }

    @Test fun `native mode returns the accounts untouched`() = runTest {
        val accounts = listOf(account("10", "USD"))
        assertSame(accounts, convertAccounts(accounts, "INR", isUnifiedMode = false, rateOf = usdRate))
    }

    @Test fun `same-currency account is not converted`() = runTest {
        val inr = account("500", "INR")
        assertSame(inr, convertAccounts(listOf(inr), "INR", isUnifiedMode = true, rateOf = usdRate).single())
    }

    @Test fun `rated account converts balance and limit at full precision`() = runTest {
        val card = account("400.123", "USD", limit = "1000.01", card = true)
        val converted = convertAccounts(listOf(card), "INR", isUnifiedMode = true, rateOf = usdRate).single()
        assertEquals("INR", converted.currency)
        assertEquals(BigDecimal("33410.2705"), converted.balance)
        assertEquals(BigDecimal("83500.835"), converted.creditLimit)
    }

    @Test fun `unrated account keeps its own currency`() = runTest {
        // #545: relabelling an unconverted $400 as the display currency misstates it.
        val xyz = account("400", "XYZ")
        assertSame(xyz, convertAccounts(listOf(xyz), "INR", isUnifiedMode = true, rateOf = usdRate).single())
    }

    @Test fun `null credit limit stays null`() = runTest {
        val converted = convertAccounts(listOf(account("10", "USD")), "INR", isUnifiedMode = true, rateOf = usdRate).single()
        assertNull(converted.creditLimit)
        assertAmount("835", converted.balance)
    }
}
