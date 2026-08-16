package com.pennywiseai.tracker.receiver

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.*
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.usecase.DeleteTransactionUseCase
import com.pennywiseai.tracker.utils.BalanceCalculator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.time.LocalDateTime

class NotificationActionReceiverTest {

    private lateinit var transactionDao: TransactionDao
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var accountBalanceDao: AccountBalanceDao
    private lateinit var accountBalanceRepository: AccountBalanceRepository
    private lateinit var deleteTransactionUseCase: DeleteTransactionUseCase

    private val transactions = mutableMapOf<Long, TransactionEntity>()
    private val balanceRecords = mutableListOf<AccountBalanceEntity>()

    private val testTxn = TransactionEntity(
        id = 101L,
        amount = BigDecimal("450.00"),
        merchantName = "Starbucks",
        category = "Food",
        transactionType = TransactionType.EXPENSE,
        dateTime = LocalDateTime.now(),
        bankName = "HDFC Bank",
        accountNumber = "1234",
        currency = "INR",
        transactionHash = "hash101",
        isDeleted = false
    )

    private val testCcTxn = TransactionEntity(
        id = 102L,
        amount = BigDecimal("2000.00"),
        merchantName = "Amazon",
        category = "Shopping",
        transactionType = TransactionType.EXPENSE,
        dateTime = LocalDateTime.now(),
        bankName = "ICICI Bank",
        accountNumber = "9999",
        currency = "INR",
        transactionHash = "hash102",
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
        balanceRecords.clear()

        transactions[101L] = testTxn
        transactions[102L] = testCcTxn

        // Debit account (starting balance 1000.00)
        balanceRecords.add(
            AccountBalanceEntity(
                id = 1L,
                bankName = "HDFC Bank",
                accountLast4 = "1234",
                balance = BigDecimal("1000.00"),
                timestamp = LocalDateTime.now().minusHours(1),
                isCreditCard = false,
                sourceType = "TRANSACTION"
            )
        )

        // Credit card account (starting outstanding debt 5000.00)
        balanceRecords.add(
            AccountBalanceEntity(
                id = 2L,
                bankName = "ICICI Bank",
                accountLast4 = "9999",
                balance = BigDecimal("5000.00"),
                timestamp = LocalDateTime.now().minusHours(1),
                isCreditCard = true,
                sourceType = "TRANSACTION"
            )
        )

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
                        transactions[id] = current.copy(isDeleted = true)
                    }
                    null
                }
                "updateTransaction" -> {
                    val entity = args[0] as TransactionEntity
                    transactions[entity.id] = entity
                    null
                }
                "getTransactionsForAccountStrict" -> emptyList<TransactionEntity>()
                "getTransfersForAccount" -> emptyList<TransactionEntity>()
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
            transactionDao,
            splitDao,
            object : com.pennywiseai.tracker.data.preferences.UserPreferencesRepository(mockContext) {}
        ) {
            override suspend fun getTransactionById(id: Long): TransactionEntity? = transactions[id]
            override suspend fun deleteTransaction(transaction: TransactionEntity, hardDelete: Boolean) {
                val current = transactions[transaction.id]
                if (current != null) {
                    transactions[transaction.id] = current.copy(isDeleted = true)
                }
            }
        }

        accountBalanceDao = Proxy.newProxyInstance(
            AccountBalanceDao::class.java.classLoader,
            arrayOf(AccountBalanceDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getLatestBalance" -> {
                    val bank = args[0] as String
                    val last4 = args[1] as String
                    balanceRecords.lastOrNull { it.bankName == bank && it.accountLast4 == last4 }
                }
                "insertBalance" -> {
                    val entity = args[0] as AccountBalanceEntity
                    val newId = (balanceRecords.size + 1).toLong()
                    val saved = entity.copy(id = newId)
                    balanceRecords.add(saved)
                    newId
                }
                "getOpeningRow" -> null
                "countSmsSourcedBalances" -> 1
                else -> null
            }
        } as AccountBalanceDao

        accountBalanceRepository = object : AccountBalanceRepository(
            accountBalanceDao,
            transactionDao,
            TestDb()
        ) {
            override suspend fun applyDeleteBalanceShift(transaction: TransactionEntity) {
                val bank = transaction.bankName ?: return
                val acct = transaction.accountNumber ?: return
                val latest = accountBalanceDao.getLatestBalance(bank, acct)
                if (latest != null) {
                    val effect = BalanceCalculator.signedBalanceEffect(
                        isCreditCard = latest.isCreditCard,
                        transactionType = transaction.transactionType,
                        amount = transaction.amount
                    ).negate()
                    accountBalanceDao.insertBalance(
                        latest.copy(
                            id = 0,
                            balance = latest.balance + effect,
                            timestamp = LocalDateTime.now(),
                            transactionId = null,
                            sourceType = "TRANSACTION"
                        )
                    )
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
    fun `deleteTransactionUseCase soft deletes transaction and restores debit balance`() = runBlocking {
        val originalTxn = transactionDao.getTransactionById(101L)
        assertNotNull(originalTxn)
        assertFalse(originalTxn!!.isDeleted)

        val deleted = deleteTransactionUseCase(101L)
        assertTrue(deleted)

        val postDeleteTxn = transactionDao.getTransactionById(101L)
        assertNotNull(postDeleteTxn)
        assertTrue(postDeleteTxn!!.isDeleted)

        // Expense of 450.00 reverted: 1000.00 -> 1450.00
        val latestBalance = accountBalanceDao.getLatestBalance("HDFC Bank", "1234")
        assertNotNull(latestBalance)
        assertEquals(BigDecimal("1450.00"), latestBalance!!.balance)
    }

    @Test
    fun `repeated notification delete delivery does not shift balance twice (idempotent)`() = runBlocking {
        // First delivery
        val firstDeleted = deleteTransactionUseCase(101L)
        assertTrue(firstDeleted)

        val balanceAfterFirst = accountBalanceDao.getLatestBalance("HDFC Bank", "1234")?.balance
        assertEquals(BigDecimal("1450.00"), balanceAfterFirst)

        // Second delivery of same notification intent
        val secondDeleted = deleteTransactionUseCase(101L)
        assertFalse(secondDeleted)

        val balanceAfterSecond = accountBalanceDao.getLatestBalance("HDFC Bank", "1234")?.balance
        assertEquals(BigDecimal("1450.00"), balanceAfterSecond)
    }

    @Test
    fun `deleteTransactionUseCase soft deletes credit card expense and decreases debt`() = runBlocking {
        val originalTxn = transactionDao.getTransactionById(102L)
        assertNotNull(originalTxn)
        assertFalse(originalTxn!!.isDeleted)

        val deleted = deleteTransactionUseCase(102L)
        assertTrue(deleted)

        val postDeleteTxn = transactionDao.getTransactionById(102L)
        assertNotNull(postDeleteTxn)
        assertTrue(postDeleteTxn!!.isDeleted)

        // CC Expense of 2000.00 reverted: 5000.00 debt -> 3000.00 debt
        val latestBalance = accountBalanceDao.getLatestBalance("ICICI Bank", "9999")
        assertNotNull(latestBalance)
        assertEquals(BigDecimal("3000.00"), latestBalance!!.balance)
    }

    @Test
    fun `deleteTransactionUseCase with missing id returns false`() = runBlocking {
        val deleted = deleteTransactionUseCase(9999L)
        assertFalse(deleted)
    }
}
