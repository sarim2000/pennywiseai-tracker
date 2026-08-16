package com.pennywiseai.tracker.domain.usecase

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.*
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.time.LocalDateTime

class RestoreTransactionUseCaseTest {

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var accountBalanceRepository: AccountBalanceRepository
    private lateinit var restoreTransactionUseCase: RestoreTransactionUseCase

    private val transactions = mutableMapOf<Long, TransactionEntity>()
    private val restoredRepoTransactions = mutableListOf<TransactionEntity>()
    private val balanceShiftTransactions = mutableListOf<TransactionEntity>()

    private val sampleTxn = TransactionEntity(
        id = 2001L,
        amount = BigDecimal("750.00"),
        merchantName = "Refund Store",
        category = "Shopping",
        transactionType = TransactionType.EXPENSE,
        dateTime = LocalDateTime.now(),
        bankName = "ICICI",
        accountNumber = "5678",
        currency = "INR",
        transactionHash = "hash2",
        isDeleted = true
    )

    private class TestDb : PennyWiseDatabase() {
        override fun transactionDao(): TransactionDao = error("unused")
        override fun subscriptionDao(): SubscriptionDao = error("unused")
        override fun chatDao(): ChatDao = error("unused")
        override fun merchantMappingDao(): MerchantMappingDao = error("unused")
        override fun merchantAliasDao(): MerchantAliasDao = error("unused")
        override fun categoryDao(): CategoryDao = error("unused")
        override fun accountBalanceDao(): AccountBalanceDao = error("unused")
        override fun unrecognizedSmsDao(): UnrecognizedSmsDao = error("unused")
        override fun cardDao(): CardDao = error("unused")
        override fun ruleDao(): RuleDao = error("unused")
        override fun ruleApplicationDao(): RuleApplicationDao = error("unused")
        override fun exchangeRateDao(): ExchangeRateDao = error("unused")
        override fun budgetDao(): BudgetDao = error("unused")
        override fun budgetSnapshotDao(): BudgetSnapshotDao = error("unused")
        override fun transactionSplitDao(): TransactionSplitDao = error("unused")
        override fun bankNotificationDao(): BankNotificationDao = error("unused")
        override fun loanDao(): LoanDao = error("unused")
        override fun transactionGroupDao(): TransactionGroupDao = error("unused")
        override fun profileDao(): ProfileDao = error("unused")
        override fun tagDao(): TagDao = error("unused")
        override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper = error("unused")
        override fun createInvalidationTracker(): InvalidationTracker = error("unused")
        override fun clearAllTables() {}
    }

    @Before
    fun setUp() {
        transactions.clear()
        restoredRepoTransactions.clear()
        balanceShiftTransactions.clear()

        transactions[2001L] = sampleTxn

        val txDao = Proxy.newProxyInstance(
            TransactionDao::class.java.classLoader,
            arrayOf(TransactionDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getTransactionById" -> {
                    val id = args[0] as Long
                    transactions[id]
                }
                "updateTransaction" -> {
                    val entity = args[0] as TransactionEntity
                    transactions[entity.id] = entity
                    null
                }
                else -> null
            }
        } as TransactionDao

        val splitDao = Proxy.newProxyInstance(
            TransactionSplitDao::class.java.classLoader,
            arrayOf(TransactionSplitDao::class.java)
        ) { _, _, _ -> null } as TransactionSplitDao

        val mockContext = object : android.content.ContextWrapper(null) {
            override fun getApplicationContext(): android.content.Context = this
        }

        transactionRepository = object : TransactionRepository(
            txDao,
            splitDao,
            object : com.pennywiseai.tracker.data.preferences.UserPreferencesRepository(mockContext) {}
        ) {
            override suspend fun getTransactionById(id: Long): TransactionEntity? = transactions[id]
            override suspend fun undoDeleteTransaction(transaction: TransactionEntity) {
                val current = transactions[transaction.id]
                if (current != null) {
                    val updated = current.copy(isDeleted = false)
                    transactions[transaction.id] = updated
                    restoredRepoTransactions.add(updated)
                }
            }
        }

        val acctDao = Proxy.newProxyInstance(
            AccountBalanceDao::class.java.classLoader,
            arrayOf(AccountBalanceDao::class.java)
        ) { _, _, _ -> null } as AccountBalanceDao

        accountBalanceRepository = object : AccountBalanceRepository(
            acctDao,
            txDao,
            TestDb()
        ) {
            override suspend fun applyRestoreBalanceShift(transaction: TransactionEntity) {
                balanceShiftTransactions.add(transaction)
            }
        }

        restoreTransactionUseCase = object : RestoreTransactionUseCase(
            database = TestDb(),
            transactionRepository = transactionRepository,
            accountBalanceRepository = accountBalanceRepository
        ) {
            override suspend fun <R> runInTransaction(block: suspend () -> R): R = block()
        }
    }

    @Test
    fun `invoke with single entity calls repository restore and balance shift`() = runBlocking {
        restoreTransactionUseCase(sampleTxn)
        assertEquals(1, restoredRepoTransactions.size)
        assertEquals(1, balanceShiftTransactions.size)
        assertEquals(2001L, restoredRepoTransactions.first().id)
        assertEquals(2001L, balanceShiftTransactions.first().id)
    }

    @Test
    fun `invoke with active entity does not shift balance twice (idempotent)`() = runBlocking {
        // Initial restore
        restoreTransactionUseCase(sampleTxn)
        assertEquals(1, restoredRepoTransactions.size)
        assertEquals(1, balanceShiftTransactions.size)

        // Duplicate restore on active entity
        restoreTransactionUseCase(sampleTxn.copy(isDeleted = false))
        assertEquals(1, restoredRepoTransactions.size)
        assertEquals(1, balanceShiftTransactions.size)
    }

    @Test
    fun `invoke with transactionId restores transaction and applies balance shift`() = runBlocking {
        val result = restoreTransactionUseCase(2001L)
        assertTrue(result)
        assertEquals(1, restoredRepoTransactions.size)
        assertEquals(1, balanceShiftTransactions.size)
    }

    @Test
    fun `invoke with repeated transactionId returns false on second call without double shifting balance`() = runBlocking {
        val firstResult = restoreTransactionUseCase(2001L)
        assertTrue(firstResult)
        assertEquals(1, restoredRepoTransactions.size)
        assertEquals(1, balanceShiftTransactions.size)

        // Second call on now-active transaction
        val secondResult = restoreTransactionUseCase(2001L)
        assertFalse(secondResult)
        assertEquals(1, restoredRepoTransactions.size)
        assertEquals(1, balanceShiftTransactions.size)
    }

    @Test
    fun `invoke with missing transactionId returns false`() = runBlocking {
        val result = restoreTransactionUseCase(9999L)
        assertFalse(result)
        assertEquals(0, restoredRepoTransactions.size)
        assertEquals(0, balanceShiftTransactions.size)
    }

    @Test
    fun `invoke with list of entities restores all items in batch`() = runBlocking {
        val secondTxn = sampleTxn.copy(id = 2002L)
        transactions[2002L] = secondTxn
        restoreTransactionUseCase(listOf(sampleTxn, secondTxn))
        assertEquals(2, restoredRepoTransactions.size)
        assertEquals(2, balanceShiftTransactions.size)
        assertEquals(2001L, restoredRepoTransactions[0].id)
        assertEquals(2002L, restoredRepoTransactions[1].id)
    }
}
