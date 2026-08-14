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

class DeleteTransactionUseCaseTest {

    private lateinit var transactionDao: TransactionDao
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var accountBalanceRepository: AccountBalanceRepository
    private lateinit var deleteTransactionUseCase: DeleteTransactionUseCase

    private val transactions = mutableMapOf<Long, TransactionEntity>()
    private val deletedRepoTransactions = mutableListOf<TransactionEntity>()
    private val balanceShiftTransactions = mutableListOf<TransactionEntity>()

    private val sampleTxn = TransactionEntity(
        id = 1001L,
        amount = BigDecimal("500.00"),
        merchantName = "Test Merchant",
        category = "Food",
        transactionType = TransactionType.EXPENSE,
        dateTime = LocalDateTime.now(),
        bankName = "HDFC",
        accountNumber = "1234",
        currency = "INR",
        transactionHash = "hash1",
        isDeleted = false
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
        deletedRepoTransactions.clear()
        balanceShiftTransactions.clear()

        transactions[1001L] = sampleTxn

        transactionDao = Proxy.newProxyInstance(
            TransactionDao::class.java.classLoader,
            arrayOf(TransactionDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getTransactionById" -> {
                    val id = args[0] as Long
                    transactions[id]
                }
                "softDeleteTransaction" -> {
                    val id = args[0] as Long
                    val current = transactions[id]
                    if (current != null) {
                        val updated = current.copy(isDeleted = true)
                        transactions[id] = updated
                        deletedRepoTransactions.add(updated)
                    }
                    null
                }
                else -> null
            }
        } as TransactionDao

        val splitDao = Proxy.newProxyInstance(
            TransactionSplitDao::class.java.classLoader,
            arrayOf(TransactionSplitDao::class.java)
        ) { _, _, _ -> null } as TransactionSplitDao

        val acctDao = Proxy.newProxyInstance(
            AccountBalanceDao::class.java.classLoader,
            arrayOf(AccountBalanceDao::class.java)
        ) { _, _, _ -> null } as AccountBalanceDao

        accountBalanceRepository = object : AccountBalanceRepository(
            acctDao,
            transactionDao,
            TestDb()
        ) {
            override suspend fun applyDeleteBalanceShift(transaction: TransactionEntity) {
                balanceShiftTransactions.add(transaction)
            }
        }

        // Subclass TransactionRepository with stub for test isolation
        val mockContext = object : android.content.ContextWrapper(null) {
            override fun getApplicationContext(): android.content.Context = this
        }
        transactionRepository = object : TransactionRepository(
            transactionDao,
            splitDao,
            object : com.pennywiseai.tracker.data.preferences.UserPreferencesRepository(mockContext) {}
        ) {
            override suspend fun getTransactionById(id: Long): TransactionEntity? = transactions[id]
            override suspend fun deleteTransaction(transaction: TransactionEntity, hardDelete: Boolean) {
                val current = transactions[transaction.id]
                if (current != null) {
                    val updated = current.copy(isDeleted = true)
                    transactions[transaction.id] = updated
                    deletedRepoTransactions.add(updated)
                }
            }
        }

        deleteTransactionUseCase = object : DeleteTransactionUseCase(
            database = TestDb(),
            transactionRepository = transactionRepository,
            accountBalanceRepository = accountBalanceRepository
        ) {
            override suspend fun <R> runInTransaction(block: suspend () -> R): R = block()
        }
    }

    @Test
    fun `invoke with single active entity deletes and applies balance shift`() = runBlocking {
        deleteTransactionUseCase(sampleTxn)
        assertEquals(1, deletedRepoTransactions.size)
        assertEquals(1, balanceShiftTransactions.size)
        assertEquals(1001L, deletedRepoTransactions.first().id)
        assertEquals(1001L, balanceShiftTransactions.first().id)
    }

    @Test
    fun `invoke with already soft-deleted entity does not shift balance twice (idempotent)`() = runBlocking {
        // Initial deletion
        deleteTransactionUseCase(sampleTxn)
        assertEquals(1, deletedRepoTransactions.size)
        assertEquals(1, balanceShiftTransactions.size)

        // Duplicate deletion attempt
        deleteTransactionUseCase(sampleTxn.copy(isDeleted = true))
        assertEquals(1, deletedRepoTransactions.size)
        assertEquals(1, balanceShiftTransactions.size)
    }

    @Test
    fun `invoke with existing transactionId fetches entity, deletes, and returns true`() = runBlocking {
        val result = deleteTransactionUseCase(1001L)
        assertTrue(result)
        assertEquals(1, deletedRepoTransactions.size)
        assertEquals(1, balanceShiftTransactions.size)
        assertEquals(1001L, deletedRepoTransactions.first().id)
    }

    @Test
    fun `invoke with repeated transactionId returns false on second call without double shifting balance`() = runBlocking {
        val firstResult = deleteTransactionUseCase(1001L)
        assertTrue(firstResult)
        assertEquals(1, deletedRepoTransactions.size)
        assertEquals(1, balanceShiftTransactions.size)

        // Repeated invocation on soft-deleted transaction
        val secondResult = deleteTransactionUseCase(1001L)
        assertFalse(secondResult)
        assertEquals(1, deletedRepoTransactions.size)
        assertEquals(1, balanceShiftTransactions.size)
    }

    @Test
    fun `invoke with missing transactionId returns false without deleting`() = runBlocking {
        val result = deleteTransactionUseCase(9999L)
        assertFalse(result)
        assertEquals(0, deletedRepoTransactions.size)
        assertEquals(0, balanceShiftTransactions.size)
    }

    @Test
    fun `invoke with list of entities deletes and shifts all items in batch`() = runBlocking {
        val secondTxn = sampleTxn.copy(id = 1002L)
        transactions[1002L] = secondTxn
        deleteTransactionUseCase(listOf(sampleTxn, secondTxn))
        assertEquals(2, deletedRepoTransactions.size)
        assertEquals(2, balanceShiftTransactions.size)
        assertEquals(1001L, deletedRepoTransactions[0].id)
        assertEquals(1002L, deletedRepoTransactions[1].id)
    }
}
