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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

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
            .addMigrations(*PennyWiseDatabase.ALL_MIGRATIONS)
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

    private companion object { const val NAME = "full-upgrade-test" }
}
