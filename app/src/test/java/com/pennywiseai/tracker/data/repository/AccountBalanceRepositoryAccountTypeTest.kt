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
                "getBalanceHistoryForAccount" ->
                    balanceRows
                        .filter { it.bankName == args[0] && it.accountLast4 == args[1] }
                        .sortedByDescending { it.timestamp }
                "deleteAccount" -> {
                    val before = balanceRows.size
                    balanceRows.removeAll { it.bankName == args[0] && it.accountLast4 == args[1] }
                    before - balanceRows.size
                }
                "deleteTransactionDerivedBalances" -> {
                    val before = balanceRows.size
                    balanceRows.removeAll { it.transactionId != null && it.smsSource == null }
                    before - balanceRows.size
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

    private val cardCreatedAt: LocalDateTime = LocalDateTime.now().minusDays(10)

    private suspend fun markCredit(bankName: String = "Test Bank", last4: String = "4321") =
        repository.ensureCreditCardAccount(bankName, last4, cardCreatedAt)

    private fun cardTxn(
        id: Long,
        daysAgo: Long,
        type: TransactionType,
        amount: String,
        statedBalance: String? = null,
        currency: String = "INR",
        bankName: String = "Test Bank"
    ) = TransactionEntity(
        id = id,
        amount = BigDecimal(amount),
        merchantName = "Test Merchant",
        category = "Others",
        transactionType = type,
        dateTime = LocalDateTime.now().minusDays(daysAgo),
        smsBody = "Test SMS $id",
        bankName = bankName,
        accountNumber = "4321",
        balanceAfter = statedBalance?.let(::BigDecimal),
        transactionHash = "hash-$id",
        currency = currency
    )

    /** The balance row the live receiver writes for an unlinked debit card's spend. */
    private fun liveDebitRow(txn: TransactionEntity, balance: String) = AccountBalanceEntity(
        bankName = "Test Bank",
        accountLast4 = "4321",
        balance = BigDecimal(balance),
        timestamp = txn.dateTime,
        transactionId = txn.id,
        smsSource = txn.smsBody,
        sourceType = "TRANSACTION",
        currency = txn.currency
    )

    private fun assertOwed(expected: String) {
        val account = balanceRows.filter { it.bankName == "Test Bank" && it.accountLast4 == "4321" }
            .maxByOrNull { it.timestamp }!!
        assertTrue(account.isCreditCard)
        assertEquals(AccountType.CREDIT, account.getAccountType())
        assertEquals("owed", 0, BigDecimal(expected).compareTo(account.balance))
    }

    @Test
    fun `a card with no transactions gets a manual zero row stamped when the card was first seen`() = runBlocking {
        markCredit()

        val account = repository.getLatestBalance("Test Bank", "4321")!!
        assertOwed("0")
        assertEquals(AccountBalanceRepository.SOURCE_MANUAL, account.sourceType)
        assertEquals(cardCreatedAt, account.timestamp)
        assertEquals(1, balanceRows.size)
    }

    @Test
    fun `an SMS imported after the placeholder sorts after it and sets the balance`() = runBlocking {
        markCredit()

        // An older first SMS (still newer than when the card was first seen) found by a later scan.
        repository.insertBalanceFromTransaction(
            bankName = "Test Bank",
            accountLast4 = "4321",
            balance = BigDecimal("800.00"),
            timestamp = LocalDateTime.now().minusDays(5),
            transactionId = 41L,
            isCreditCard = true
        )

        assertEquals(0, BigDecimal("800.00").compareTo(repository.getLatestBalance("Test Bank", "4321")!!.balance))
    }

    @Test
    fun `spends replay in time order and a repayment lowers what is owed`() = runBlocking {
        // Saved out of order: the replay must follow time, not insertion.
        cardTransactions += cardTxn(2, daysAgo = 2, TransactionType.EXPENSE, "500.00")
        cardTransactions += cardTxn(1, daysAgo = 3, TransactionType.EXPENSE, "1000.00")
        cardTransactions += cardTxn(3, daysAgo = 1, TransactionType.INCOME, "300.00")

        markCredit()

        assertOwed("1200.00")
        // A MANUAL zero anchor first, then one row per transaction in time order.
        val ordered = balanceRows.sortedBy { it.timestamp }
        assertEquals(listOf(null, 1L, 2L, 3L), ordered.map { it.transactionId })
        assertEquals(AccountBalanceRepository.SOURCE_MANUAL, ordered.first().sourceType)
        assertEquals(0, BigDecimal.ZERO.compareTo(ordered.first().balance))
        // A second before the card's createdAt, which here is earlier than its first transaction.
        assertEquals(cardCreatedAt.minusSeconds(1), ordered.first().timestamp)
    }

    @Test
    fun `the anchor goes a second before a first transaction older than the card`() = runBlocking {
        // A hand-recorded transaction dated before the app first saw the card.
        val first = cardTxn(1, daysAgo = 20, TransactionType.EXPENSE, "100.00")
        cardTransactions += first

        markCredit()

        val anchor = balanceRows.minBy { it.timestamp }
        assertEquals(AccountBalanceRepository.SOURCE_MANUAL, anchor.sourceType)
        assertEquals(first.dateTime.minusSeconds(1), anchor.timestamp)
    }

    @Test
    fun `fix 1 - an account the live receiver wrote as a bank account is replayed, not relabelled`() = runBlocking {
        val spend = cardTxn(1, daysAgo = 1, TransactionType.EXPENSE, "1000.00", currency = "USD")
        cardTransactions += spend
        // Bank math: the spend took the account to -1,000.
        balanceRows += liveDebitRow(spend, "-1000.00")

        markCredit()

        assertOwed("1000.00")
        assertEquals(2, balanceRows.size)
        assertTrue(balanceRows.all { it.currency == "USD" })
    }

    @Test
    fun `a rebuilt account keeps the alias, profile, alert and statement day set on it`() = runBlocking {
        val spend = cardTxn(1, daysAgo = 1, TransactionType.EXPENSE, "1000.00")
        cardTransactions += spend
        balanceRows += liveDebitRow(spend, "-1000.00").copy(
            alias = "Travel card",
            profileId = 7L,
            lowBalanceThreshold = BigDecimal("50.00"),
            statementDay = 12
        )

        markCredit()

        val row = balanceRows.maxBy { it.timestamp }
        assertOwed("1000.00")
        assertEquals("Travel card", row.alias)
        assertEquals(7L, row.profileId)
        assertEquals(0, BigDecimal("50.00").compareTo(row.lowBalanceThreshold))
        assertEquals(12, row.statementDay)
    }

    @Test
    fun `an account with rows from elsewhere only switches its type`() = runBlocking {
        val spend = cardTxn(1, daysAgo = 1, TransactionType.EXPENSE, "1000.00")
        cardTransactions += spend
        balanceRows += liveDebitRow(spend, "4000.00")
        balanceRows += AccountBalanceEntity(
            bankName = "Test Bank",
            accountLast4 = "4321",
            balance = BigDecimal("5000.00"),
            timestamp = LocalDateTime.now().minusDays(4),
            sourceType = AccountBalanceRepository.SOURCE_MANUAL
        )

        markCredit()

        assertOwed("4000.00")
        assertEquals(2, balanceRows.size)
        assertTrue(balanceRows.all { it.isCreditCard })
    }

    @Test
    fun `fix 2 - clearing local history keeps the replayed account`() = runBlocking {
        cardTransactions += cardTxn(1, daysAgo = 2, TransactionType.EXPENSE, "1000.00")
        cardTransactions += cardTxn(2, daysAgo = 1, TransactionType.EXPENSE, "500.00")
        markCredit()

        repository.deleteTransactionDerivedBalances()

        assertOwed("1500.00")
        assertEquals(
            listOf("Test SMS 1", "Test SMS 2"),
            balanceRows.filter { it.transactionId != null }.sortedBy { it.timestamp }.map { it.smsSource }
        )
    }

    @Test
    fun `fix 3 - the account takes the currency its SMS are in, not a fallback`() = runBlocking {
        // A bank no parser knows: getBankBaseCurrency would fall back to INR.
        cardTransactions += cardTxn(1, daysAgo = 1, TransactionType.EXPENSE, "40.00", currency = "AED", bankName = "Unknown Test Bank")

        repository.ensureCreditCardAccount("Unknown Test Bank", "4321", cardCreatedAt)

        val account = repository.getLatestBalance("Unknown Test Bank", "4321")!!
        assertEquals("AED", account.currency)
        assertEquals(0, BigDecimal("40.00").compareTo(account.balance))
    }

    @Test
    fun `a new account takes its bank parser's currency, not the one its spends share`() = runBlocking {
        // An INR bank whose card was only used abroad.
        cardTransactions += cardTxn(1, daysAgo = 2, TransactionType.EXPENSE, "40.00", currency = "USD", bankName = "ICICI Bank")
        cardTransactions += cardTxn(2, daysAgo = 1, TransactionType.EXPENSE, "25.00", currency = "USD", bankName = "ICICI Bank")

        repository.ensureCreditCardAccount("ICICI Bank", "4321", cardCreatedAt)

        assertEquals("INR", repository.getLatestBalance("ICICI Bank", "4321")!!.currency)
    }

    @Test
    fun `a rebuilt account keeps its currency when its transactions are in several`() = runBlocking {
        val usd = cardTxn(1, daysAgo = 2, TransactionType.EXPENSE, "100.00", currency = "USD")
        val eur = cardTxn(2, daysAgo = 1, TransactionType.EXPENSE, "50.00", currency = "EUR")
        cardTransactions += usd
        cardTransactions += eur
        // The live receiver's USD account: the EUR spend left it as it was.
        balanceRows += liveDebitRow(usd, "-100.00")
        balanceRows += liveDebitRow(eur, "-100.00").copy(currency = "USD")

        markCredit()

        assertOwed("100.00")
        assertTrue(balanceRows.all { it.currency == "USD" })
    }

    @Test
    fun `an account whose figure the user corrected is relabelled, not rebuilt`() = runBlocking {
        val spend = cardTxn(1, daysAgo = 1, TransactionType.EXPENSE, "1000.00")
        cardTransactions += spend
        // Balance History edit: same source and transaction, the user's figure.
        balanceRows += liveDebitRow(spend, "-1000.00").copy(balance = BigDecimal("2500.00"))

        markCredit()

        assertOwed("2500.00")
        assertEquals(1, balanceRows.size)
    }

    @Test
    fun `clearing local history keeps an account whose transactions were recorded by hand`() = runBlocking {
        cardTransactions += cardTxn(1, daysAgo = 1, TransactionType.EXPENSE, "1000.00").copy(smsBody = null)
        markCredit()

        repository.deleteTransactionDerivedBalances()

        assertOwed("0")
        assertEquals(AccountBalanceRepository.SOURCE_MANUAL, balanceRows.single().sourceType)
    }

    @Test
    fun `fix 4 - a repayment marked as a transfer pair lowers what is owed`() = runBlocking {
        cardTransactions += cardTxn(1, daysAgo = 2, TransactionType.EXPENSE, "1000.00")
        // The card's leg of a marked pair: stored as TRANSFER, arriving on 4321.
        cardTransactions += cardTxn(2, daysAgo = 1, TransactionType.TRANSFER, "300.00").copy(
            transferPairId = "pair-1",
            fromAccount = "9999",
            toAccount = "4321"
        )

        markCredit()

        assertOwed("700.00")
    }

    @Test
    fun `fix 5 - an available balance in a debit-read SMS is not taken as what is owed`() = runBlocking {
        cardTransactions += cardTxn(1, daysAgo = 1, TransactionType.EXPENSE, "125.50", statedBalance = "3250.75", currency = "AED")

        markCredit()

        assertOwed("125.50")
    }

    @Test
    fun `an existing credit card account is left alone`() = runBlocking {
        markCredit()
        markCredit()

        assertEquals(1, balanceRows.size)
    }
}
