package com.pennywiseai.tracker.data.repository

import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.dao.BalanceRowAfter
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.utils.BalanceCalculator
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * #982: a bank whose SMS state no balance gets its balance as "anchor ± each
 * transaction". The inbox scan saves SMS out of time order (parallel parsers),
 * so each SMS must move from the balance just before it in time and carry its
 * change to the rows after it — not add itself to whatever row was newest.
 */
class SmsBalanceOrderTest {

    private val rows = mutableListOf<AccountBalanceEntity>()
    private val stated = mutableMapOf<Long, Boolean>() // transaction id → SMS stated a balance
    private var nextId = 1L

    // In-memory account_balances with the (bank, account, timestamp) REPLACE index.
    private val dao = Proxy.newProxyInstance(
        AccountBalanceDao::class.java.classLoader, arrayOf(AccountBalanceDao::class.java)
    ) { _, method, args ->
        fun account(r: AccountBalanceEntity) = r.bankName == args[0] && r.accountLast4 == args[1]
        when (method.name) {
            "insertBalance" -> {
                val row = args[0] as AccountBalanceEntity
                rows.removeAll { it.bankName == row.bankName && it.accountLast4 == row.accountLast4 && it.timestamp == row.timestamp }
                rows += row.copy(id = nextId++)
                nextId - 1
            }
            "getLatestBalance" -> rows.filter(::account).maxByOrNull { it.timestamp }
            "getBalanceAtOrBefore" -> rows.filter { account(it) && it.timestamp <= args[2] as LocalDateTime }
                .maxByOrNull { it.timestamp }
            "getBalanceRowsAfter" -> rows.filter { account(it) && it.timestamp > args[2] as LocalDateTime }
                .sortedBy { it.timestamp }
                .map {
                    val rebuilding = args[3] as Boolean
                    val derived = it.sourceType == "TRANSACTION" &&
                        ((it.smsSource == null && !rebuilding) ||
                            (it.smsSource != null && stated[it.transactionId] == false))
                    BalanceRowAfter(it.id, it.balance, it.creditLimit, derived)
                }
            "getBalancesForTransaction" -> rows.filter { it.transactionId == args[0] }
            "deleteBalanceById" -> rows.removeAll { it.id == args[0] }.let { }
            "updateBalanceAndLimitById" -> {
                val i = rows.indexOfFirst { it.id == args[0] }
                rows[i] = rows[i].copy(balance = args[1] as BigDecimal, creditLimit = args[2] as BigDecimal?)
                Unit
            }
            else -> error("unexpected ${method.name}")
        }
    } as AccountBalanceDao

    private val database = mockk<PennyWiseDatabase>()
    private val repo = AccountBalanceRepository(dao, mockk(), database)

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    @After
    fun tearDown() = unmockkStatic("androidx.room.RoomDatabaseKt")

    private fun at(day: Int, h: Int, m: Int) = LocalDateTime.of(2026, 1, day, h, m)

    /** What both SMS paths do for a transaction SMS on a debit account. */
    private fun sms(
        id: Long, time: LocalDateTime, type: TransactionType, amount: String, statedBalance: String? = null,
        availableLimit: String? = null, card: Boolean = false, rebuilding: Boolean = false
    ) = runBlocking {
        stated[id] = statedBalance != null
        val latestLimit = rows.filter { it.bankName == BANK && it.accountLast4 == ACCT }
            .maxByOrNull { it.timestamp }?.creditLimit
        repo.insertSmsTransactionBalance(BANK, ACCT, time, rebuilding) { before ->
            val balance = BalanceCalculator.balanceAfter(
                statedBalance?.let(::BigDecimal), card, type, BigDecimal(amount), "INR", "INR", before
            )
            AccountBalanceEntity(
                bankName = BANK, accountLast4 = ACCT, timestamp = time, transactionId = id,
                smsSource = "test sms $id", sourceType = "TRANSACTION", balance = balance, isCreditCard = card,
                // As both SMS paths do (#486): available + outstanding, else the account's limit.
                creditLimit = availableLimit?.let { BigDecimal(it) + balance } ?: latestLimit
            )
        }
    }

    /** A signed delta row stamped "now", as a manual add or a delete/edit writes. */
    private fun delta(time: LocalDateTime, transactionId: Long, balance: String, creditLimit: String? = null) {
        rows += AccountBalanceEntity(
            id = nextId++, bankName = BANK, accountLast4 = ACCT, balance = BigDecimal(balance),
            timestamp = time, transactionId = transactionId, sourceType = "TRANSACTION",
            creditLimit = creditLimit?.let(::BigDecimal)
        )
    }

    private fun anchor(time: LocalDateTime, balance: String, creditLimit: String? = null) {
        rows += AccountBalanceEntity(
            id = nextId++, bankName = BANK, accountLast4 = ACCT, balance = BigDecimal(balance),
            timestamp = time, sourceType = AccountBalanceRepository.SOURCE_MANUAL,
            creditLimit = creditLimit?.let(::BigDecimal), isCreditCard = creditLimit != null
        )
    }

    private fun latest() = rows.filter { it.bankName == BANK && it.accountLast4 == ACCT }.maxBy { it.timestamp }

    private fun history() = rows.sortedBy { it.timestamp }.map { it.timestamp.toLocalTime().toString() to it.balance.toPlainString() }

