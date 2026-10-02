package com.pennywiseai.tracker.data.webhook

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.repository.WebhookRepository
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.*
import com.pennywiseai.tracker.data.database.dao.WebhookProfileDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class WebhookDatabaseTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @get:Rule val helper = MigrationTestHelper(instrumentation, PennyWiseDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun upgradeFrom62PreservesTransactionsAndValidatesSchema() {
        val name = "webhook-migration-test"
        helper.createDatabase(name, 62).apply {
            execSQL("""INSERT INTO transactions (id, amount, merchant_name, category, transaction_type, date_time,
                transaction_hash, is_recurring, created_at, updated_at) VALUES
                (1, '12.50', 'Test merchant', 'Food', 'EXPENSE', '2026-01-01T12:00:00',
                'synthetic', 0, '2026-01-01T12:00:00', '2026-01-01T12:00:00')""")
            close()
        }
        helper.runMigrationsAndValidate(name, 64, true, PennyWiseDatabase.MIGRATION_62_63, PennyWiseDatabase.MIGRATION_63_64).use { db ->
            db.query("SELECT amount FROM transactions WHERE id = 1").use {
                assertTrue(it.moveToFirst())
                assertEquals("12.50", it.getString(0))
            }
            db.query("SELECT COUNT(*) FROM webhook_profiles").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        }
        instrumentation.targetContext.deleteDatabase(name)
    }

    @Test fun editPreservesLogsAndCursorsAndDeleteCascades() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, PennyWiseDatabase::class.java).build()
        try {
            val profile = WebhookProfileEntity(id = "test-profile", name = "Test", url = "https://example.com")
            db.webhookProfileDao().upsert(profile)
            db.webhookLogDao().insert(WebhookLogEntity(profileId = profile.id, profileName = profile.name,
                syncReason = WebhookSyncReason.TEST, status = WebhookLogStatus.SUCCESS, message = "Delivered"))
            db.webhookCursorDao().upsert(WebhookCursorEntity(profile.id, WebhookDataType.TRANSACTIONS, LocalDateTime.now()))
            db.webhookProfileDao().upsert(profile.copy(name = "Edited"))
            assertEquals(1, db.webhookCursorDao().forProfile(profile.id).size)
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM webhook_logs").use {
                assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
            }
            db.webhookProfileDao().delete(profile.id)
            assertTrue(db.webhookCursorDao().forProfile(profile.id).isEmpty())
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM webhook_logs").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
        } finally { db.close() }
    }

    @Test fun incrementalQueryIncludesOldEditsAndDeletionsAndRespectsCurrencyAndUpperBound() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, PennyWiseDatabase::class.java).build()
        try {
            val start = LocalDateTime.of(2026, 1, 1, 12, 0)
            val end = start.plusHours(1)
            val transaction = TransactionEntity(amount = BigDecimal("10"), merchantName = "Test merchant", category = "Food",
                transactionType = TransactionType.EXPENSE, dateTime = start.minusYears(1), transactionHash = "synthetic",
                updatedAt = start.plusMinutes(1))
            db.transactionDao().insertTransaction(transaction.copy(id = 1))
            db.transactionDao().insertTransaction(transaction.copy(id = 2, transactionHash = "deleted", isDeleted = true))
            db.transactionDao().insertTransaction(transaction.copy(id = 3, transactionHash = "foreign", currency = "USD"))
            db.transactionDao().insertTransaction(transaction.copy(id = 4, transactionHash = "future", updatedAt = end.plusNanos(1)))
            val changes = db.transactionDao().getWebhookChanges(start, end, "INR")
            assertEquals(listOf(1L, 2L), changes.map { it.id })
            assertTrue(changes.last().isDeleted)
        } finally { db.close() }
    }
    @Test fun endpointEditDuringDeliveryCannotCommitOldCursors() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, PennyWiseDatabase::class.java).build()
        try {
            val profile = WebhookProfileEntity(id = "test-profile", name = "Test", url = "https://example.com/old")
            db.webhookProfileDao().upsert(profile)
            db.webhookProfileDao().upsert(profile.copy(url = "https://example.com/new"))
            val repository = WebhookRepository(db, db.webhookProfileDao(), db.webhookLogDao(), db.webhookCursorDao())
            val now = LocalDateTime.now()
            repository.markSuccess(profile, now, listOf(WebhookCursorUpdate(WebhookDataType.TRANSACTIONS, now, now)))
            assertTrue(db.webhookCursorDao().forProfile(profile.id).isEmpty())
            assertNull(db.webhookProfileDao().byId(profile.id)?.lastSyncedAt)
            repository.recordDelivery(profile, listOf(WebhookTransactionPayload("txn_1", "upsert", updatedAt = now.toString())))
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM webhook_delivered_transactions").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
        } finally { db.close() }
    }

    @Test fun currencyChangesRemoveOnlyPreviouslyDeliveredRowsAndRetainFailedBatchReceipts() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, PennyWiseDatabase::class.java).build()
        try {
            val profile = WebhookProfileEntity(id = "test-profile", name = "Test", url = "https://example.com")
            db.webhookProfileDao().upsert(profile)
            val repository = WebhookRepository(db, db.webhookProfileDao(), db.webhookLogDao(), db.webhookCursorDao())
            val now = LocalDateTime.of(2026, 1, 1, 12, 0)
            val row = TransactionEntity(id = 1, amount = BigDecimal.ONE, merchantName = "Test merchant", category = "Food",
                transactionType = TransactionType.EXPENSE, dateTime = now, updatedAt = now, transactionHash = "currency")
            db.transactionDao().insertTransaction(row)
            // Disabling or changing filters after the POST does not undo its acceptance at the same receiver.
            db.webhookProfileDao().upsert(profile.copy(enabled = false, dataTypes = "TRANSACTIONS"))
            repository.recordDelivery(profile, listOf(webhookTransactionPayload(row)))
            db.webhookProfileDao().upsert(profile)
            // A later batch fails, so no cursor is committed. The successful receipt still matters.
            db.transactionDao().updateTransaction(row.copy(currency = "USD", updatedAt = now.plusMinutes(1)))
            db.transactionDao().insertTransaction(row.copy(id = 2, currency = "USD", transactionHash = "never-delivered"))
            val changes = db.transactionDao().getWebhookChanges(now, now.plusHours(1), "INR", profile.id)
            assertEquals(listOf(1L), changes.map { it.id })
            assertTrue(db.transactionDao().getWebhookChanges(now, now.plusHours(1), "INR", "another-profile").isEmpty())
            val removal = webhookTransactionPayload(changes.single(), "INR")
            assertEquals("delete", removal.action)
            assertNull(removal.amount)
            assertNull(removal.currency)
            assertEquals(listOf(1L), db.transactionDao().getWebhookCurrencyRemovals("INR", profile.id).map { it.id })
            repository.recordDelivery(profile, listOf(removal))
            assertTrue(db.transactionDao().getWebhookChanges(now, now.plusHours(1), "INR", profile.id).isEmpty())
            // Moving back into INR is a normal upsert.
            db.transactionDao().updateTransaction(row.copy(updatedAt = now.plusMinutes(2)))
            assertEquals("upsert", webhookTransactionPayload(db.transactionDao()
                .getWebhookChanges(now, now.plusHours(1), "INR", profile.id).single(), "INR").action)
            repository.recordDelivery(profile, listOf(webhookTransactionPayload(row)))
            db.webhookProfileDao().delete(profile.id)
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM webhook_delivered_transactions").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
        } finally { db.close() }
    }

    @Test fun profileCurrencyEditKeepsReceiptsIncludingInFlightBatchUntilRemovalsSucceed() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, PennyWiseDatabase::class.java).build()
        try {
            val profile = WebhookProfileEntity(id = "test-profile", name = "Test", url = "https://example.com")
            db.webhookProfileDao().upsert(profile)
            val repository = WebhookRepository(db, db.webhookProfileDao(), db.webhookLogDao(), db.webhookCursorDao())
            val now = LocalDateTime.of(2026, 1, 1, 12, 0)
            val row = TransactionEntity(amount = BigDecimal.ONE, merchantName = "Test merchant", category = "Food",
                transactionType = TransactionType.EXPENSE, dateTime = now, updatedAt = now, transactionHash = "profile-currency")
            db.transactionDao().insertTransaction(row.copy(id = 1))
            db.transactionDao().insertTransaction(row.copy(id = 2, transactionHash = "in-flight"))
            repository.recordDelivery(profile, listOf(webhookTransactionPayload(row.copy(id = 1))))
            db.webhookCursorDao().upsert(WebhookCursorEntity(profile.id, WebhookDataType.TRANSACTIONS, now))
            val draft = WebhookProfileDraft(id = profile.id, name = profile.name, url = profile.url,
                enabled = true, currency = "USD", dataTypes = setOf(WebhookDataType.TRANSACTIONS),
                rangePreset = WebhookRangePreset.SINCE_LAST_SUCCESS, headers = emptyList())
            repository.save(draft)
            // The INR batch was already in flight when the profile switched to USD.
            repository.recordDelivery(profile, listOf(webhookTransactionPayload(row.copy(id = 2))))
            assertTrue(repository.getCursors(profile.id).isEmpty())
            val removals = db.transactionDao().getWebhookChanges(now, now.plusHours(1), "USD", profile.id)
            assertEquals(listOf(1L, 2L), removals.map { it.id })
            val current = requireNotNull(repository.profile(profile.id))
            repository.recordDelivery(current, removals.map { webhookTransactionPayload(it, "USD") })
            assertTrue(db.transactionDao().getWebhookCurrencyRemovals("USD", profile.id).isEmpty())
            // A new endpoint must not inherit the former receiver's delivery tracking.
            repository.recordDelivery(current, listOf(webhookTransactionPayload(row.copy(id = 1))))
            repository.save(draft.copy(url = "https://example.com/new"))
            assertTrue(db.transactionDao().getWebhookCurrencyRemovals("USD", profile.id).isEmpty())
        } finally { db.close() }
    }

    @Test fun upgradeFrom63PreservesWebhookConfiguration() {
        val name = "webhook-receipts-migration-test"
        helper.createDatabase(name, 63).apply {
            execSQL("""INSERT INTO webhook_profiles (id, name, url, range_preset, created_at, updated_at)
                VALUES ('synthetic-profile', 'Test', 'https://example.com', 'SINCE_LAST_SUCCESS',
                '2026-01-01T12:00:00', '2026-01-01T12:00:00')""")
            close()
        }
        helper.runMigrationsAndValidate(name, 64, true, PennyWiseDatabase.MIGRATION_63_64).use { db ->
            db.query("SELECT name FROM webhook_profiles WHERE id = 'synthetic-profile'").use {
                assertTrue(it.moveToFirst()); assertEquals("Test", it.getString(0))
            }
        }
        instrumentation.targetContext.deleteDatabase(name)
    }

    @Test fun togglePreservesEditsAndDeliveryStatusCommittedBeforeItsWrite() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, PennyWiseDatabase::class.java).build()
        try {
            val profile = WebhookProfileEntity(id = "test-profile", name = "Original", url = "https://example.com/old")
            val latest = profile.copy(name = "Edited", url = "https://example.com/new", currency = "USD",
                dataTypes = "ACCOUNTS", lastSyncedAt = LocalDateTime.of(2026, 1, 1, 12, 0),
                lastError = "HTTP 503", consecutiveFailures = 2)
            val dao = db.webhookProfileDao()
            dao.upsert(profile)
            val interleaved = object : WebhookProfileDao by dao {
                override suspend fun update(profile: WebhookProfileEntity) {
                    dao.upsert(latest)
                    dao.update(profile)
                }
                override suspend fun setEnabled(id: String, enabled: Boolean, at: LocalDateTime) {
                    dao.upsert(latest)
                    dao.setEnabled(id, enabled, at)
                }
            }
            val repository = WebhookRepository(db, interleaved, db.webhookLogDao(), db.webhookCursorDao())
            repository.setEnabled(profile.id, false)
            val saved = requireNotNull(dao.byId(profile.id))
            assertFalse(saved.enabled)
            assertEquals(latest.name, saved.name)
            assertEquals(latest.url, saved.url)
            assertEquals(latest.currency, saved.currency)
            assertEquals(latest.dataTypes, saved.dataTypes)
            assertEquals(latest.lastSyncedAt, saved.lastSyncedAt)
            assertEquals(latest.lastError, saved.lastError)
            assertEquals(latest.consecutiveFailures, saved.consecutiveFailures)
        } finally { db.close() }
    }

    @Test fun androidClientDeliversSyntheticJsonAndHeadersToLoopback() = runBlocking {
        val server = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val received = java.util.concurrent.CompletableFuture<Pair<List<String>, String>>()
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        executor.submit {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 10_000
                    val reader = socket.getInputStream().bufferedReader()
                    val headers = mutableListOf<String>()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        headers += line
                    }
                    val length = headers.first { it.startsWith("Content-Length:", true) }.substringAfter(':').trim().toInt()
                    val body = CharArray(length)
                    var offset = 0
                    while (offset < length) {
                        val count = reader.read(body, offset, length - offset)
                        if (count < 0) break
                        offset += count
                    }
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}".toByteArray())
                    socket.getOutputStream().flush()
                    received.complete(headers to String(body))
                }
            } catch (failure: Exception) { received.completeExceptionally(failure) }
        }
        try {
            val now = LocalDateTime.now().toString()
            val payload = WebhookEnvelope(generatedAt = now, app = WebhookAppInfo("Test", "test"),
                profile = WebhookProfileInfo("synthetic-profile", "Test"),
                request = WebhookRequestInfo("test", now, now, "INR", listOf("transactions")),
                batch = WebhookBatchInfo("synthetic-batch", 1, 1))
            val result = WebhookDeliveryService().deliver("http://127.0.0.1:${server.localPort}/hook",
                listOf(WebhookHeader("X-Test", "synthetic-value")), payload)
            assertTrue(result.message, result.success)
            val (headers, body) = received.get(10, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue(headers.any { it.equals("X-Test: synthetic-value", true) })
            val json = kotlinx.serialization.json.Json.parseToJsonElement(body).toString()
            assertTrue(json.contains("\"schema_version\":\"1.0\""))
            assertTrue(json.contains("\"range\":\"test\""))
        } finally {
            server.close()
            executor.shutdownNow()
        }
    }

}
