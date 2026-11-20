package com.pennywiseai.tracker.debug

import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.ChatMessage
import com.pennywiseai.tracker.data.database.entity.expandWithChildren
import com.pennywiseai.tracker.data.database.longs
import com.pennywiseai.tracker.data.model.SpendingComparison
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.PlanningRepository
import com.pennywiseai.tracker.di.DatabaseCallback
import com.pennywiseai.tracker.domain.spending.RecurringCharge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class DemoSeedDataTest {
    private val date = LocalDate.of(2026, 3, 1)
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun seedIsRepeatableAndChatFiguresHoldOnTheFirstOfTheMonth() = runBlocking {
        val db = database()
        try {
            assertEquals(DemoSeedStatus.CREATED, db.seedDemoData(date) { "receipts/demo.pdf" })
            val seededCount = db.transactionDao().countAllTransactions()
            assertTrue(seededCount >= 4_000)
            assertEquals(DemoSeedStatus.ALREADY_SEEDED, db.seedDemoData(date.plusDays(1)) { error("Receipt already exists") })
            assertEquals(seededCount, db.transactionDao().countAllTransactions())
            assertEquals(listOf(12L), db.longs("SELECT count(DISTINCT substr(date_time, 1, 7)) FROM transactions"))
            assertEquals(listOf(4L), db.longs("SELECT count(DISTINCT bank_name) FROM transactions"))
            assertEquals(listOf(3L), db.longs("SELECT count(DISTINCT currency) FROM transactions"))
            assertEquals(listOf(0L), db.longs("SELECT count(*) FROM pragma_foreign_key_check"))
            assertEquals(2, db.goalDao().observeEffectiveContributions().first().size)
            assertEquals(2, db.chatDao().getMessageCount())
            assertTrue(db.webhookProfileDao().enabled().isEmpty())

            val categories = db.categoryDao().getAllCategoriesList().expandWithChildren(setOf("Food & Dining"))
            val splits = db.transactionSplitDao()
            val current = splits.getTransactionsWithSplitsAllCurrencies(date.atStartOfDay(), date.atTime(LocalTime.MAX)).first()
            val previous = splits.getTransactionsWithSplitsAllCurrencies(date.minusMonths(1).atStartOfDay(), date.atStartOfDay().minusNanos(1)).first()
            assertEquals(mapOf("JOD" to "32", "USD" to "18", "EUR" to "9.5"),
                SpendingComparison.spend(current, categories).plainAmounts())
            assertEquals(mapOf("JOD" to "20", "USD" to "10", "EUR" to "6"),
                SpendingComparison.spend(previous, categories).plainAmounts())
            assertEquals(mapOf("JOD" to "30"), SpendingComparison.spend(current, setOf("Groceries")).plainAmounts())
            assertEquals(mapOf("JOD" to "18"), SpendingComparison.spend(current, setOf("Shopping")).plainAmounts())

            val bills = PlanningRepository.billsDue(
                db.subscriptionDao().getActiveSubscriptions().first(), date, days = 7, incomeCategories = setOf("Salary"),
                advance = { day, cycle -> RecurringCharge.advance(day, cycle) }
            )
            assertEquals("Demo Internet", bills.overdue.single().merchantName)
            assertEquals("Demo Streaming", bills.upcoming.single().merchantName)
        } finally {
            db.close()
        }
    }

    @Test
    fun customizedDefaultCategoryBlocksSeed() = runBlocking {
        val db = database()
        try {
            val category = checkNotNull(db.categoryDao().getCategoryByName("Food & Dining"))
            db.categoryDao().updateCategory(category.copy(color = "#001122"))
            assertEquals(DemoSeedStatus.HAS_DATA, db.seedDemoData(date) { error("Seed must skip receipt") })
            assertEquals(0, db.transactionDao().countAllTransactions())
        } finally {
            db.close()
        }
    }

    @Test
    fun automaticSeedRemembersSkippedFirstLaunchAfterExistingDataIsCleared() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO)
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getNoBackupFilesDir() = temporaryFolder.root
            override fun getFilesDir() = temporaryFolder.root
        }
        val store = PreferenceDataStoreFactory.create(scope = scope) {
            temporaryFolder.root.resolve("demo-test.preferences_pb")
        }
        val preferences = UserPreferencesRepository(store) {}
        val db = database()
        try {
            db.chatDao().insertMessage(ChatMessage(id = "existing", message = "Existing test history", isUser = true))
            DemoSeeder(context, db, preferences).prepare()
            assertFalse(preferences.userPreferences.first().hasCompletedOnboarding)
            assertEquals(1, db.chatDao().getMessageCount())
            db.chatDao().deleteAllMessages()

            val seeder = DemoSeeder(context, db, preferences)
            seeder.prepare()
            assertEquals(0, db.transactionDao().countAllTransactions())
            assertEquals(DemoSeedStatus.CREATED, seeder.seed(date))
            assertTrue(preferences.userPreferences.first().hasCompletedOnboarding)
        } finally {
            db.close()
            scope.cancel()
        }
    }

    @Test
    fun nonTransactionDataBlocksSeedWithoutChanges() = runBlocking {
        val db = database()
        try {
            val message = ChatMessage(id = "existing", message = "Existing test history", isUser = true)
            db.chatDao().insertMessage(message)
            assertEquals(DemoSeedStatus.HAS_DATA, db.seedDemoData(date) { error("Seed must skip receipt") })
            assertEquals(listOf(message), db.chatDao().getAllMessages().first())
            assertEquals(0, db.transactionDao().countAllTransactions())
        } finally {
            db.close()
        }
    }

    @Test
    fun failureRollsBackAllRowsAndAllowsRetry() = runBlocking {
        val db = database()
        try {
            val error = runCatching { db.seedDemoData(date) { error("Could not write receipt") } }.exceptionOrNull()
            assertEquals("Could not write receipt", error?.message)
            assertEquals(0, db.transactionDao().countAllTransactions())
            assertEquals(0, db.chatDao().getMessageCount())
            val retryDate = date.plusDays(19)
            assertEquals(DemoSeedStatus.CREATED, db.seedDemoData(retryDate) { "receipts/demo.pdf" })
            val current = db.transactionSplitDao()
                .getTransactionsWithSplitsAllCurrencies(date.atStartOfDay(), retryDate.atTime(LocalTime.MAX)).first()
            assertTrue(current.map { it.transaction.dateTime.toLocalDate() }.distinct().size >= 5)
        } finally {
            db.close()
        }
    }

    private fun database() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, PennyWiseDatabase::class.java
    ).setDriver(AndroidSQLiteDriver()).addCallback(DatabaseCallback()).build()

    private fun Map<String, BigDecimal>.plainAmounts() = mapValues { (_, value) -> value.stripTrailingZeros().toPlainString() }
}
