package com.pennywiseai.tracker.data.repository

import android.content.Context
import android.content.ContextWrapper
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.currency.ExchangeRateProvider
import com.pennywiseai.tracker.data.database.dao.ExchangeRateDao
import com.pennywiseai.tracker.data.database.dao.SubscriptionDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.ExchangeRateEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
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
    private lateinit var subscriptionDao: SubscriptionDao
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var currencyConversionService: CurrencyConversionService
    private lateinit var repository: AiContextRepository

    private var transactionsList = mutableListOf<TransactionEntity>()
    private var subscriptionsList = mutableListOf<SubscriptionEntity>()

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
        subscriptionsList.clear()

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

        subscriptionDao = Proxy.newProxyInstance(
            SubscriptionDao::class.java.classLoader,
            arrayOf(SubscriptionDao::class.java)
        ) { _, method, _ ->
            if (method.name == "getSubscriptionsByStateList") {
                subscriptionsList
            } else {
                null
            }
        } as SubscriptionDao

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

        userPreferencesRepository = UserPreferencesRepository(FakeContext())
        userPreferencesRepository.updateBaseCurrency("INR")

        currencyConversionService = CurrencyConversionService(
            exchangeRateDao = fakeExchangeRateDao,
            exchangeRateProvider = fakeExchangeRateProvider,
            userPreferencesRepository = userPreferencesRepository
        )

        repository = AiContextRepository(
            transactionDao = transactionDao,
            subscriptionDao = subscriptionDao,
            userPreferencesRepository = userPreferencesRepository,
            currencyConversionService = currencyConversionService
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

        val summary = repository.getChatContext().monthSummary

        // totalIncome = 100 USD * 80 = 8000 INR
        assertEquals(BigDecimal("8000.00"), summary.totalIncome)
        // totalExpense = (50 USD * 80) + 1000 INR = 4000 + 1000 = 5000 INR
        assertEquals(BigDecimal("5000.00"), summary.totalExpense)
        assertEquals(3, summary.transactionCount)
    }

    @Test
    fun `getRecentTransactions maps and converts amounts correctly`() = runBlocking {
        val now = LocalDateTime.now()
        transactionsList.addAll(listOf(
            createTransaction(amount = BigDecimal("10.00"), currency = "USD", merchant = "Starbucks", date = now),
            createTransaction(amount = BigDecimal("150.00"), currency = "INR", merchant = "Local Store", date = now.minusHours(1))
        ))

        val recent = repository.getChatContext().recentTransactions

        assertEquals(2, recent.size)
        // Starbucks is the most recent (now)
        assertEquals("Starbucks", recent[0].merchantName)
        assertEquals(BigDecimal("800.00"), recent[0].amount)
        // Local Store is next (now - 1h)
        assertEquals("Local Store", recent[1].merchantName)
        assertEquals(BigDecimal("150.00"), recent[1].amount)
    }

    @Test
    fun `getActiveSubscriptions maps and converts subscription amounts correctly`() = runBlocking {
        subscriptionsList.addAll(listOf(
            createSubscription(amount = BigDecimal("9.99"), currency = "USD", merchant = "Netflix"),
            createSubscription(amount = BigDecimal("299.00"), currency = "INR", merchant = "Spotify")
        ))

        val activeSubs = repository.getChatContext().activeSubscriptions

        assertEquals(2, activeSubs.size)
        // Netflix: 9.99 USD * 80 = 799.20 INR
        val netflixSub = activeSubs.find { it.merchantName == "Netflix" }
        assertNotNull(netflixSub)
        assertEquals(BigDecimal("799.20"), netflixSub.amount)

        // Spotify: 299 INR -> 299.00 INR
        val spotifySub = activeSubs.find { it.merchantName == "Spotify" }
        assertNotNull(spotifySub)
        assertEquals(BigDecimal("299.00"), spotifySub.amount)
    }

    @Test
    fun `getTopCategories aggregates and calculates percentages using base currency`() = runBlocking {
        transactionsList.addAll(listOf(
            createTransaction(amount = BigDecimal("10.00"), currency = "USD", category = "Food", type = TransactionType.EXPENSE),
            createTransaction(amount = BigDecimal("20.00"), currency = "USD", category = "Food", type = TransactionType.EXPENSE),
            createTransaction(amount = BigDecimal("1000.00"), currency = "INR", category = "Transport", type = TransactionType.EXPENSE)
        ))

        val topCategories = repository.getChatContext().topCategories

        // Total Food = 10 USD + 20 USD = 30 USD -> 2400 INR
        // Total Transport = 1000 INR
        // Total Spending = 3400 INR
        // Food % = 2400 / 3400 = 70.588... % -> 70.59%
        // Transport % = 1000 / 3400 = 29.41%

        assertEquals(2, topCategories.size)
        val foodCat = topCategories.find { it.category == "Food" }
        assertNotNull(foodCat)
        assertEquals(BigDecimal("2400.00"), foodCat.amount)
        assertEquals(70.59f, foodCat.percentage, 0.01f)

        val transportCat = topCategories.find { it.category == "Transport" }
        assertNotNull(transportCat)
        assertEquals(BigDecimal("1000.00"), transportCat.amount)
        assertEquals(29.41f, transportCat.percentage, 0.01f)
    }

    @Test
    fun `getQuickStats daily spending and largest expense convert currency correctly`() = runBlocking {
        transactionsList.addAll(listOf(
            // 20 USD = 1600 INR
            createTransaction(amount = BigDecimal("20.00"), currency = "USD", merchant = "Big Expense USD", type = TransactionType.EXPENSE),
            // 1500 INR
            createTransaction(amount = BigDecimal("1500.00"), currency = "INR", merchant = "Big Expense INR", type = TransactionType.EXPENSE)
        ))

        val chatContext = repository.getChatContext()
        val stats = chatContext.quickStats

        // The largest expense should be "Big Expense USD" (1600 INR) instead of "Big Expense INR" (1500 INR)
        assertNotNull(stats.largestExpenseThisMonth)
        assertEquals("Big Expense USD", stats.largestExpenseThisMonth.merchantName)
        assertEquals(BigDecimal("1600.00"), stats.largestExpenseThisMonth.amount)
    }

    private fun createTransaction(
        amount: BigDecimal,
        currency: String,
        merchant: String = "Merchant",
        category: String = "Food",
        type: TransactionType = TransactionType.EXPENSE,
        date: LocalDateTime = LocalDateTime.now()
    ): TransactionEntity {
        return TransactionEntity(
            id = (1..100000).random().toLong(),
            amount = amount,
            merchantName = merchant,
            category = category,
            transactionType = type,
            dateTime = date,
            currency = currency,
            transactionHash = "hash_" + (1..100000).random()
        )
    }

    private fun createSubscription(
        amount: BigDecimal,
        currency: String,
        merchant: String = "Merchant",
        state: SubscriptionState = SubscriptionState.ACTIVE
    ): SubscriptionEntity {
        return SubscriptionEntity(
            id = (1..100000).random().toLong(),
            merchantName = merchant,
            amount = amount,
            currency = currency,
            state = state,
            nextPaymentDate = LocalDate.now().plusDays(5)
        )
    }
}
