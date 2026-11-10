package com.pennywiseai.shared.data.local

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The v2 → v3 upgrade on a real SQLite: Room's own v2 DDL, synthetic rows. */
class SharedDatabaseMigrationTest {
    private val connection = BundledSQLiteDriver().open(":memory:")

    @AfterTest
    fun close() = connection.close()

    @Test
    fun threeDecimalRowsAreRescaledAndEverythingElseIsUntouched() = runTest {
        V2_TABLES.forEach { connection.execSQL(it) }
        val tooBig = 1_000_000_000_000_000_000L // past Long.MAX_VALUE / 10: x 10 would wrap
        connection.execSQL(
            "INSERT INTO shared_transactions (id, amount_minor, merchant_name, category, transaction_type, " +
                "occurred_at_epoch_millis, currency, balance_after_minor, created_at_epoch_millis, updated_at_epoch_millis) VALUES " +
                "(1, 124, 'Test Cafe', 'Food', 'EXPENSE', 0, 'JOD', 10013, 0, 0), " +
                "(2, 45000, 'Test Shop', 'Food', 'EXPENSE', 0, 'INR', NULL, 0, 0), " +
                "(3, 500, 'Test Shop', 'Food', 'EXPENSE', 0, 'kwd', NULL, 0, 0), " +
                "(4, $tooBig, 'Test Shop', 'Food', 'EXPENSE', 0, 'JOD', 7, 0, 0), " +
                "(5, -$tooBig, 'Test Shop', 'Food', 'EXPENSE', 0, 'Jod', NULL, 0, 0)"
        )
        connection.execSQL(
            "INSERT INTO shared_transaction_splits (id, transaction_id, category, amount_minor, created_at_epoch_millis) " +
                "VALUES (1, 1, 'Food', 100, 0), (2, 2, 'Food', 200, 0), (3, 4, 'Food', $tooBig, 0)"
        )
        connection.execSQL(
            "INSERT INTO shared_budgets (id, name, limit_minor, period_type, start_epoch_millis, end_epoch_millis, is_active, " +
                "group_type, currency, created_at_epoch_millis, updated_at_epoch_millis) VALUES " +
                "(1, 'Budget A', 10000, 'MONTHLY', 0, 1, 1, 'LIMIT', 'OMR', 0, 0), " +
                "(2, 'Budget B', 10000, 'MONTHLY', 0, 1, 1, 'LIMIT', 'USD', 0, 0)"
        )
        connection.execSQL(
            "INSERT INTO shared_budget_categories (id, budget_id, category_name, budget_amount_minor) " +
                "VALUES (1, 1, 'Food', 5000), (2, 2, 'Food', 5000)"
        )
        connection.execSQL(
            "INSERT INTO shared_account_balances (id, bank_name, account_last4, timestamp_epoch_millis, balance_minor, " +
                "is_credit_card, currency, created_at_epoch_millis) VALUES " +
                "(1, 'Test Bank', '0001', 0, 10013, 0, 'BHD', 0), (2, 'Test Bank', '0002', 0, 10013, 0, 'AED', 0)"
        )
        connection.execSQL(
            "INSERT INTO shared_cards (id, card_last4, card_type, bank_name, is_active, last_balance_minor, currency, " +
                "created_at_epoch_millis, updated_at_epoch_millis) VALUES " +
                "(1, '0001', 'DEBIT', 'Test Bank', 1, NULL, 'JOD', 0, 0), (2, '0002', 'CREDIT', 'Test Bank', 1, 700, 'TND', 0, 0)"
        )
        connection.execSQL(
            "INSERT INTO shared_subscriptions (id, merchant_name, amount_minor, state, currency, created_at_epoch_millis, " +
                "updated_at_epoch_millis) VALUES (1, 'Test Stream', 999, 'ACTIVE', 'LYD', 0, 0), (2, 'Test Gym', 999, 'ACTIVE', 'IQD', 0, 0)"
        )

        SharedDatabase.MIGRATION_2_3.migrate(connection)

        assertEquals(listOf(1240L, 45000L, 5000L, tooBig, -tooBig), column("shared_transactions", "amount_minor"))
        assertEquals(listOf(100130L, null, null, 70L, null), column("shared_transactions", "balance_after_minor"))
        assertEquals(listOf(1000L, 200L, tooBig), column("shared_transaction_splits", "amount_minor"))
        assertEquals(listOf(100000L, 10000L), column("shared_budgets", "limit_minor"))
        assertEquals(listOf(50000L, 5000L), column("shared_budget_categories", "budget_amount_minor"))
        assertEquals(listOf(100130L, 10013L), column("shared_account_balances", "balance_minor"))
        assertEquals(listOf(null, 7000L), column("shared_cards", "last_balance_minor"))
        assertEquals(listOf(9990L, 999L), column("shared_subscriptions", "amount_minor"))
    }