    @Test
    fun `balance-less SMS saved out of time order give the time-order history`() {
        anchor(at(1, 21, 20), "1000.00")
        // Saved in the scrambled order seen in #982, not in time order.
        sms(4, at(2, 2, 10), TransactionType.EXPENSE, "50.00")
        sms(2, at(2, 1, 15), TransactionType.EXPENSE, "3000.00")
        sms(5, at(2, 3, 30), TransactionType.EXPENSE, "40.00")
        sms(8, at(2, 3, 50), TransactionType.EXPENSE, "400.00")
        sms(7, at(2, 3, 40), TransactionType.EXPENSE, "10.00")
        sms(3, at(2, 2, 45), TransactionType.EXPENSE, "300.00")
        sms(1, at(2, 1, 0), TransactionType.INCOME, "3000.00")

        assertEquals(
            listOf(
                "21:20" to "1000.00",
                "01:00" to "4000.00",
                "01:15" to "1000.00",
                "02:10" to "950.00",
                "02:45" to "650.00",
                "03:30" to "610.00",
                "03:40" to "600.00",
                "03:50" to "200.00",
            ),
            history()
        )
        assertEquals(BigDecimal("200.00"), runBlocking { repo.getLatestBalance(BANK, ACCT) }!!.balance)
    }

    @Test
    fun `a later anchor stops the shift and a stated balance saved late rebases what follows it`() {
        anchor(at(1, 9, 0), "1000.00")
        anchor(at(1, 18, 0), "5000.00") // Update Balance later that day
        sms(2, at(1, 12, 0), TransactionType.EXPENSE, "100.00")
        sms(1, at(1, 10, 0), TransactionType.EXPENSE, "50.00")
        // A stated-balance SMS saved after a balance-less one that came after it.
        sms(4, at(1, 20, 0), TransactionType.EXPENSE, "20.00")
        sms(3, at(1, 19, 0), TransactionType.EXPENSE, "500.00", statedBalance = "4400.00")

        assertEquals(
            listOf(
                "09:00" to "1000.00",
                "10:00" to "950.00",
                "12:00" to "850.00",
                "18:00" to "5000.00", // anchor kept
                "19:00" to "4400.00", // the SMS's own figure kept
                "20:00" to "4380.00",
            ),
            history()
        )
    }

    @Test
    fun `an SMS applied again after its rows are removed counts once`() {
        anchor(at(1, 9, 0), "1000.00")
        sms(1, at(1, 10, 0), TransactionType.EXPENSE, "100.00")
        sms(2, at(1, 11, 0), TransactionType.EXPENSE, "50.00")
        // The scan replaces transaction 1 with a better read of the same charge.
        runBlocking { repo.removeSmsTransactionBalances(1) }
        sms(1, at(1, 10, 1), TransactionType.EXPENSE, "100.00")

        assertEquals(listOf("09:00" to "1000.00", "10:01" to "900.00", "11:00" to "850.00"), history())
    }

    @Test
    fun `a resync putting SMS back leaves a manual add that already holds them alone`() {
        sms(1, at(1, 9, 0), TransactionType.EXPENSE, "10.00", statedBalance = "1000.00")
        sms(2, at(1, 10, 0), TransactionType.EXPENSE, "100.00")
        delta(at(1, 12, 0), 99, "850.00") // a manual 50 expense, added later
        // Force resync: the SMS transactions and their rows go; the manual add's row stays.
        rows.removeAll { it.transactionId == 1L || it.transactionId == 2L }
        sms(2, at(1, 10, 0), TransactionType.EXPENSE, "100.00", rebuilding = true)
        sms(1, at(1, 9, 0), TransactionType.EXPENSE, "10.00", statedBalance = "1000.00", rebuilding = true)

        assertEquals(listOf("09:00" to "1000.00", "10:00" to "900.00", "12:00" to "850.00"), history())
    }

    @Test
    fun `removing a transaction with an edit row takes each row back once`() {
        anchor(at(1, 9, 0), "1000.00")
        sms(1, at(1, 10, 0), TransactionType.EXPENSE, "100.00")
        sms(2, at(1, 11, 0), TransactionType.EXPENSE, "20.00")
        delta(at(1, 12, 0), 1, "830.00") // the user edits transaction 1 from 100 to 150
        sms(3, at(1, 13, 0), TransactionType.EXPENSE, "30.00")

        runBlocking { repo.removeSmsTransactionBalances(1) }

        assertEquals(listOf("09:00" to "1000.00", "11:00" to "980.00", "13:00" to "950.00"), history())
    }

    @Test
    fun `a card SMS saved late keeps the available limit the later SMS stated`() {
        sms(2, at(1, 12, 0), TransactionType.CREDIT, "100.00", availableLimit = "8850.00", card = true)
        sms(1, at(1, 10, 0), TransactionType.CREDIT, "50.00", availableLimit = "8950.00", card = true)

        val card = latest()
        assertEquals(BigDecimal("150.00"), card.balance)
        assertEquals(BigDecimal("8850.00"), card.creditLimit!! - card.balance)
    }

    @Test
    fun `a card limit the user set is not moved by an SMS saved late`() {
        anchor(at(1, 9, 0), "0.00", creditLimit = "10000.00")
        sms(2, at(1, 12, 0), TransactionType.CREDIT, "100.00", card = true)
        delta(at(1, 13, 0), 99, "130.00", creditLimit = "10000.00") // a manual 30 card spend
        sms(1, at(1, 10, 0), TransactionType.CREDIT, "50.00", card = true)

        val card = latest()
        assertEquals(BigDecimal("180.00"), card.balance)
        assertEquals(BigDecimal("10000.00"), card.creditLimit)
    }

    private companion object {
        const val BANK = "Test Bank"
        const val ACCT = "1111"
    }
}
