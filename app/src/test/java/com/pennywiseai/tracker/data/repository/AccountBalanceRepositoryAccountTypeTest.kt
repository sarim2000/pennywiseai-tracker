package com.pennywiseai.tracker.data.repository

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.*
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.domain.model.getAccountType
import com.pennywiseai.tracker.presentation.accounts.AccountType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.time.LocalDateTime

class AccountBalanceRepositoryAccountTypeTest {

    private lateinit var accountBalanceDao: AccountBalanceDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var repository: AccountBalanceRepository

    private val balanceRows = mutableListOf<AccountBalanceEntity>()

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
        override fun recurringTransactionDao(): RecurringTransactionDao = error("unused")
        override fun clearAllTables() = Unit
        @Suppress("DEPRECATION")
        override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper = error("unused")
        override fun createInvalidationTracker(): InvalidationTracker = error("unused")
    }

    @Before
    fun setUp() {
        balanceRows.clear()

        accountBalanceDao = Proxy.newProxyInstance(
            AccountBalanceDao::class.java.classLoader,
            arrayOf(AccountBalanceDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insertBalance" -> {
                    val entity = args[0] as AccountBalanceEntity
                    balanceRows.add(entity)
                    balanceRows.size.toLong()
                }
                "getLatestBalance" -> {
                    val bankName = args[0] as String
                    val accountLast4 = args[1] as String
                    balanceRows
                        .filter { it.bankName == bankName && it.accountLast4 == accountLast4 }
                        .maxByOrNull { it.timestamp }
                }
                else -> null
            }
        } as AccountBalanceDao

        transactionDao = Proxy.newProxyInstance(
            TransactionDao::class.java.classLoader,
            arrayOf(TransactionDao::class.java)
        ) { _, _, _ -> null } as TransactionDao

        repository = AccountBalanceRepository(
            accountBalanceDao = accountBalanceDao,
            transactionDao = transactionDao,
            database = TestDb()
        )
    }

    @Test
    fun `insertBalanceFromTransaction preserves existing non-default accountType CURRENT`() = runBlocking {
        val initial = AccountBalanceEntity(
            bankName = "HDFC",
            accountLast4 = "1234",
            balance = BigDecimal("1000.00"),
            timestamp = LocalDateTime.now().minusHours(2),
            accountType = "CURRENT",
            isCreditCard = false
        )
        repository.insertBalance(initial)

        repository.insertBalanceFromTransaction(
            bankName = "HDFC",
            accountLast4 = "1234",
            balance = BigDecimal("950.00"),
            timestamp = LocalDateTime.now().minusHours(1),
            transactionId = 101L,
            isCreditCard = false
        )

        val latest = repository.getLatestBalance("HDFC", "1234")
        assertEquals("CURRENT", latest?.accountType)
        assertEquals(AccountType.CURRENT, latest?.getAccountType())
    }

    @Test
    fun `insertBalanceUpdate preserves existing accountType across balance notifications`() = runBlocking {
        val initial = AccountBalanceEntity(
            bankName = "ICICI",
            accountLast4 = "5678",
            balance = BigDecimal("5000.00"),
            timestamp = LocalDateTime.now().minusHours(3),
            accountType = "CURRENT",
            isCreditCard = false
        )
        repository.insertBalance(initial)

        repository.insertBalanceUpdate(
            bankName = "ICICI",
            accountLast4 = "5678",
            balance = BigDecimal("5500.00"),
            timestamp = LocalDateTime.now().minusHours(1),
            smsSource = "ICICI alert: A/c 5678 bal is 5500",
            sourceType = "SMS_BALANCE"
        )

        val latest = repository.getLatestBalance("ICICI", "5678")
        assertEquals("CURRENT", latest?.accountType)
        assertEquals(AccountType.CURRENT, latest?.getAccountType())
    }

    @Test
    fun `insertBalanceFromTransaction keeps accountType null for genuinely new accounts`() = runBlocking {
        repository.insertBalanceFromTransaction(
            bankName = "SBI",
            accountLast4 = "9999",
            balance = BigDecimal("2000.00"),
            timestamp = LocalDateTime.now(),
            transactionId = 102L,
            isCreditCard = false
        )

        val latest = repository.getLatestBalance("SBI", "9999")
        assertNull(latest?.accountType)
        assertEquals(AccountType.SAVINGS, latest?.getAccountType())
    }

    @Test
    fun `insertBalanceFromTransaction preserves CREDIT accountType and backward compatibility with isCreditCard`() = runBlocking {
        val initial = AccountBalanceEntity(
            bankName = "AXIS",
            accountLast4 = "4321",
            balance = BigDecimal("1500.00"),
            timestamp = LocalDateTime.now().minusHours(2),
            accountType = "CREDIT",
            isCreditCard = true
        )
        repository.insertBalance(initial)

        repository.insertBalanceFromTransaction(
            bankName = "AXIS",
            accountLast4 = "4321",
            balance = BigDecimal("2000.00"),
            timestamp = LocalDateTime.now().minusHours(1),
            transactionId = 103L,
            isCreditCard = true
        )

        val latest = repository.getLatestBalance("AXIS", "4321")
        assertEquals("CREDIT", latest?.accountType)
        assertEquals(AccountType.CREDIT, latest?.getAccountType())
    }
}
