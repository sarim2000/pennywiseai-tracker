package com.pennywiseai.tracker.data.repository

import android.content.Context
import android.content.ContextWrapper
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.currency.ExchangeRateProvider
import com.pennywiseai.tracker.data.database.dao.ExchangeRateDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.ExchangeRateEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import kotlin.test.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class AiContextRepositoryTest {

    private lateinit var transactionDao: TransactionDao
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var currencyConversionService: CurrencyConversionService
    private lateinit var repository: AiContextRepository

    private var transactionsList = mutableListOf<TransactionEntity>()

    // Zero-dependency stub context using concrete ContextWrapper to satisfy compiler
    class FakeContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File {
            val tempDir = File(System.getProperty("java.io.tmpdir"), "pennywise_test_files_" + System.currentTimeMillis())
            tempDir.mkdirs()
            return tempDir
        }
    }

    @Before
    fun setUp() = runBlocking {
        transactionsList.clear()

        // Create JDK dynamic proxies for the DAOs to avoid any external mocking dependencies
        transactionDao = Proxy.newProxyInstance(
            TransactionDao::class.java.classLoader,
            arrayOf(TransactionDao::class.java)
        ) { _, method, _ ->
            if (method.name == "getTransactionsBetweenDatesList") {
                transactionsList
            } else {
                null
            }
        } as TransactionDao

        val fakeExchangeRateDao = Proxy.newProxyInstance(
            ExchangeRateDao::class.java.classLoader,
            arrayOf(ExchangeRateDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getExchangeRate" -> {
                    val from = args[0] as String
                    val to = args[1] as String
                    val rate = when (from.uppercase()) {
                        "USD" -> BigDecimal("80.00")
                        "EUR" -> BigDecimal("90.00")
                        else -> BigDecimal("1.00")
                    }
                    ExchangeRateEntity(
                        id = 1,
                        fromCurrency = from,
                        toCurrency = to,
                        rate = rate,
                        provider = "Fake",
                        updatedAt = LocalDateTime.now(),
                        updatedAtUnix = System.currentTimeMillis() / 1000,
                        expiresAt = LocalDateTime.now().plusDays(1),
                        expiresAtUnix = (System.currentTimeMillis() / 1000) + 86400
                    )
                }
                "hasValidRate" -> 1
                "getExchangeRateIgnoringExpiry" -> null
                else -> null
            }
        } as ExchangeRateDao

        val fakeExchangeRateProvider = Proxy.newProxyInstance(
            ExchangeRateProvider::class.java.classLoader,
            arrayOf(ExchangeRateProvider::class.java)
        ) { _, _, _ -> null } as ExchangeRateProvider

        userPreferencesRepository = UserPreferencesRepository(FakeContext()) {}
        userPreferencesRepository.updateBaseCurrency("INR")

        currencyConversionService = CurrencyConversionService(
            exchangeRateDao = fakeExchangeRateDao,
            exchangeRateProvider = fakeExchangeRateProvider,
            userPreferencesRepository = userPreferencesRepository
        )

        repository = AiContextRepository(
            transactionDao = transactionDao,
            // Unused by these tests: only compareSpending reads splits.
            transactionSplitDao = Proxy.newProxyInstance(
                com.pennywiseai.tracker.data.database.dao.TransactionSplitDao::class.java.classLoader,
                arrayOf(com.pennywiseai.tracker.data.database.dao.TransactionSplitDao::class.java)
            ) { _, _, _ -> error("unused") } as com.pennywiseai.tracker.data.database.dao.TransactionSplitDao,
            userPreferencesRepository = userPreferencesRepository,
            currencyConversionService = currencyConversionService,
            loanRepository = io.mockk.mockk(),
            categoryDao = io.mockk.mockk(relaxed = true)
        )
    }

    @Test
    fun `getMonthSummary converts multiple currencies to base currency correctly`() = runBlocking {
        // Set up test transactions:
        // 1. Income: 100 USD (should be 8000 INR)
        // 2. Expense: 50 USD (should be 4000 INR)
        // 3. Credit (Expense): 1000 INR (should be 1000 INR)
        transactionsList.addAll(listOf(
            createTransaction(amount = BigDecimal("100.00"), currency = "USD", type = TransactionType.INCOME),
            createTransaction(amount = BigDecimal("50.00"), currency = "USD", type = TransactionType.EXPENSE),
            createTransaction(amount = BigDecimal("1000.00"), currency = "INR", type = TransactionType.CREDIT)
        ))

        val summary = repository.getMonthSummary()

        // totalIncome = 100 USD * 80 = 8000 INR
        assertEquals(BigDecimal("8000.00"), summary.totalIncome)
        // totalExpense = (50 USD * 80) + 1000 INR = 4000 + 1000 = 5000 INR
        assertEquals(BigDecimal("5000.00"), summary.totalExpense)
        assertEquals(3, summary.transactionCount)
    }

    @Test
    fun `getMonthSummary excludes loan-linked transactions`() = runBlocking {
        transactionsList.addAll(listOf(
            createTransaction(amount = BigDecimal("100.00"), currency = "INR", type = TransactionType.INCOME),
            createTransaction(amount = BigDecimal("50.00"), currency = "INR", type = TransactionType.EXPENSE),
            // Loan disbursement (lent out) and repayment (received back) — must not count as real spend/income.
            createTransaction(amount = BigDecimal("500.00"), currency = "INR", type = TransactionType.EXPENSE, loanId = 1L),
            createTransaction(amount = BigDecimal("500.00"), currency = "INR", type = TransactionType.INCOME, loanId = 1L)
        ))

        val summary = repository.getMonthSummary()

        assertEquals(BigDecimal("100.00"), summary.totalIncome)
        assertEquals(BigDecimal("50.00"), summary.totalExpense)
        assertEquals(2, summary.transactionCount)
    }

    @Test
    fun `getCategorySpending aggregates and calculates percentages using base currency`() = runBlocking {
        transactionsList.addAll(listOf(
            createTransaction(amount = BigDecimal("10.00"), currency = "USD", category = "Food", type = TransactionType.EXPENSE),
            createTransaction(amount = BigDecimal("20.00"), currency = "USD", category = "Food", type = TransactionType.EXPENSE),
            createTransaction(amount = BigDecimal("1000.00"), currency = "INR", category = "Transport", type = TransactionType.EXPENSE)
        ))

        val foodCat = repository.getCategorySpending("Food")
        val transportCat = repository.getCategorySpending("Transport")

        // Total Food = 10 USD + 20 USD = 30 USD -> 2400 INR
        // Total Transport = 1000 INR
        // Total Spending = 3400 INR
        // Food % = 2400 / 3400 = 70.588... % -> 70.59%
        // Transport % = 1000 / 3400 = 29.41%

        assertNotNull(foodCat)
        assertEquals(BigDecimal("2400.00"), foodCat.amount)
        assertEquals(70.59f, foodCat.percentage, 0.01f)

        assertNotNull(transportCat)
        assertEquals(BigDecimal("1000.00"), transportCat.amount)
        assertEquals(29.41f, transportCat.percentage, 0.01f)
    }

    @Test
    fun `getCategorySpending excludes loan-linked transactions`() = runBlocking {
        transactionsList.addAll(listOf(
            createTransaction(amount = BigDecimal("100.00"), currency = "INR", category = "Food", type = TransactionType.EXPENSE),
            // Money lent out, categorized as "Food" by mistake or not — must not inflate the category total.
            createTransaction(amount = BigDecimal("900.00"), currency = "INR", category = "Food", type = TransactionType.EXPENSE, loanId = 1L)
        ))

        val foodCat = repository.getCategorySpending("Food")
        assertNotNull(foodCat)
        assertEquals(BigDecimal("100.00"), foodCat.amount)
        assertEquals(100.0f, foodCat.percentage, 0.01f)
    }

    private fun createTransaction(
        amount: BigDecimal,
        currency: String,
        merchant: String = "Merchant",
        category: String = "Food",
        type: TransactionType = TransactionType.EXPENSE,
        date: LocalDateTime = LocalDateTime.now(),
        loanId: Long? = null
    ): TransactionEntity {
        return TransactionEntity(
            id = (1..100000).random().toLong(),
            amount = amount,
            merchantName = merchant,
            category = category,
            transactionType = type,
            dateTime = date,
            currency = currency,
            transactionHash = "hash_" + (1..100000).random(),
            loanId = loanId
        )
    }

    @Test
    fun `comparing a parent counts its children, whatever casing the model used`() = runBlocking {
        val at = LocalDateTime.now()
        fun row(id: Long, category: String) = com.pennywiseai.tracker.data.database.entity.TransactionWithSplits(
            TransactionEntity(id = id, amount = BigDecimal("10"), merchantName = "Shop", category = category, transactionType = TransactionType.EXPENSE, dateTime = at, transactionHash = "h$id", currency = "INR"),
            emptyList()
        )
        val parentAndChild = AiContextRepository(
            transactionDao = transactionDao,
            transactionSplitDao = io.mockk.mockk {
                io.mockk.every { getTransactionsWithSplitsAllCurrencies(any(), any()) } returns kotlinx.coroutines.flow.flowOf(listOf(row(1, "Food"), row(2, "Coffee"), row(3, "Travel")))
            },
            userPreferencesRepository = userPreferencesRepository,
            currencyConversionService = currencyConversionService,
            loanRepository = io.mockk.mockk(relaxed = true),
            categoryDao = io.mockk.mockk {
                io.mockk.coEvery { getAllCategoriesList() } returns listOf(
                    com.pennywiseai.tracker.data.database.entity.CategoryEntity(id = 1, name = "Food", color = "#000"),
                    com.pennywiseai.tracker.data.database.entity.CategoryEntity(id = 2, name = "Coffee", color = "#000", parentId = 1)
                )
            }
        )
        val result = parentAndChild.compareSpending(com.pennywiseai.tracker.data.model.SpendingComparison.Period.MONTH, "food", creditAsExpense = false)
        assertEquals(BigDecimal("20"), (result as com.pennywiseai.tracker.data.model.SpendingComparison.Result.Rows).rows.single().current)
    }
}
