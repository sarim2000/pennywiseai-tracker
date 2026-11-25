package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.domain.model.AccountType
import com.pennywiseai.tracker.domain.model.getAccountType
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
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
    private val cardTransactions = mutableListOf<TransactionEntity>()

    @Before
    fun setUp() {
        balanceRows.clear()
        cardTransactions.clear()

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
                "fillMissingAccountType" -> {
                    val (bankName, accountLast4, type, isCredit) = args
                    var updated = 0
                    balanceRows.replaceAll { row ->
                        if (row.bankName == bankName && row.accountLast4 == accountLast4 && row.accountType == null) {
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
        ) { _, method, args ->
            when (method.name) {
                "getTransactionsForAccountStrict" ->
                    cardTransactions.filter { it.bankName == args[0] && it.accountNumber == args[1] }
                else -> null
            }
        } as TransactionDao

        mockkStatic("com.pennywiseai.tracker.data.database.RoomCompatKt")
        val database = mockk<PennyWiseDatabase>()
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }

        repository = AccountBalanceRepository(
            accountBalanceDao = accountBalanceDao,
            transactionDao = transactionDao,
            database = database,
            userPreferencesRepository = io.mockk.mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() = unmockkStatic("com.pennywiseai.tracker.data.database.RoomCompatKt")

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
    fun `a balance notice on a credit card keeps it a card with its limit`() = runBlocking {
        repository.insertBalance(
            smsAccountRow("3000.00", hoursAgo = 2).copy(accountType = "CREDIT", isCreditCard = true, creditLimit = BigDecimal("50000"))
        )
        repository.insertBalanceUpdate(
            bankName = "Test Bank",
            accountLast4 = "7777",
            balance = BigDecimal("3500.00"),
            timestamp = LocalDateTime.now().minusHours(1)
        )

        val latest = repository.getLatestBalance("Test Bank", "7777")!!
        assertTrue(latest.isCreditCard)
        assertEquals(BigDecimal("50000"), latest.creditLimit)
    }

    @Test
    fun `linking a card to a non-INR account keeps its currency and bill`() = runBlocking {
        repository.insertBalance(
            smsAccountRow("3000.00", hoursAgo = 2).copy(
                accountType = "CREDIT", isCreditCard = true, currency = "USD",
                cardDueDay = 10, cardBillAmount = BigDecimal("400"), cardBillAsOf = LocalDateTime.now().minusDays(3),
            )
        )
        // The card-link path names no currency.
        repository.insertBalanceUpdate(
            bankName = "Test Bank",
            accountLast4 = "7777",
            balance = BigDecimal("3500.00"),
            timestamp = LocalDateTime.now().minusHours(1),
            sourceType = "CARD_LINK"
        )
        repository.insertBalanceFromTransaction(
            bankName = "Test Bank",
            accountLast4 = "7777",
            balance = BigDecimal("3600.00"),
            timestamp = LocalDateTime.now(),
            transactionId = 7L,
            isCreditCard = true
        )

        val latest = repository.getLatestBalance("Test Bank", "7777")!!
        assertEquals(BigDecimal("3600.00"), latest.balance)
        assertEquals("USD", latest.currency)
        assertEquals(BigDecimal("400"), latest.cardBillAmount)
    }

    @Test
    fun `restoreAccountType keeps a type the user set while the resync ran`() = runBlocking {
        val beforeRebuild = smsAccountRow("250000.00", hoursAgo = 1).copy(accountType = "LOAN")
        repository.insertBalance(smsAccountRow("260000.00", hoursAgo = 5))

        // The user switches the account back to Savings mid-scan.
        repository.setAccountType("Test Bank", "7777", AccountType.SAVINGS)
        repository.restoreAccountType(beforeRebuild)

        assertEquals(AccountType.SAVINGS, repository.getLatestBalance("Test Bank", "7777")?.getAccountType())
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

    @Test
    fun `a card marked CREDIT with no account gets a manual credit card account`() = runBlocking {
        assertTrue(repository.ensureCreditCardAccount("Test Bank", "4321"))

        val account = repository.getLatestBalance("Test Bank", "4321")!!
        assertTrue(account.isCreditCard)
        assertEquals(AccountType.CREDIT, account.getAccountType())
        assertEquals(0, BigDecimal.ZERO.compareTo(account.balance))
        assertEquals(AccountBalanceRepository.SOURCE_MANUAL, account.sourceType)
        assertEquals(1, balanceRows.size)
    }

    @Test
    fun `a card marked CREDIT keeps its existing account and only switches the type`() = runBlocking {
        repository.insertBalance(smsAccountRow("5000.00", hoursAgo = 1))

        assertTrue(repository.ensureCreditCardAccount("Test Bank", "7777"))

        val account = repository.getLatestBalance("Test Bank", "7777")!!
        assertTrue(account.isCreditCard)
        assertEquals(AccountType.CREDIT, account.getAccountType())
        assertEquals(0, BigDecimal("5000.00").compareTo(account.balance))
        assertEquals(1, balanceRows.size)
    }

    private fun cardTxn(
        id: Long,
        daysAgo: Long,
        type: TransactionType,
        amount: String,
        statedBalance: String? = null
    ) = TransactionEntity(
        id = id,
        amount = BigDecimal(amount),
        merchantName = "Test Merchant",
        category = "Others",
        transactionType = type,
        dateTime = LocalDateTime.now().minusDays(daysAgo),
        bankName = "Test Bank",
        accountNumber = "4321",
        balanceAfter = statedBalance?.let(::BigDecimal),
        transactionHash = "hash-$id",
        currency = "INR"
    )

    @Test
    fun `a card marked CREDIT with earlier SMS opens with the balance they give`() = runBlocking {
        // Saved out of order: the replay must follow time, not insertion.
        cardTransactions += cardTxn(2, daysAgo = 2, TransactionType.EXPENSE, "500.00")
        cardTransactions += cardTxn(1, daysAgo = 3, TransactionType.EXPENSE, "1000.00")
        cardTransactions += cardTxn(3, daysAgo = 1, TransactionType.INCOME, "300.00")

        assertTrue(repository.ensureCreditCardAccount("Test Bank", "4321"))

        val account = repository.getLatestBalance("Test Bank", "4321")!!
        assertTrue(account.isCreditCard)
        assertEquals(AccountType.CREDIT, account.getAccountType())
        assertEquals(0, BigDecimal("1200.00").compareTo(account.balance))
        assertEquals(3L, account.transactionId)
        assertEquals(listOf(1L, 2L, 3L), balanceRows.map { it.transactionId })
        assertTrue(balanceRows.none { it.sourceType == AccountBalanceRepository.SOURCE_MANUAL })
    }

    @Test
    fun `a balance stated in an earlier SMS wins over the running total`() = runBlocking {
        cardTransactions += cardTxn(1, daysAgo = 3, TransactionType.EXPENSE, "1000.00")
        cardTransactions += cardTxn(2, daysAgo = 2, TransactionType.EXPENSE, "500.00", statedBalance = "4000.00")
        cardTransactions += cardTxn(3, daysAgo = 1, TransactionType.EXPENSE, "250.00")

        repository.ensureCreditCardAccount("Test Bank", "4321")

        val account = repository.getLatestBalance("Test Bank", "4321")!!
        assertEquals(0, BigDecimal("4250.00").compareTo(account.balance))
    }

    @Test
    fun `an existing credit card account is left alone`() = runBlocking {
        repository.ensureCreditCardAccount("Test Bank", "4321")

        assertFalse(repository.ensureCreditCardAccount("Test Bank", "4321"))
        assertEquals(1, balanceRows.size)
    }
}
