package com.pennywiseai.tracker.data.backup

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.RuleApplicationEntity
import com.pennywiseai.tracker.data.database.entity.RuleEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionTagCrossRef
import com.pennywiseai.tracker.data.database.entity.TransactionType
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * A backup row whose parent transaction is not in the backup (deleted before export) must be
 * skipped and counted, never take the rows that can import down with it (#1513). Real Room, real SQLite.
 */
@RunWith(AndroidJUnit4::class)
class BackupImportOrphanRowsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, PennyWiseDatabase::class.java)
        .setDriver(AndroidSQLiteDriver()).build()
    private val importer = BackupImporter(
        context, db, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)
    )
    private val at = LocalDateTime.of(2026, 10, 1, 12, 0)

    @After fun close() = db.close()

    private fun tx(id: Long) = TransactionEntity(
        id = id, amount = BigDecimal("10"), merchantName = "Shop $id", category = "Food",
        transactionType = TransactionType.EXPENSE, dateTime = at, transactionHash = "hash$id"
    )

    private val rule = RuleEntity(
        id = "r1", name = "Rule", description = null, priority = 1, conditions = "[]", actions = "[]",
        isActive = true, createdAt = at, updatedAt = at
    )

    private fun application(id: String, transactionId: Long) = RuleApplicationEntity(
        id = id, ruleId = "r1", ruleName = "Rule", transactionId = transactionId.toString(),
        fieldsModified = "[]", appliedAt = at
    )

    // Ids 1 and 2 are in the backup; 99 is a transaction deleted before export.
    private fun backupWithOrphans() = PennyWiseBackup(
        database = DatabaseSnapshot(
            transactions = listOf(tx(1), tx(2)),
            rules = listOf(rule),
            ruleApplications = listOf(application("good", 1), application("orphan", 99)),
            transactionSplits = listOf(
                TransactionSplitEntity(transactionId = 1, category = "Food", amount = BigDecimal("4")),
                TransactionSplitEntity(transactionId = 99, category = "Food", amount = BigDecimal("4"))
            ),
            transactionTagCrossRefs = listOf(TransactionTagCrossRef(transactionId = 99, tagId = 1))
        )
    )

    private fun file(backup: PennyWiseBackup) = File.createTempFile("backup", ".json", context.cacheDir).apply {
        writeText(backupJson.encodeToString(backup))
        deleteOnExit()
    }

    private suspend fun assertOnlyOrphansSkipped(strategy: ImportStrategy) {
        val result = importer.importBackup(android.net.Uri.fromFile(file(backupWithOrphans())), strategy)

        assertTrue("$result", result is ImportResult.Success)
        result as ImportResult.Success
        assertEquals(2, result.importedTransactions)
        assertEquals(2, db.transactionDao().getAllTransactions().first().size)
        assertEquals(listOf("good"), db.ruleApplicationDao().getAllApplications().first().map { it.id })
        assertEquals(1, db.transactionSplitDao().getAllSplits().first().size)
        assertEquals("orphan application, split and tag link are counted", 3, result.skippedRows)
    }

    @Test fun mergeKeepsImportableRowsAndSkipsOrphans() = runBlocking { assertOnlyOrphansSkipped(ImportStrategy.MERGE) }

    @Test fun replaceAllKeepsImportableRowsAndSkipsOrphans() = runBlocking { assertOnlyOrphansSkipped(ImportStrategy.REPLACE_ALL) }
}
