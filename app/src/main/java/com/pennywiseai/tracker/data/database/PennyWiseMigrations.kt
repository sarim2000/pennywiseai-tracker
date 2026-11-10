@file:Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") // migrations keep Room 2's `db` name so their bodies stay unchanged

package com.pennywiseai.tracker.data.database

import androidx.room3.DeleteColumn
import androidx.room3.DeleteTable
import androidx.room3.migration.AutoMigrationSpec
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.pennywiseai.shared.data.bootstrap.DefaultCategoryData
import com.pennywiseai.tracker.data.database.converter.Converters
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.utils.findUnlinkedTransferPairs
import java.math.BigDecimal
import java.util.UUID

/**
 * Upgrade code for [PennyWiseDatabase]: the manual migrations registered in
 * [ALL_MIGRATIONS] and the specs its `@Database` auto-migrations name. The
 * schema itself (entities, `SCHEMA_VERSION`, the `autoMigrations` list) stays
 * on the database class.
 */
object PennyWiseMigrations {
    /**
     * Manual migration from version 1 to 2.
     * Example of how to write manual migrations when auto-migration isn't sufficient.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override suspend fun migrate(db: SQLiteConnection) {
            // Example: Add a new column
            // db.execSQL("ALTER TABLE transactions ADD COLUMN tags TEXT")
        }
    }
    
    /**
     * Manual migration from version 13 to 14.
     * Adds is_deleted column and unique constraint, handling existing duplicates.
     */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override suspend fun migrate(db: SQLiteConnection) {
            // Check if sms_sender column already exists in transactions table
            val hasSenderColumn = db.hasColumn("transactions", "sms_sender")
            
            // Add sms_sender column to transactions table only if it doesn't exist
            if (!hasSenderColumn) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN sms_sender TEXT")
            }
            
            // Check if is_deleted column already exists in unrecognized_sms table
            val hasIsDeletedColumn = db.hasColumn("unrecognized_sms", "is_deleted")
            
