package com.pennywiseai.tracker.data.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.database.entity.LoanShareEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.LoanRepository
import com.pennywiseai.tracker.domain.loan.SplitShare
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * #346 against real SQLite: the 66→67 migration, the unique and foreign-key
 * constraints on loan_shares, and LoanRepository's split bookkeeping inside
 * real Room transactions.
 */
@RunWith(AndroidJUnit4::class)
class LoanSplitDatabaseTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, PennyWiseDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    private lateinit var db: PennyWiseDatabase
    private lateinit var repo: LoanRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, PennyWiseDatabase::class.java).build()
        repo = LoanRepository(db.loanDao(), db.transactionDao(), db)
    }

    @After
    fun tearDown() = db.close()

    private fun d(v: String) = BigDecimal(v)

    private suspend fun bill(amount: String, hash: String) = db.transactionDao().insertTransaction(
        TransactionEntity(
            amount = d(amount), merchantName = "Dinner", category = "Food", transactionType = TransactionType.EXPENSE,
            dateTime = LocalDateTime.of(2026, 10, 1, 20, 0), transactionHash = hash, currency = "INR"
        )
    )

    private suspend fun loanOf(name: String) = db.loanDao().getAllLoans().first().single { it.personName == name }

    @Test
    fun migration66To67AddsTheTableAndColumnAndKeepsRows() {
        val name = "split-migration-test"
        helper.createDatabase(name, 66).apply {
            execSQL(
                """INSERT INTO transactions (id, amount, merchant_name, category, transaction_type, date_time,
                transaction_hash, is_recurring, created_at, updated_at) VALUES
                (1, '1000', 'Dinner', 'Food', 'EXPENSE', '2026-10-01T20:00:00', 'synthetic', 0,
                '2026-10-01T20:00:00', '2026-10-01T20:00:00')"""
            )
            close()
        }
        helper.runMigrationsAndValidate(name, 67, true).use { m ->
            m.query("SELECT amount, lent_shares_total FROM transactions WHERE id = 1").use {
                assertTrue(it.moveToFirst())
                assertEquals("1000", it.getString(0))
                assertTrue(it.isNull(1))
            }
            m.query("SELECT COUNT(*) FROM loan_shares").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        }
        instrumentation.targetContext.deleteDatabase(name)
    }

    @Test
    fun splitWritesSharesLoansAndTotalAndUndoesCleanly() = runBlocking {
        val id = bill("1000", "b1")
        repo.saveSplit(id, listOf(SplitShare("Alex", d("333.33")), SplitShare("Sam", d("333.33"))))

        assertEquals(0, d("666.66").compareTo(db.transactionDao().getTransactionById(id)!!.lentSharesTotal))
        assertEquals(2, db.loanDao().getSharesForTransaction(id).size)
        assertEquals(0, d("333.33").compareTo(loanOf("Alex").remainingAmount))

        // The unique (transaction, loan) index: a second share for the same person is ignored.
        val alexShare = db.loanDao().getSharesForTransaction(id).first()
        assertEquals(-1L, db.loanDao().insertShare(LoanShareEntity(transactionId = id, loanId = alexShare.loanId, amount = d("1"))))

        repo.saveSplit(id, emptyList())
        assertTrue(db.loanDao().getAllShares().isEmpty())
        assertTrue(db.loanDao().getAllLoans().first().isEmpty())
        assertNull(db.transactionDao().getTransactionById(id)!!.lentSharesTotal)
    }

    @Test
    fun hardDeletingABillCascadesItsSharesAndDeletingALoanResyncsTheBill() = runBlocking {
        val one = bill("1000", "b1")
        val two = bill("500", "b2")
        repo.saveSplit(one, listOf(SplitShare("Alex", d("300")), SplitShare("Sam", d("200"))))
        repo.saveSplit(two, listOf(SplitShare("Alex", d("100"))))

        db.transactionDao().deleteTransactionById(two) // FK cascade
        assertEquals(2, db.loanDao().getAllShares().size)

        repo.deleteLoan(loanOf("Alex").id)
        assertEquals(1, db.loanDao().getAllShares().size)
        assertEquals(0, d("200").compareTo(db.transactionDao().getTransactionById(one)!!.lentSharesTotal))
    }

    @Test
    fun aSettledLoansShareCantChangeAndRemovingItKeepsTheLoanSettled() = runBlocking {
        val one = bill("1000", "b1")
        val two = bill("500", "b2")
        repo.saveSplit(one, listOf(SplitShare("Alex", d("300"))))
        repo.saveSplit(two, listOf(SplitShare("Alex", d("200"))))
        repo.settleLoans(listOf(loanOf("Alex").id))

        // Refused, and the Room transaction rolls the attempt back.
        assertTrue(runCatching { repo.saveSplit(one, listOf(SplitShare("Alex", d("301")))) }.isFailure)
        assertEquals(0, d("500").compareTo(loanOf("Alex").originalAmount))
        assertEquals(0, d("300").compareTo(db.transactionDao().getTransactionById(one)!!.lentSharesTotal))

        repo.saveSplit(one, emptyList())
        with(loanOf("Alex")) {
            assertEquals(LoanStatus.SETTLED, status)
            assertEquals(0, d("200").compareTo(originalAmount))
            assertEquals(0, BigDecimal.ZERO.compareTo(remainingAmount))
        }
    }
}
