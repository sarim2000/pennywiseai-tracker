package com.pennywiseai.tracker.data.database

import androidx.room3.Room
import androidx.room3.useReaderConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * An old install's rows survive every hop to [SCHEMA_VERSION] on the device's
 * own SQLite (run it on the Api26_Migration AVD: SQLite 3.18), opened the way
 * the app opens it.
 */
@RunWith(AndroidJUnit4::class)
class FullUpgradeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule val helper = pennyWiseMigrationHelper(instrumentation, NAME)

    @After fun cleanUp() { instrumentation.targetContext.deleteDatabase(NAME) }

    @Test fun upgradeFrom47KeepsRows() = runBlocking {
        helper.createDatabase(47).apply {
            execSQL("""INSERT INTO transactions (id, amount, merchant_name, category, transaction_type, date_time,
                transaction_hash, is_recurring, created_at, updated_at, bank_name, account_number) VALUES
                (1, '250.75', 'Synthetic store', 'Food', 'EXPENSE', '2025-01-01T10:00:00', 'synthetic-1', 0,
                '2025-01-01T10:00:00', '2025-01-01T10:00:00', 'Example Bank', '0000')""")
            execSQL("""INSERT INTO account_balances (id, bank_name, account_last4, balance, timestamp, created_at)
                VALUES (1, 'Example Bank', '0000', '1000.50', '2025-01-01T10:00:00', '2025-01-01T10:00:00')""")
            close()
        }

        val db = Room.databaseBuilder(instrumentation.targetContext, PennyWiseDatabase::class.java, NAME)
            .addMigrations(*PennyWiseMigrations.ALL_MIGRATIONS)
            .setDriver(AndroidSQLiteDriver())
            .build()
        try {
            val txn = db.transactionDao().getTransactionById(1)!!
            assertEquals(0, BigDecimal("250.75").compareTo(txn.amount))
            assertEquals("Synthetic store", txn.merchantName)
            val balance = db.accountBalanceDao().getLatestBalance("Example Bank", "0000")!!
            assertEquals(0, BigDecimal("1000.50").compareTo(balance.balance))
            val version = db.useReaderConnection { c -> c.usePrepared("PRAGMA user_version") { it.step(); it.getLong(0) } }
            assertEquals(SCHEMA_VERSION.toLong(), version)
        } finally { db.close() }
    }

    @Test fun upgradeFrom67LinksMarkedTransferPairs() = runBlocking {
        helper.createDatabase(67).apply {
            // Rows 1 and 2 are a marked pair; 3 is a standalone transfer on the same route;
            // 4 lost its other leg, 5, to a delete.
            for ((id, account, hour) in listOf(
                Triple(1, "1111", 10), Triple(2, "2222", 10), Triple(3, "1111", 15), Triple(4, "1111", 18), Triple(5, "2222", 18)
            )) {
                execSQL("""INSERT INTO transactions (id, amount, merchant_name, category, transaction_type, date_time,
                    transaction_hash, is_recurring, created_at, updated_at, bank_name, account_number,
                    from_account, to_account, from_bank_name, to_bank_name) VALUES
                    ($id, '100', 'Synthetic', 'Others', 'TRANSFER', '2026-01-01T$hour:00:00', 'synthetic-$id', 0,
                    '2026-01-01T$hour:00:00', '2026-01-01T$hour:00:00', 'Bank $account', '$account',
                    '1111', '2222', 'Bank 1111', 'Bank 2222')""")
            }
            execSQL("UPDATE transactions SET is_deleted = 1 WHERE id = 5")
            // 6 and 7 are a pair marked before from/to banks: MIGRATION_63_64 knew only each row's own.
            for ((id, account, banks) in listOf(
                Triple(6, "3333", "'Bank 3333', NULL"), Triple(7, "4444", "NULL, 'Bank 4444'")
            )) {
                execSQL("""INSERT INTO transactions (id, amount, merchant_name, category, transaction_type, date_time,
                    transaction_hash, is_recurring, created_at, updated_at, bank_name, account_number,
                    from_account, to_account, from_bank_name, to_bank_name) VALUES
                    ($id, '100', 'Synthetic', 'Others', 'TRANSFER', '2026-01-01T20:00:00', 'synthetic-$id', 0,
                    '2026-01-01T20:00:00', '2026-01-02T09:00:00', 'Bank $account', '$account',
                    '3333', '4444', $banks)""")
            }
            close()
        }

        val db = Room.databaseBuilder(instrumentation.targetContext, PennyWiseDatabase::class.java, NAME)
            .addMigrations(*PennyWiseMigrations.ALL_MIGRATIONS)
            .setDriver(AndroidSQLiteDriver())
            .build()
        try {
            val dao = db.transactionDao()
            val source = dao.getTransactionById(1)!!
            assertNotNull(source.transferPairId)
            assertEquals(source.transferPairId, dao.getTransactionById(2)!!.transferPairId)
            assertNull(dao.getTransactionById(3)!!.transferPairId)
            assertNull(dao.getTransactionById(4)!!.transferPairId)
            val legacySource = dao.getTransactionById(6)!!
            assertNotNull(legacySource.transferPairId)
            assertEquals(legacySource.transferPairId, dao.getTransactionById(7)!!.transferPairId)
            // Linking isn't an edit: webhook sync goes by updated_at.
            assertEquals(LocalDateTime.of(2026, 1, 1, 10, 0), source.updatedAt)
        } finally { db.close() }
    }

    private companion object { const val NAME = "full-upgrade-test" }
}
