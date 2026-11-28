package com.pennywiseai.tracker.data.backup

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.database.entity.RuleApplicationEntity
import com.pennywiseai.tracker.data.database.entity.RuleEntity
import com.pennywiseai.tracker.data.database.entity.TagEntity
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

    private fun tx(id: Long, hash: String = "hash$id") = TransactionEntity(
        id = id, amount = BigDecimal("10"), merchantName = "Shop $id", category = "Food",
        transactionType = TransactionType.EXPENSE, dateTime = at, transactionHash = hash
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

    // Both contributions pass the parent check (their goal restores) and then clash on the unique uid
    // index: a SQL failure no pre-check can catch. It must fail the import, never come back as a
    // Success that saved nothing. (Only REPLACE_ALL reaches it: MERGE dedups contributions by uid.)
    @Test fun replaceAllFailsOnASqlErrorInsteadOfReportingSuccess() = runBlocking {
        val backup = PennyWiseBackup(
            database = DatabaseSnapshot(
                transactions = listOf(tx(1), tx(2)),
                goals = listOf(GoalEntity(id = 1, uid = "goal")),
                goalContributions = listOf(
                    GoalContributionEntity(uid = "dup", goalId = 1, amount = BigDecimal("5")),
                    GoalContributionEntity(uid = "dup", goalId = 1, amount = BigDecimal("6"))
                )
            )
        )

        val result = importer.importBackup(android.net.Uri.fromFile(file(backup)), ImportStrategy.REPLACE_ALL)

        assertTrue("$result", result is ImportResult.Error)
        assertEquals(0, db.transactionDao().getAllTransactions().first().size)
    }

    // Rows 1 and 2 share a hash: the second insert is ignored (-1). Its dependants must be skipped and
    // counted, never stored against id -1 (which would break the foreign key and sink the import).
    private suspend fun assertDuplicateHashRowIsNotAParent(strategy: ImportStrategy, expectedSkippedRows: Int) {
        val backup = PennyWiseBackup(
            database = DatabaseSnapshot(
                transactions = listOf(tx(1, hash = "same"), tx(2, hash = "same"), tx(3)),
                rules = listOf(rule),
                ruleApplications = listOf(application("good", 1), application("onDuplicate", 2)),
                transactionSplits = listOf(
                    TransactionSplitEntity(transactionId = 1, category = "Food", amount = BigDecimal("4")),
                    TransactionSplitEntity(transactionId = 2, category = "Food", amount = BigDecimal("4"))
                )
            )
        )

        val result = importer.importBackup(android.net.Uri.fromFile(file(backup)), strategy)

        assertTrue("$result", result is ImportResult.Success)
        result as ImportResult.Success
        assertEquals(2, result.importedTransactions)
        assertEquals(2, db.transactionDao().getAllTransactions().first().size)
        assertEquals(listOf("good"), db.ruleApplicationDao().getAllApplications().first().map { it.id })
        assertEquals(1, db.transactionSplitDao().getAllSplits().first().size)
        assertTrue(db.transactionSplitDao().getAllSplits().first().all { it.transactionId > 0 })
        assertEquals(expectedSkippedRows, result.skippedRows + result.skippedDuplicates)
    }

    // MERGE counts the ignored row as a duplicate; REPLACE_ALL as a skipped row.
    @Test fun mergeSkipsDependantsOfAnIgnoredDuplicateHashRow() = runBlocking { assertDuplicateHashRowIsNotAParent(ImportStrategy.MERGE, 3) }

    @Test fun replaceAllSkipsDependantsOfAnIgnoredDuplicateHashRow() = runBlocking { assertDuplicateHashRowIsNotAParent(ImportStrategy.REPLACE_ALL, 3) }

    // Two tags named alike: the second insert is ignored (-1), so a link to it is skipped and counted.
    @Test fun replaceAllSkipsTagLinksOfAnIgnoredDuplicateTag() = runBlocking {
        val backup = PennyWiseBackup(
            database = DatabaseSnapshot(
                transactions = listOf(tx(1)),
                tags = listOf(TagEntity(id = 1, name = "work"), TagEntity(id = 2, name = "work")),
                transactionTagCrossRefs = listOf(TransactionTagCrossRef(1, 1), TransactionTagCrossRef(1, 2))
            )
        )

        val result = importer.importBackup(android.net.Uri.fromFile(file(backup)), ImportStrategy.REPLACE_ALL)

        assertTrue("$result", result is ImportResult.Success)
        assertEquals(1, db.tagDao().getAllTagsSync().size)
        assertEquals(listOf(TransactionTagCrossRef(1, 1)), db.tagDao().getAllCrossRefs())
        assertEquals(2, (result as ImportResult.Success).skippedRows)
    }
}