            // Only proceed with unrecognized_sms migration if needed
            if (!hasIsDeletedColumn) {
                // First, add the is_deleted column with default value
                db.execSQL("ALTER TABLE unrecognized_sms ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")
                
                // Create a temporary table with the new schema (including unique constraint)
                db.execSQL("""
                        CREATE TABLE unrecognized_sms_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            sender TEXT NOT NULL,
                            sms_body TEXT NOT NULL,
                            received_at TEXT NOT NULL,
                            reported INTEGER NOT NULL,
                            is_deleted INTEGER NOT NULL DEFAULT 0,
                            created_at TEXT NOT NULL
                        )
                    """)
                
                // Copy data from old table, keeping only the most recent of duplicates
                db.execSQL("""
                        INSERT INTO unrecognized_sms_new (id, sender, sms_body, received_at, reported, is_deleted, created_at)
                        SELECT id, sender, sms_body, received_at, reported, is_deleted, created_at
                        FROM unrecognized_sms
                        WHERE id IN (
                            SELECT MAX(id)
                            FROM unrecognized_sms
                            GROUP BY sender, sms_body
                        )
                    """)
                
                // Drop the old table
                db.execSQL("DROP TABLE unrecognized_sms")
                
                // Rename the new table to the original name
                db.execSQL("ALTER TABLE unrecognized_sms_new RENAME TO unrecognized_sms")
                
                // Create the unique index
                db.execSQL("CREATE UNIQUE INDEX index_unrecognized_sms_sender_sms_body ON unrecognized_sms (sender, sms_body)")
            }
        }
    }
    
    /**
     * Manual migration from version 12 to 14.
     * Handles direct upgrade from 12 to 14, combining migrations 12->13 and 13->14.
     */
    val MIGRATION_12_14 = object : Migration(12, 14) {
        override suspend fun migrate(db: SQLiteConnection) {
            // Same as MIGRATION_13_14 since we need to handle both cases
            MIGRATION_13_14.migrate(db)
        }
    }
    
    /**
     * Manual migration from version 14 to 15.
     * Adds sms_body column to subscriptions table.
     */
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override suspend fun migrate(db: SQLiteConnection) {
            // Add sms_body column to subscriptions table
            db.execSQL("ALTER TABLE subscriptions ADD COLUMN sms_body TEXT")
        }
    }
    
    /**
     * Manual migration from version 20 to 21.
     * Makes next_payment_date nullable in subscriptions table.
     * This fixes the issue where v2.15.18 had non-nullable field but v2.15.19+ needs nullable.
     */
    val MIGRATION_20_21 = object : Migration(20, 21) {
        override suspend fun migrate(db: SQLiteConnection) {
            // SQLite doesn't support ALTER COLUMN, so we need to recreate the table
            // Step 1: Create new subscriptions table with nullable next_payment_date
            db.execSQL("""
                    CREATE TABLE subscriptions_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        merchant_name TEXT NOT NULL,
                        amount TEXT NOT NULL,
                        next_payment_date TEXT,
                        state TEXT NOT NULL,
                        bank_name TEXT,
                        umn TEXT,
                        category TEXT,
                        sms_body TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                """)
            
            // Step 2: Copy data from old table to new table
            db.execSQL("""
                    INSERT INTO subscriptions_new (id, merchant_name, amount, next_payment_date, state, bank_name, umn, category, sms_body, created_at, updated_at)
                    SELECT id, merchant_name, amount, next_payment_date, state, bank_name, umn, category, sms_body, created_at, updated_at
                    FROM subscriptions
                """)
            
            // Step 3: Drop old table
            db.execSQL("DROP TABLE subscriptions")
            
            // Step 4: Rename new table to original name
            db.execSQL("ALTER TABLE subscriptions_new RENAME TO subscriptions")
        }
    }

    /**
     * Manual migration from version 21 to 22.
     * Adds transaction_rules and rule_applications tables for the rule engine.
     * Note: This migration is kept for users who might be on v21.
     */
    val MIGRATION_21_22 = object : Migration(21, 22) {
        override suspend fun migrate(db: SQLiteConnection) {
            // Create transaction_rules table
            db.execSQL("""
                    CREATE TABLE IF NOT EXISTS transaction_rules (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT,
                        priority INTEGER NOT NULL,
                        conditions TEXT NOT NULL,
                        actions TEXT NOT NULL,
                        is_active INTEGER NOT NULL,
                        is_system_template INTEGER NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                """)

            // Create indices for transaction_rules
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_rules_priority_is_active ON transaction_rules (priority, is_active)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_rules_name ON transaction_rules (name)")

            // Create rule_applications table
            db.execSQL("""
                    CREATE TABLE rule_applications (
                        id TEXT PRIMARY KEY NOT NULL,
                        rule_id TEXT NOT NULL,
                        rule_name TEXT NOT NULL,
                        transaction_id TEXT NOT NULL,
                        fields_modified TEXT NOT NULL,
                        applied_at TEXT NOT NULL,
                        FOREIGN KEY(rule_id) REFERENCES transaction_rules(id) ON DELETE CASCADE,
                        FOREIGN KEY(transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
                    )
                """)

            // Create indices for rule_applications
            db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_applications_rule_id ON rule_applications (rule_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_applications_transaction_id ON rule_applications (transaction_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_applications_applied_at ON rule_applications (applied_at)")
        }
    }

    /**
     * Manual migration from version 22 to 23.
     * Adds transaction_rules and rule_applications tables for the rule engine.
     * This is for users who were already on v22 before the rules feature was added.
     */
    val MIGRATION_22_23 = object : Migration(22, 23) {
        override suspend fun migrate(db: SQLiteConnection) {
            // Drop table if exists to ensure clean state
            db.execSQL("DROP TABLE IF EXISTS transaction_rules")
            db.execSQL("DROP TABLE IF EXISTS rule_applications")

            // Create transaction_rules table with all required columns
            db.execSQL("""
                    CREATE TABLE transaction_rules (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT,
                        priority INTEGER NOT NULL,
                        conditions TEXT NOT NULL,
                        actions TEXT NOT NULL,
                        is_active INTEGER NOT NULL,
                        is_system_template INTEGER NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                """)

            // Create indices for transaction_rules
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_rules_priority_is_active ON transaction_rules (priority, is_active)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_rules_name ON transaction_rules (name)")

            // Create rule_applications table
            db.execSQL("""
                    CREATE TABLE rule_applications (
                        id TEXT PRIMARY KEY NOT NULL,
                        rule_id TEXT NOT NULL,
                        rule_name TEXT NOT NULL,
                        transaction_id TEXT NOT NULL,
                        fields_modified TEXT NOT NULL,
                        applied_at TEXT NOT NULL,
                        FOREIGN KEY(rule_id) REFERENCES transaction_rules(id) ON DELETE CASCADE,
                        FOREIGN KEY(transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
                    )
                """)

            // Create indices for rule_applications
            db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_applications_rule_id ON rule_applications (rule_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_applications_transaction_id ON rule_applications (transaction_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_applications_applied_at ON rule_applications (applied_at)")
        }
    }

    val MIGRATION_44_45 = object : Migration(44, 45) {
        override suspend fun migrate(db: SQLiteConnection) {
            db.execSQL("ALTER TABLE `account_balances` ADD COLUMN `profile_id` INTEGER NOT NULL DEFAULT 1")
        }
    }

    val MIGRATION_45_46 = object : Migration(45, 46) {
        override suspend fun migrate(db: SQLiteConnection) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `profile_id` INTEGER DEFAULT NULL")
        }
    }

    val MIGRATION_46_47 = object : Migration(46, 47) {
        override suspend fun migrate(db: SQLiteConnection) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `loan_contribution` TEXT DEFAULT NULL")
        }
    }

    /**
     * Income autopay + cycle persistence (#371). Adds two columns to
     * `subscriptions`:
     *
     *  - `direction` — EXPENSE (today's behaviour: Netflix, EMIs) or
     *    INCOME (wallet top-ups, allowance) for the new income-autopay
     *    flow that phantom-creates a transaction when `next_payment_date`
     *    rolls past today.
     *  - `billing_cycle` — previously collected by the Add-Subscription
     *    UI but silently dropped by the use case, so the matcher's
     *    hard-coded +30-day advance ran for every cycle. Persist it now
     *    so cycle-aware advance + phantom creation can honour the
     *    user-chosen cadence (Weekly / Monthly / Quarterly / Annual).
     *
     * Existing rows default to EXPENSE + Monthly so behaviour is unchanged.
     */
    val MIGRATION_47_48 = object : Migration(47, 48) {
        override suspend fun migrate(db: SQLiteConnection) {
            db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `direction` TEXT NOT NULL DEFAULT 'EXPENSE'")
            db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `billing_cycle` TEXT NOT NULL DEFAULT 'Monthly'")
        }
    }

    /**
     * Adds `last_paid_at` to subscriptions so the UI can show a "Paid"
     * badge after the user marks a cycle paid, and the use case can
     * refuse a same-cycle re-tap (#412 follow-up). Nullable — existing
     * rows have no payment history yet.
     */
    val MIGRATION_48_49 = object : Migration(48, 49) {
        override suspend fun migrate(db: SQLiteConnection) {
            db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `last_paid_at` TEXT DEFAULT NULL")
        }
    }

    /**
     * Adds a user-set friendly display name (`alias`) to account_balances
     * so an account can show as "My Salary Account" instead of the raw
     * bank/last-4. Nullable — existing rows fall back to the bank/last-4
     * format until the user sets one.
     */
    val MIGRATION_49_50 = object : Migration(49, 50) {
        override suspend fun migrate(db: SQLiteConnection) {
            db.execSQL("ALTER TABLE `account_balances` ADD COLUMN `alias` TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_50_51 = object : Migration(50, 51) {
        override suspend fun migrate(db: SQLiteConnection) {
            // Per-transaction "exclude from analytics" flag (#451).
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `excluded_from_analytics` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_51_52 = object : Migration(51, 52) {
        override suspend fun migrate(db: SQLiteConnection) {
            // Type-aware budgets: a budget bucket can now track a transaction
            // TYPE (match_type set) instead of a category name.
            db.execSQL("ALTER TABLE `budget_categories` ADD COLUMN `match_type` TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE `budget_category_month_snapshots` ADD COLUMN `match_type` TEXT DEFAULT NULL")
            // Auto-upgrade existing "Investments" category buckets to track the
            // INVESTMENT type directly, so investments count regardless of how
            // each transaction was categorised (and stop leaking into Spending).
            db.execSQL("UPDATE `budget_categories` SET `match_type` = 'INVESTMENT' WHERE `category_name` = 'Investments'")
            db.execSQL("UPDATE `budget_category_month_snapshots` SET `match_type` = 'INVESTMENT' WHERE `category_name` = 'Investments'")
        }
    }

    /**
     * Per-account low-balance alert threshold (#509). Nullable — existing
     * rows have no alert set (null = off). Stored as TEXT to match how
     * BigDecimal columns (balance, credit_limit) are persisted in this DB.
     */
    val MIGRATION_52_53 = object : Migration(52, 53) {
        override suspend fun migrate(db: SQLiteConnection) {
            db.execSQL("ALTER TABLE `account_balances` ADD COLUMN `lowBalanceThreshold` TEXT DEFAULT NULL")
        }
    }

    /**
     * Per-budget cadence anchors for the recurring / one-time split on
     * the Budget Period card (Weekly / Monthly / One-time). Both columns
     * are nullable so pre-existing rows keep working — the read-time
     * resolver falls back to Monday (Weekly) or the global startDay
     * preference (Monthly) when the anchor is null.
     *
     * No backfill: every existing row was created before this feature
     * and is treated as Monthly anchored to the user's global startDay
     * until the user opens the budget and explicitly picks a cadence.
     */
    val MIGRATION_53_54 = object : Migration(53, 54) {
        override suspend fun migrate(db: SQLiteConnection) {
            db.execSQL("ALTER TABLE `budgets` ADD COLUMN `weekday_anchor` INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE `budgets` ADD COLUMN `month_anchor` INTEGER DEFAULT NULL")
        }
    }

    /**
     * Drop the per-month budget snapshot tables. They stored a single
     * reconstructed row per (year, month, budget) and assumed every
     * budget was a monthly bucket — which made Weekly and One-time
     * budgets invisible or mis-rendered on historical views. With the
     * new cadence model the budget list is computed live per-window
     * (see [com.pennywiseai.tracker.data.repository.windowsForMonth]),
     * so the snapshot is no longer the source of truth for historical
     * views.
     *
     * Destructive: existing per-month snapshots are gone after this
     * migration runs. The live query reproduces the same data for any
     * (year, month) the user navigates to, plus per-week sub-buckets
     * for Weekly budgets that the snapshot couldn't represent.
     */
    val MIGRATION_54_55 = object : Migration(54, 55) {
        override suspend fun migrate(db: SQLiteConnection) {
            // Clear — don't drop — the per-month snapshot tables. The
            // tables remain in the schema because the backup format
            // round-trips them as JSON; dropping them would cascade a
            // larger refactor through BackupExporter/BackupImporter.
            // The live per-window query in windowsForMonth is the new
            // source of truth for historical views, so we just empty
            // the tables.
            db.execSQL("DELETE FROM `budget_category_month_snapshots`")
            db.execSQL("DELETE FROM `budget_month_snapshots`")
        }
    }

    val MIGRATION_57_58 = object : Migration(57, 58) {
        override suspend fun migrate(db: SQLiteConnection) {
            // Free-text tags with a many-to-many link to transactions.
            // SQL mirrors Room's generated schema (schemas/.../58.json) so
            // the migrated DB matches the compiled identity hash.
            db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `created_at` TEXT NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")

            db.execSQL("CREATE TABLE IF NOT EXISTS `transaction_tag_cross_ref` (`transaction_id` INTEGER NOT NULL, `tag_id` INTEGER NOT NULL, PRIMARY KEY(`transaction_id`, `tag_id`), FOREIGN KEY(`transaction_id`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_tag_cross_ref_transaction_id` ON `transaction_tag_cross_ref` (`transaction_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_tag_cross_ref_tag_id` ON `transaction_tag_cross_ref` (`tag_id`)")
        }
    }

    /**
     * Adds the `recurring_transactions` table for user-defined recurring /
     * scheduled manual (cash) transaction templates (#706). Purely additive
     * — a new standalone table — but written as a manual migration so the
     * exact column types / defaults match Room's generated identity for v59
     * (schemas/.../59.json). Enums, dates and BigDecimal are TEXT; booleans
     * and ints are INTEGER; nullable funding-account / day columns omit NOT
     * NULL.
     */
    val MIGRATION_58_59 = object : Migration(58, 59) {
        override suspend fun migrate(db: SQLiteConnection) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recurring_transactions` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`uid` TEXT NOT NULL DEFAULT '', " +
                    "`merchant_name` TEXT NOT NULL, " +
                    "`amount` TEXT NOT NULL, " +
                    "`currency` TEXT NOT NULL DEFAULT 'INR', " +
                    "`category` TEXT NOT NULL DEFAULT 'Others', " +
                    "`transaction_type` TEXT NOT NULL DEFAULT 'EXPENSE', " +
                    "`frequency` TEXT NOT NULL DEFAULT 'MONTHLY', " +
                    "`day_of_month` INTEGER, " +
                    "`day_of_week` INTEGER, " +
                    "`next_due_date` TEXT NOT NULL, " +
                    "`is_active` INTEGER NOT NULL DEFAULT 1, " +
                    "`account_last4` TEXT, " +
                    "`bank_name` TEXT, " +
                    "`note` TEXT, " +
                    "`profile_id` INTEGER NOT NULL DEFAULT 1, " +
                    "`created_at` TEXT NOT NULL, " +
                    "`updated_at` TEXT NOT NULL)"
            )
        }
    }

    val MIGRATION_38_39 = object : Migration(38, 39) {
        override suspend fun migrate(db: SQLiteConnection) {
            // Add receipt_path to transactions if missing
            if (!db.hasColumn("transactions", "receipt_path")) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `receipt_path` TEXT DEFAULT NULL")
            }
            // Add statement_day to account_balances if missing (added in main's v38)
            if (!db.hasColumn("account_balances", "statement_day")) {
                db.execSQL("ALTER TABLE `account_balances` ADD COLUMN `statement_day` INTEGER DEFAULT NULL")
            }
        }
    }

    // #736 — hide unused (esp. default) categories from the pickers.
    val MIGRATION_59_60 = object : Migration(59, 60) {
        override suspend fun migrate(db: SQLiteConnection) {
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `is_hidden` INTEGER NOT NULL DEFAULT 0")
        }
    }

    // #760: optional emoji per category.
    val MIGRATION_60_61 = object : Migration(60, 61) {
        override suspend fun migrate(db: SQLiteConnection) {
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `icon` TEXT")
        }
    }

    // #374: sub-categories — nullable parent pointer.
    val MIGRATION_61_62 = object : Migration(61, 62) {
        override suspend fun migrate(db: SQLiteConnection) {
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `parent_id` INTEGER")
        }
    }

    val MIGRATION_62_63 = object : Migration(62, 63) {
        override suspend fun migrate(db: SQLiteConnection) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `webhook_profiles` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, `enabled` INTEGER NOT NULL DEFAULT 1, `range_preset` TEXT NOT NULL, `custom_start` TEXT, `custom_end` TEXT, `data_types` TEXT NOT NULL DEFAULT 'SUMMARY,TRANSACTIONS,BUDGETS,ACCOUNTS,SUBSCRIPTIONS', `headers_json` TEXT NOT NULL DEFAULT '[]', `currency` TEXT NOT NULL DEFAULT 'INR', `last_error` TEXT, `consecutive_failures` INTEGER NOT NULL DEFAULT 0, `last_synced_at` TEXT, `created_at` TEXT NOT NULL, `updated_at` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `webhook_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profile_id` TEXT NOT NULL, `profile_name` TEXT NOT NULL, `sync_reason` TEXT NOT NULL, `status` TEXT NOT NULL, `message` TEXT NOT NULL, `http_status` INTEGER, `batch_count` INTEGER NOT NULL DEFAULT 0, `created_at` TEXT NOT NULL, FOREIGN KEY(`profile_id`) REFERENCES `webhook_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_webhook_logs_profile_id` ON `webhook_logs` (`profile_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_webhook_logs_created_at` ON `webhook_logs` (`created_at`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `webhook_cursors` (`profile_id` TEXT NOT NULL, `data_type` TEXT NOT NULL, `last_success_at` TEXT, `last_range_end` TEXT, `updated_at` TEXT NOT NULL, PRIMARY KEY(`profile_id`, `data_type`), FOREIGN KEY(`profile_id`) REFERENCES `webhook_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_webhook_cursors_profile_id` ON `webhook_cursors` (`profile_id`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `webhook_delivered_transactions` (`profile_id` TEXT NOT NULL, `transaction_id` INTEGER NOT NULL, PRIMARY KEY(`profile_id`, `transaction_id`), FOREIGN KEY(`profile_id`) REFERENCES `webhook_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_webhook_delivered_transactions_profile_id` ON `webhook_delivered_transactions` (`profile_id`)")
        }
    }

    val MIGRATION_63_64 = object : Migration(63, 64) {
        override suspend fun migrate(db: SQLiteConnection) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN from_bank_name TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE transactions ADD COLUMN to_bank_name TEXT DEFAULT NULL")
            // The owning row identifies a leg directly. For the other leg, only
            // backfill when one bank is evidenced by balances in the same currency.
            // Shared suffixes at different banks must remain unknown.
            // No HAVING without GROUP BY: SQLite only allows that from 3.39
            // (Android 14), and older phones crashed on every launch (2.22.0).
            for (leg in listOf("from", "to")) {
                db.execSQL("""
                        UPDATE transactions SET ${leg}_bank_name = CASE
                            WHEN account_number = ${leg}_account AND from_account IS NOT to_account THEN bank_name
                            ELSE (SELECT CASE WHEN COUNT(DISTINCT bank_name) = 1 THEN MIN(bank_name) END
                                  FROM account_balances b
                                  WHERE b.account_last4 = transactions.${leg}_account
                                    AND b.currency = transactions.currency)
                        END
                        WHERE transaction_type = 'TRANSFER' AND ${leg}_account IS NOT NULL
                    """.trimIndent())
            }
        }
    }

    /** Built-in categories remember their original name so they can be renamed (#823). */
    val MIGRATION_64_65 = object : Migration(64, 65) {
        override suspend fun migrate(db: SQLiteConnection) {
            db.execSQL("ALTER TABLE categories ADD COLUMN system_name TEXT DEFAULT NULL")
            db.execSQL("UPDATE categories SET system_name = name WHERE is_system = 1")
        }
    }

    /**
     * Links the transfer pairs marked before this column existed, so they stop
     * counting twice. Pairing happens in Kotlin, so the only SQL here is the
     * plain SELECT and UPDATE that SQLite 3.18 runs. A row that can't be read is
     * left unlinked: it must not fail the update for the whole database.
     */
    val MIGRATION_67_68 = object : Migration(67, 68) {
        override suspend fun migrate(db: SQLiteConnection) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN transfer_pair_id TEXT DEFAULT NULL")
            val converters = Converters()
            val rows = db.prepare("""
                    SELECT id, amount, currency, date_time, updated_at, bank_name, account_number,
                           from_account, to_account, from_bank_name, to_bank_name, is_deleted
                    FROM transactions
                    WHERE transaction_type = 'TRANSFER' AND (from_account IS NOT NULL OR to_account IS NOT NULL)
                """.trimIndent()).use { stmt ->
                fun text(column: Int) = if (stmt.isNull(column)) null else stmt.getText(column)
                buildList {
                    while (stmt.step()) {
                        val row = runCatching {
                            TransactionEntity(
                                id = stmt.getLong(0),
                                amount = BigDecimal(stmt.getText(1)),
                                merchantName = "",
                                category = "",
                                transactionType = TransactionType.TRANSFER,
                                currency = stmt.getText(2),
                                dateTime = converters.toLocalDateTime(stmt.getText(3))!!,
                                updatedAt = converters.toLocalDateTime(stmt.getText(4))!!,
                                transactionHash = "",
                                bankName = text(5),
                                accountNumber = text(6),
                                fromAccount = text(7),
                                toAccount = text(8),
                                fromBankName = text(9),
                                toBankName = text(10),
                                isDeleted = stmt.getLong(11) != 0L
                            )
                        }
                        row.getOrNull()?.let(::add)
                    }
                }
            }
            findUnlinkedTransferPairs(rows).forEach { (source, destination) ->
                db.execSQL(
                    "UPDATE transactions SET transfer_pair_id = ? WHERE id IN (?, ?)",
                    arrayOf(UUID.randomUUID().toString(), source, destination)
                )
            }
        }
    }

    /**
     * Single source of truth for the migration list. Both the Hilt-built
     * database (DatabaseModule.providePennyWiseDatabase) and the
     * BroadcastReceiver fallback path (PennyWiseDatabase.getInstance) reference this
     * — adding a new migration here registers it for both. Splitting
     * them previously caused a v47→v48 boot crash because the Hilt
     * builder didn't know about MIGRATION_47_48.
     */
    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_12_14,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_20_21,
        MIGRATION_21_22,
        MIGRATION_22_23,
        MIGRATION_38_39,
        MIGRATION_44_45,
        MIGRATION_45_46,
        MIGRATION_46_47,
        MIGRATION_47_48,
        MIGRATION_48_49,
        MIGRATION_49_50,
        MIGRATION_50_51,
        MIGRATION_51_52,
        MIGRATION_52_53,
        MIGRATION_53_54,
        MIGRATION_54_55,
        MIGRATION_57_58,
        MIGRATION_58_59,
        MIGRATION_59_60,
        MIGRATION_60_61,
        MIGRATION_61_62,
        MIGRATION_62_63,
        MIGRATION_63_64,
        MIGRATION_64_65,
        MIGRATION_67_68,
    )
}

/**
 * Example AutoMigrationSpec for renaming tables or columns.
 * Uncomment and modify when needed.
 */
// @RenameTable(fromTableName = "transactions", toTableName = "user_transactions")
// @RenameColumn(
//     tableName = "transactions",
//     fromColumnName = "merchant_name", 
//     toColumnName = "vendor_name"
// )
// class Migration1To2 : AutoMigrationSpec {
//     override suspend fun onPostMigrate(db: SQLiteConnection) {
//         // Perform additional operations after migration if needed
//         // Example: Update default values, create indexes, etc.
//     }
// }


/**
 * Migration from version 4 to 5.
 * - Removes sessionId column from chat_messages table
 * - Adds isSystemPrompt column to chat_messages table
 */
@DeleteColumn.Entries(
    DeleteColumn(
        tableName = "chat_messages",
        columnName = "sessionId"
    )
)
class Migration4To5 : AutoMigrationSpec

/**
 * Migration from version 7 to 8.
 * - Adds categories table with default categories
 */
class Migration7To8 : AutoMigrationSpec {
    override suspend fun onPostMigrate(db: SQLiteConnection) {
        super.onPostMigrate(db)
        
        // Insert default categories
        val categories = DefaultCategoryData.ALL

        categories.forEachIndexed { index, seed ->
            db.execSQL("""
                INSERT INTO categories (name, color, is_system, is_income, display_order, created_at, updated_at)
                VALUES (?, ?, 1, ?, ?, datetime('now'), datetime('now'))
            """.trimIndent(), arrayOf<Any>(seed.name, seed.colorHex, if (seed.isIncome) 1 else 0, index + 1))
        }
    }
}

/**
 * Migration from version 10 to 11.
 * - Adds account_balances table for tracking account balance history
 * - Migrates existing balance data from transactions table
 */
class Migration10To11 : AutoMigrationSpec {
    override suspend fun onPostMigrate(db: SQLiteConnection) {
        super.onPostMigrate(db)

        // Migrate existing balance data from transactions table
        db.execSQL("""
            INSERT INTO account_balances (bank_name, account_last4, balance, timestamp, transaction_id, created_at)
            SELECT
                bank_name,
                account_number,
                balance_after,
                date_time,
                id,
                created_at
            FROM transactions
            WHERE balance_after IS NOT NULL
                AND bank_name IS NOT NULL
                AND account_number IS NOT NULL
        """.trimIndent())
    }
}

/**
 * Migration from version 27 to 28.
 * - Adds account_type column to account_balances table
 * - Migrates existing accounts: isCreditCard=true → CREDIT, others → SAVINGS
 */
class Migration27To28 : AutoMigrationSpec {
    override suspend fun onPostMigrate(db: SQLiteConnection) {
        super.onPostMigrate(db)

        // Update existing credit card accounts
        db.execSQL("""
            UPDATE account_balances
            SET account_type = 'CREDIT'
            WHERE is_credit_card = 1
        """)

        // Update existing non-credit accounts to SAVINGS (default)
        db.execSQL("""
            UPDATE account_balances
            SET account_type = 'SAVINGS'
            WHERE is_credit_card = 0 AND account_type IS NULL
        """)
    }
}

class Migration34To35 : AutoMigrationSpec

@DeleteTable.Entries(
    DeleteTable(tableName = "category_budget_limits")
)
class Migration35To36 : AutoMigrationSpec

class Migration43To44 : AutoMigrationSpec {
    override suspend fun onPostMigrate(db: SQLiteConnection) {
        super.onPostMigrate(db)
        db.execSQL("INSERT OR IGNORE INTO profiles (id, name, color_hex, sort_order) VALUES (1, 'Personal', '#4CAF50', 0)")
        db.execSQL("INSERT OR IGNORE INTO profiles (id, name, color_hex, sort_order) VALUES (2, 'Business', '#2196F3', 1)")
    }
}
