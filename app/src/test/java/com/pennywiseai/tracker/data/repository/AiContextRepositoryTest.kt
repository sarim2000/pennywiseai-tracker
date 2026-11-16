package com.pennywiseai.tracker.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.dao.ExchangeRateDao
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.ExchangeRateEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.ledger.breakdownByCurrency
import com.pennywiseai.tracker.data.ledger.convertedSpent
import com.pennywiseai.tracker.data.ledger.forTotals
import com.pennywiseai.tracker.data.model.SpendingComparison
import com.pennywiseai.tracker.data.preferences.CurrencyPreferences
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The chat's figures are Home's: same period, profile, card setting, refunds and forgiven loans. */
class AiContextRepositoryTest {

    // Each case its own empty preferences file: the app's DataStore is one per process.
    @get:Rule
    val tmp = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val today = LocalDate.of(2026, 11, 16)
    private val rows = mutableListOf<TransactionWithSplits>()
    private val accounts = mutableListOf<AccountBalanceEntity>()
    private var forgiven: Map<String, BigDecimal> = emptyMap()
    private var forgivenSource: TransactionEntity? = null
    private var nextId = 1L
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var repository: AiContextRepository

    // The app's own conversion, rounding included, with one JPY worth 0.0201 USD.
    private val rated = CurrencyConversionService(
        mockk<ExchangeRateDao> {
            coEvery { getExchangeRate("JPY", "USD", any()) } returns ExchangeRateEntity(
                fromCurrency = "JPY", toCurrency = "USD", rate = BigDecimal("0.0201"), provider = "test",
                updatedAt = LocalDateTime.now(), expiresAt = LocalDateTime.now().plusDays(1)
            )
        },
        mockk(relaxed = true),
        mockk<CurrencyPreferences>(relaxed = true)
    )

    // USD has a rate, XYZ never had one.
    private val conversion = mockk<CurrencyConversionService> {
        coEvery { convertAmountOrNull(any(), any(), any(), any()) } coAnswers {
            val (amount, from, to) = Triple(firstArg<BigDecimal>(), secondArg<String>(), thirdArg<String>())
            when {
                from == to -> amount
                from == "USD" && to == "INR" -> amount * BigDecimal("80")
                from == "JPY" && to == "USD" -> rated.convertAmountOrNull(amount, from, to)
                else -> null
            }
        }
    }

