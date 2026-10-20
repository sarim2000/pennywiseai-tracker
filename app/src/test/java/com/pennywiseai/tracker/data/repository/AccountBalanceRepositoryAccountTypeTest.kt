package com.pennywiseai.tracker.data.repository

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.*
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.model.getAccountType
import com.pennywiseai.tracker.presentation.accounts.AccountType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        override fun webhookProfileDao(): WebhookProfileDao = error("unused")
        override fun webhookLogDao(): WebhookLogDao = error("unused")
        override fun webhookCursorDao(): WebhookCursorDao = error("unused")
        override fun goalDao(): com.pennywiseai.tracker.data.database.dao.GoalDao = error("unused")
        override fun webhookDeliveredTransactionDao(): WebhookDeliveredTransactionDao = error("unused")
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
                "updateAccountType" -> {
                    val (bankName, accountLast4, type, isCredit) = args
                    var updated = 0
                    balanceRows.replaceAll { row ->
                        if (row.bankName == bankName && row.accountLast4 == accountLast4) {
                            updated++
                            row.copy(accountType = type as String, isCreditCard = isCredit as Boolean)
                        } else row
                    }
                    updated
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

    private fun smsAccountRow(balance: String, hoursAgo: Long) = AccountBalanceEntity(
        bankName = "Test Bank",
        accountLast4 = "7777",
        balance = BigDecimal(balance),
        timestamp = LocalDateTime.now().minusHours(hoursAgo),
        sourceType = "TRANSACTION"
    )

    @Test
    fun `changing an SMS account to LOAN re-tags every row and keeps the balance figures`() = runBlocking {
        repository.insertBalance(smsAccountRow("260000.00", hoursAgo = 5))
        repository.insertBalance(smsAccountRow("250000.00", hoursAgo = 2))

        repository.setAccountType("Test Bank", "7777", AccountType.LOAN)

        assertEquals(2, balanceRows.size)
        assertTrue(balanceRows.all { it.isLoan && it.isLiability && !it.isCreditCard })
        assertEquals(listOf("260000.00", "250000.00"), balanceRows.map { it.balance.toPlainString() })
        assertEquals(AccountType.LOAN, repository.getLatestBalance("Test Bank", "7777")?.getAccountType())
    }

    @Test
    fun `a payment on a loan account lowers what is owed, and switching back restores bank behaviour`() = runBlocking {
        repository.insertBalance(smsAccountRow("250000.00", hoursAgo = 2))
        repository.setAccountType("Test Bank", "7777", AccountType.LOAN)

        repository.applyTransactionToBalance("Test Bank", "7777", BigDecimal("12000.00"), TransactionType.INCOME, 1L)
        assertEquals(BigDecimal("238000.00"), repository.getLatestBalance("Test Bank", "7777")?.balance)

        repository.setAccountType("Test Bank", "7777", AccountType.SAVINGS)
        repository.applyTransactionToBalance("Test Bank", "7777", BigDecimal("1000.00"), TransactionType.INCOME, 2L)
        val latest = repository.getLatestBalance("Test Bank", "7777")!!
        assertFalse(latest.isLiability)
        assertEquals(BigDecimal("239000.00"), latest.balance)
    }

    @Test
    fun `restoreAccountType re-tags rows a force resync rebuilt without a type`() = runBlocking {
        // Snapshot taken before the resync wiped the account's rows.
        val beforeRebuild = smsAccountRow("250000.00", hoursAgo = 1).copy(accountType = "LOAN")

        // Rebuilt from SMS, with nothing left to inherit the type from.
        repository.insertBalanceUpdate(
            bankName = "Test Bank",
            accountLast4 = "7777",
            balance = BigDecimal("260000.00"),
            timestamp = LocalDateTime.now().minusHours(5),
            sourceType = "SMS_BALANCE"
        )
        assertFalse(balanceRows.single().isLoan)

        repository.restoreAccountType(beforeRebuild)
        assertTrue(balanceRows.single().isLoan)
        assertEquals("260000.00", balanceRows.single().balance.toPlainString())
    }

    @Test
    fun `switching a loan to CREDIT sets the credit-card flag, and back to LOAN clears it`() = runBlocking {
        repository.insertBalance(smsAccountRow("5000.00", hoursAgo = 1))

        repository.setAccountType("Test Bank", "7777", AccountType.CREDIT)
        assertTrue(repository.getLatestBalance("Test Bank", "7777")!!.isCreditCard)

        repository.setAccountType("Test Bank", "7777", AccountType.LOAN)
        val latest = repository.getLatestBalance("Test Bank", "7777")!!
        assertFalse(latest.isCreditCard)
        assertTrue(latest.isLoan)
    }
}
