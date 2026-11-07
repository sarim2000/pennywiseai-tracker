package com.pennywiseai.tracker.domain.usecase

import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.model.TransactionDraft
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TagRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import androidx.room3.useWriterConnection
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

/** The rows of a chat review card reach the real database all together or not at all. */
@RunWith(AndroidJUnit4::class)
class AddTransactionBatchTest {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, PennyWiseDatabase::class.java
    ).build()
    private val useCase = AddTransactionUseCase(
        db,
        SubscriptionRepository(db.subscriptionDao(), db.categoryDao()),
        AccountBalanceRepository(
            db.accountBalanceDao(), db.transactionDao(), db,
            UserPreferencesRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        ),
        TagRepository(db, db.tagDao())
    )
    private val day = LocalDate.of(2026, 1, 15)

    @After fun close() = db.close()

    private fun draft(amount: String, merchant: String) = TransactionDraft(
        amount = BigDecimal(amount), currency = "INR", date = day, merchant = merchant, category = "Others",
        type = TransactionType.EXPENSE, bankName = null, accountLast4 = null, accountLabel = "Cash",
        sourceText = "synthetic", amountInMessage = true
    )

    private suspend fun rows() = db.transactionDao().getAllTransactions().first()

    private suspend fun sql(statement: String) = db.useWriterConnection { it.usePrepared(statement) { s -> s.step() } }

    /** Makes the database treat a row named Tea with [action], the way a full disk or a conflict would. */
    private suspend fun onTea(action: String) =
        sql("CREATE TRIGGER tea BEFORE INSERT ON transactions WHEN NEW.merchant_name = 'Tea' BEGIN SELECT RAISE($action); END")

    @Test fun identicalRowsAreBothSavedOnTheirDay() = runBlocking {
        useCase.executeAll(listOf(draft("50", "Coffee"), draft("50", "Coffee")))
        val saved = rows()
        assertEquals(2, saved.size)
        assertEquals(2, saved.map { it.transactionHash }.toSet().size)
        assertTrue(saved.all { it.dateTime.toLocalDate() == day })
    }

    @Test fun aFailureRollsEveryRowBackAndARetrySavesEachOnce() = runBlocking {
        onTea("ABORT, 'disk full'")
        val drafts = listOf(draft("50", "Coffee"), draft("80", "Tea"))
        runCatching { useCase.executeAll(drafts) }.onSuccess { fail("should have failed") }
        assertEquals(0, rows().size)

        sql("DROP TRIGGER tea")
        useCase.executeAll(drafts)
        assertEquals(listOf("Coffee", "Tea"), rows().map { it.merchantName }.sorted())
    }

    @Test fun aRefusedRowFailsTheCardInsteadOfVanishing() = runBlocking {
        onTea("IGNORE")
        runCatching { useCase.executeAll(listOf(draft("50", "Coffee"), draft("80", "Tea"))) }
            .onSuccess { fail("should have failed") }
        assertEquals(0, rows().size)
    }
}