    private fun column(table: String, column: String): List<Long?> {
        val statement = connection.prepare("SELECT $column FROM $table ORDER BY id")
        try {
            val values = mutableListOf<Long?>()
            while (statement.step()) values += if (statement.isNull(0)) null else statement.getLong(0)
            return values
        } finally {
            statement.close()
        }
    }

    private companion object {
        /** SharedDatabase v2's tables that hold money, as Room's generated code creates them. */
        val V2_TABLES = listOf(
            "CREATE TABLE IF NOT EXISTS `shared_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount_minor` INTEGER NOT NULL, `merchant_name` TEXT NOT NULL, `category` TEXT NOT NULL, `transaction_type` TEXT NOT NULL, `occurred_at_epoch_millis` INTEGER NOT NULL, `note` TEXT, `currency` TEXT NOT NULL DEFAULT 'INR', `transaction_hash` TEXT, `reference` TEXT, `bank_name` TEXT, `account_last4` TEXT, `balance_after_minor` INTEGER, `is_deleted` INTEGER NOT NULL DEFAULT 0, `created_at_epoch_millis` INTEGER NOT NULL, `updated_at_epoch_millis` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `shared_transaction_splits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transaction_id` INTEGER NOT NULL, `category` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `created_at_epoch_millis` INTEGER NOT NULL, FOREIGN KEY(`transaction_id`) REFERENCES `shared_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `shared_budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `limit_minor` INTEGER NOT NULL, `period_type` TEXT NOT NULL, `start_epoch_millis` INTEGER NOT NULL, `end_epoch_millis` INTEGER NOT NULL, `is_active` INTEGER NOT NULL, `group_type` TEXT NOT NULL, `currency` TEXT NOT NULL, `created_at_epoch_millis` INTEGER NOT NULL, `updated_at_epoch_millis` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `shared_budget_categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `budget_id` INTEGER NOT NULL, `category_name` TEXT NOT NULL, `budget_amount_minor` INTEGER NOT NULL, FOREIGN KEY(`budget_id`) REFERENCES `shared_budgets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `shared_account_balances` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bank_name` TEXT NOT NULL, `account_last4` TEXT NOT NULL, `timestamp_epoch_millis` INTEGER NOT NULL, `balance_minor` INTEGER NOT NULL, `transaction_id` INTEGER, `is_credit_card` INTEGER NOT NULL, `account_type` TEXT, `currency` TEXT NOT NULL, `created_at_epoch_millis` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `shared_cards` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `card_last4` TEXT NOT NULL, `card_type` TEXT NOT NULL, `bank_name` TEXT NOT NULL, `account_last4` TEXT, `is_active` INTEGER NOT NULL, `last_balance_minor` INTEGER, `currency` TEXT NOT NULL, `created_at_epoch_millis` INTEGER NOT NULL, `updated_at_epoch_millis` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `shared_subscriptions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `merchant_name` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `next_payment_epoch_millis` INTEGER, `state` TEXT NOT NULL, `bank_name` TEXT, `umn` TEXT, `category` TEXT, `currency` TEXT NOT NULL, `created_at_epoch_millis` INTEGER NOT NULL, `updated_at_epoch_millis` INTEGER NOT NULL)"
        )
    }
}