    @Before
    fun setUp() = runBlocking {
        preferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(tmp.root, "user_preferences.preferences_pb") })
        ) {}
        preferences.updateBaseCurrency("INR")
        repository = AiContextRepository(
            // Room's query: every row from start to end, inclusive.
            transactionSplitDao = mockk<TransactionSplitDao> {
                every { getTransactionsWithSplitsAllCurrencies(any(), any()) } answers {
                    val (start, end) = firstArg<LocalDateTime>() to secondArg<LocalDateTime>()
                    flowOf(rows.filter { !it.transaction.dateTime.isBefore(start) && !it.transaction.dateTime.isAfter(end) })
                }
            },
            userPreferencesRepository = preferences,
            currencyConversionService = conversion,
            loanRepository = mockk {
                every { getLentLoansSettledInPeriod(any(), any()) } returns flowOf(emptyList())
                // As LoanRepository: a forgiven balance counts once its source transaction passes the filter.
                coEvery { settlementLossByCurrency(any(), any()) } answers {
                    val keep = secondArg<(TransactionEntity) -> Boolean>()
                    if (forgivenSource?.let(keep) != false) forgiven else emptyMap()
                }
            },
            categoryDao = mockk {
                coEvery { getAllCategoriesList() } returns listOf(
                    CategoryEntity(id = 1, name = "Food", color = "#000"),
                    CategoryEntity(id = 2, name = "Coffee", color = "#000", parentId = 1),
                    CategoryEntity(id = 3, name = "Travel", color = "#000")
                )
            },
            accountBalanceDao = mockk { coEvery { getAllLatestBalancesOnce() } answers { accounts.toList() } },
            profileDao = mockk { coEvery { getById(ProfileEntity.BUSINESS_ID) } returns ProfileEntity(ProfileEntity.BUSINESS_ID, "Business", "#000") }
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun tx(
        amount: String,
        currency: String = "INR",
        type: TransactionType = TransactionType.EXPENSE,
        category: String = "Food",
        at: LocalDateTime = today.atTime(9, 0),
        splits: List<Pair<String, String>> = emptyList(),
        refundTo: String? = null,
        lent: String? = null,
        loanId: Long? = null,
        loanPart: String? = null,
        excluded: Boolean = false,
        account: String? = null,
        profileId: Long? = null,
    ): TransactionWithSplits {
        val id = nextId++
        val row = TransactionWithSplits(
            TransactionEntity(
                id = id, amount = BigDecimal(amount), merchantName = "Shop", category = category, transactionType = type,
                dateTime = at, transactionHash = "h$id", currency = currency, excludedFromAnalytics = excluded,
                lentSharesTotal = lent?.let(::BigDecimal), loanId = loanId, loanContribution = loanPart?.let(::BigDecimal),
                budgetCategory = refundTo, budgetImpactType = refundTo?.let { BudgetImpactType.DEDUCT_SPENT },
                bankName = account?.let { "Bank" }, accountNumber = account, profileId = profileId
            ),
            splits.map { (cat, amt) -> TransactionSplitEntity(transactionId = id, category = cat, amount = BigDecimal(amt)) }
        )
        rows += row
        return row
    }

    private fun amounts(map: Map<String, BigDecimal>) = map.mapValues { it.value.stripTrailingZeros().toPlainString() }

    @Test
    fun `spending is Home's breakdown, per currency, with the card setting honoured`() = runBlocking {
        tx("1000", lent = "400") // a dinner with ₹400 lent: ₹600 is yours
        tx("500", loanId = 7, loanPart = "500") // lent in full: none of it
        tx("300", loanId = 8, loanPart = "100") // ₹100 of it lent
        tx("200", type = TransactionType.CREDIT)
        tx("100", type = TransactionType.INCOME, category = "Refund", refundTo = "Food")
        tx("5000", type = TransactionType.INCOME, category = "Salary")
        tx("5", currency = "USD")
        tx("900", excluded = true)
        tx("20", type = TransactionType.TRANSFER)

        val off = repository.spending(today)
        assertEquals(mapOf("INR" to "700", "USD" to "5"), amounts(off.spent))
        assertEquals(mapOf("INR" to "5000"), amounts(off.income))
        // The rows the figures add up: not the loan lent in full, the excluded one, the transfer or the uncounted card purchase.
        assertEquals(5, off.transactionCount)
        // Exactly what Home's card works out from the same rows.
        val home = breakdownByCurrency(rows.map { it.transaction }.forTotals())
        assertEquals(home.mapValues { it.value.expenses.stripTrailingZeros().toPlainString() }, amounts(off.spent))
        assertNull(off.estimate)

        preferences.setCountCreditCardAsExpense(true)
        val on = repository.spending(today)
        assertEquals(mapOf("INR" to "900", "USD" to "5"), amounts(on.spent))
        assertEquals(6, on.transactionCount)
    }

    @Test
    fun `refunds beyond the spending floor at zero and the currency drops out`() = runBlocking {
        tx("100")
        tx("250", type = TransactionType.INCOME, refundTo = "Food")
        tx("40", currency = "USD")
        val p = repository.spending(today)
        assertEquals(mapOf("USD" to "40"), amounts(p.spentWithForgiven))
        // A refund isn't income.
        assertTrue(p.income.isEmpty())
    }

    @Test
    fun `a forgiven loan alone is spending, shown apart from what it adds to`() = runBlocking {
        forgiven = mapOf("INR" to BigDecimal("400"))
        val p = repository.spending(today)
        assertTrue(p.spent.isEmpty())
        assertEquals(mapOf("INR" to "400"), amounts(p.forgiven))
        assertEquals(mapOf("INR" to "400"), amounts(p.spentWithForgiven))
    }

    @Test
    fun `the period is Home's budget cycle up to the end of today`() = runBlocking {
        preferences.updateBudgetCycleStartDay(25)
        tx("1", at = LocalDateTime.of(2026, 10, 24, 23, 59))
        tx("10", at = LocalDateTime.of(2026, 10, 25, 0, 0))
        tx("100", at = today.atTime(23, 30))
        tx("1000", at = today.plusDays(1).atTime(0, 0))
        val p = repository.spending(today)
        assertEquals(mapOf("INR" to "110"), amounts(p.spent))
        assertEquals(SpendingComparison.Window(LocalDateTime.of(2026, 10, 25, 0, 0), today.atTime(LocalTime.MAX)), p.scope.window)
        assertNull(p.scope.profile)
    }

    @Test
    fun `only the profile Home shows counts, its name on the answer`() = runBlocking {
        preferences.updateSelectedProfileId(ProfileEntity.BUSINESS_ID)
        accounts += AccountBalanceEntity(bankName = "Bank", accountLast4 = "1111", balance = BigDecimal.ZERO, timestamp = today.atStartOfDay(), profileId = ProfileEntity.BUSINESS_ID)
        tx("100", account = "1111") // the business account's
        tx("20", profileId = ProfileEntity.BUSINESS_ID) // set on the transaction itself
        tx("7") // no account: Personal
        forgivenSource = tx("0", account = "2222").transaction // a Personal loan's source: not this profile's
        forgiven = mapOf("INR" to BigDecimal("400"))
        val p = repository.spending(today)
        assertEquals(mapOf("INR" to "120"), amounts(p.spentWithForgiven))
        assertEquals(ProfileEntity.BUSINESS_ID, p.scope.profile?.id)
    }

    @Test
    fun `a converted estimate only in unified mode, leaving out currencies with no rate`() = runBlocking {
        tx("100")
        tx("10", currency = "USD")
        tx("7", currency = "XYZ")
        assertNull(repository.spending(today).estimate)
        preferences.setUnifiedCurrencyMode(true)
        preferences.setDisplayCurrency("INR")
        val e = repository.spending(today).estimate!!
        assertEquals("INR", e.currency)
        assertEquals(0, BigDecimal("900").compareTo(e.total))
        assertEquals(mapOf("XYZ" to "7"), amounts(e.leftOut))
    }

    @Test
    fun `the converted estimate rounds as Home's card does, spending and forgiven loans apart`() = runBlocking {
        tx("50", currency = "JPY")
        forgiven = mapOf("JPY" to BigDecimal("50"))
        preferences.setUnifiedCurrencyMode(true)
        preferences.setDisplayCurrency("USD")
        val e = repository.spending(today).estimate!!
        // Each 50 is 1.005, rounded to 1.01: Home shows 2.02, where 100 converted at once is 2.01.
        assertEquals(BigDecimal("2.02"), e.total)
        val home = convertedSpent(mapOf("JPY" to BigDecimal("50")), forgiven, "USD") { a, from, to -> conversion.convertAmountOrNull(a, from, to) }
        assertEquals(home, e)
    }

    @Test
    fun `no estimate when no currency spent has a rate`() = runBlocking {
        tx("100")
        tx("7", currency = "XYZ")
        preferences.setUnifiedCurrencyMode(true)
        // Neither INR nor XYZ converts to USD: "about $0 in all" would be false.
        preferences.setDisplayCurrency("USD")
        assertNull(repository.spending(today).estimate)
    }

    @Test
    fun `a category takes its subcategories, split shares and refunds, per currency`() = runBlocking {
        tx("10000", category = "Others", splits = listOf("Others" to "7000", "Food" to "3000"))
        tx("200", category = "Coffee")
        tx("50", type = TransactionType.INCOME, refundTo = "Coffee")
        tx("9", currency = "USD")
        tx("400", category = "Travel")
        val food = repository.categorySpending("food", today)
        assertEquals(mapOf("INR" to "3150", "USD" to "9"), amounts(food.byCurrency))
        assertEquals(3, food.transactionCount)
        // ₹3,150 of the ₹10,550 spent in rupees.
        assertEquals(3150f / 10550f, food.share!!, 0.0001f)
        assertTrue(repository.categorySpending("Pets", today).byCurrency.isEmpty())
    }

    @Test
    fun `a category refunded in full is nothing spent`() = runBlocking {
        tx("200", category = "Travel")
        tx("200", type = TransactionType.INCOME, refundTo = "Travel")
        tx("9", category = "Travel", currency = "USD")
        assertEquals(mapOf("USD" to "9"), amounts(repository.categorySpending("Travel", today).byCurrency))
        rows.removeAt(rows.lastIndex)
        assertTrue(repository.categorySpending("Travel", today).byCurrency.isEmpty())
    }

    @Test
    fun `top categories rank per currency and keep forgiven loans out of the ranking`() = runBlocking {
        tx("300", category = "Travel")
        tx("100")
        forgiven = mapOf("INR" to BigDecimal("400"))
        val top = repository.topCategories(today)
        assertEquals(listOf("Travel", "Food"), top.ranking.getValue("INR").map { it.first })
        assertEquals(mapOf("INR" to "400"), amounts(top.forgiven))
        assertEquals(0.75f, top.share!!, 0.0001f)
    }

    @Test
    fun `a comparison runs to the end of today, as Home does, and the same point last cycle`() = runBlocking {
        tx("100", at = today.atTime(23, 30))
        tx("40", at = today.minusMonths(1).atTime(23, 30))
        tx("1000", at = today.minusMonths(1).plusDays(1).atTime(0, 0))
        val result = repository.compareSpending(SpendingComparison.Period.MONTH, null, creditAsExpense = false, today = today) as SpendingComparison.Result.Rows
        assertEquals(repository.spending(today).scope.window, result.current)
        assertEquals(today.minusMonths(1).atTime(LocalTime.MAX), result.previous.end)
        assertEquals(listOf("100" to "40"), result.rows.map { it.current.stripTrailingZeros().toPlainString() to it.previous.stripTrailingZeros().toPlainString() })
    }

    @Test
    fun `comparing a parent counts its children, whatever casing the model used`() = runBlocking {
        val now = LocalDateTime.now()
        tx("10", at = now.withHour(0).withMinute(0))
        tx("10", category = "Coffee", at = now.withHour(0).withMinute(0))
        tx("10", category = "Travel", at = now.withHour(0).withMinute(0))
        tx("5", at = now.minusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0))
        val result = repository.compareSpending(SpendingComparison.Period.MONTH, "food", creditAsExpense = false)
        assertEquals(BigDecimal("20"), (result as SpendingComparison.Result.Rows).rows.single().current)
    }
}
