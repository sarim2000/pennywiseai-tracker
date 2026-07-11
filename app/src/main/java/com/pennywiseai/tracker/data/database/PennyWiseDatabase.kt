package com.pennywiseai.tracker.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pennywiseai.tracker.data.database.converter.Converters
import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.dao.ProfileDao
import com.pennywiseai.tracker.data.database.dao.BankNotificationDao
import com.pennywiseai.tracker.data.database.dao.BudgetDao
import com.pennywiseai.tracker.data.database.dao.CardDao
import com.pennywiseai.shared.data.bootstrap.DefaultCategoryData
import com.pennywiseai.tracker.data.database.dao.CategoryDao
import com.pennywiseai.tracker.data.database.dao.ChatDao
import com.pennywiseai.tracker.data.database.dao.ExchangeRateDao
import com.pennywiseai.tracker.data.database.dao.LoanDao
import com.pennywiseai.tracker.data.database.dao.MerchantMappingDao
import com.pennywiseai.tracker.data.database.dao.MerchantAliasDao
import com.pennywiseai.tracker.data.database.dao.BudgetSnapshotDao
import com.pennywiseai.tracker.data.database.dao.TransactionGroupDao
import com.pennywiseai.tracker.data.database.dao.RuleApplicationDao
import com.pennywiseai.tracker.data.database.dao.RuleDao
import com.pennywiseai.tracker.data.database.dao.SubscriptionDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.dao.UnrecognizedSmsDao
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.BankNotificationEntity
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryMonthSnapshotEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetMonthSnapshotEntity
import com.pennywiseai.tracker.data.database.entity.CardEntity
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.ChatMessage
import com.pennywiseai.tracker.data.database.entity.ExchangeRateEntity
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.MerchantMappingEntity
import com.pennywiseai.tracker.data.database.entity.MerchantAliasEntity
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.data.database.entity.RuleApplicationEntity
import com.pennywiseai.tracker.data.database.entity.RuleEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity

/**
 * Current Room schema version for [PennyWiseDatabase]. Lives at top-level so
 * it is usable both in the `@Database(version = ...)` annotation below and in
 * non-Room code (e.g. [com.pennywiseai.tracker.data.backup.BackupExporter])
 * that needs to record the version it was exported against. Bump this in lock-
 * step with any schema change.
 */
const val SCHEMA_VERSION = 56

/**
 * The PennyWise Room database.
 * 
 * This database stores all financial transaction data locally on the device.
 * 
 * @property version Current database version. Increment this when making schema changes.
 * @property entities List of all entities (tables) in the database.
 * @property exportSchema Set to true in production to export schema for version control.
 * @property autoMigrations List of automatic migrations between versions.
 */
@Database(
    entities = [TransactionEntity::class, SubscriptionEntity::class, ChatMessage::class, MerchantMappingEntity::class, MerchantAliasEntity::class, CategoryEntity::class, AccountBalanceEntity::class, UnrecognizedSmsEntity::class, CardEntity::class, RuleEntity::class, RuleApplicationEntity::class, ExchangeRateEntity::class, BudgetEntity::class, BudgetCategoryEntity::class, BudgetMonthSnapshotEntity::class, BudgetCategoryMonthSnapshotEntity::class, TransactionSplitEntity::class, BankNotificationEntity::class, LoanEntity::class, TransactionGroupEntity::class, ProfileEntity::class],
    version = SCHEMA_VERSION,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5, spec = Migration4To5::class),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8, spec = Migration7To8::class),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11, spec = Migration10To11::class),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        // Note: v20 to v21 uses manual migration to handle nullable field change
        AutoMigration(from = 23, to = 24),
        AutoMigration(from = 24, to = 25),
        AutoMigration(from = 25, to = 26),
        AutoMigration(from = 26, to = 27),
        AutoMigration(from = 27, to = 28, spec = Migration27To28::class),
        AutoMigration(from = 28, to = 29),
        AutoMigration(from = 29, to = 30),
        AutoMigration(from = 30, to = 31),
        AutoMigration(from = 31, to = 32),
        AutoMigration(from = 32, to = 33),
        AutoMigration(from = 33, to = 34),
        AutoMigration(from = 34, to = 35, spec = Migration34To35::class),
        AutoMigration(from = 35, to = 36, spec = Migration35To36::class),
        AutoMigration(from = 36, to = 37),
        AutoMigration(from = 37, to = 38),
        // 38→39 is a manual migration (MIGRATION_38_39) registered in DatabaseModule — no AutoMigration entry needed
        AutoMigration(from = 39, to = 40),
        AutoMigration(from = 40, to = 41),
        AutoMigration(from = 41, to = 42),
        AutoMigration(from = 42, to = 43),
        AutoMigration(from = 43, to = 44, spec = Migration43To44::class),
        // 44→45, 45→46 and 46→47 are manual migrations registered in DatabaseModule
        // (profile_id columns and loan_contribution column).
        // 47→55 are manual migrations registered in DatabaseModule.
        // 55→56 adds the merchant_aliases table — a pure additive change, so
        // Room generates the CREATE TABLE automatically.
        AutoMigration(from = 55, to = 56)
    ]
)
@TypeConverters(Converters::class)
abstract class PennyWiseDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun chatDao(): ChatDao
    abstract fun merchantMappingDao(): MerchantMappingDao
    abstract fun merchantAliasDao(): MerchantAliasDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountBalanceDao(): AccountBalanceDao
    abstract fun unrecognizedSmsDao(): UnrecognizedSmsDao
    abstract fun cardDao(): CardDao
    abstract fun ruleDao(): RuleDao
    abstract fun ruleApplicationDao(): RuleApplicationDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun budgetDao(): BudgetDao
    abstract fun transactionSplitDao(): TransactionSplitDao
    abstract fun bankNotificationDao(): BankNotificationDao
    abstract fun loanDao(): LoanDao
    abstract fun transactionGroupDao(): TransactionGroupDao
    abstract fun budgetSnapshotDao(): BudgetSnapshotDao
    abstract fun profileDao(): ProfileDao

    companion object {
        const val DATABASE_NAME = "pennywise_database"

        @Volatile
        private var INSTANCE: PennyWiseDatabase? = null

        /**
         * Returns a singleton instance of the database.
         * This is used by components that don't have access to Hilt injection
         * (like BroadcastReceivers).
         */
        fun getInstance(context: android.content.Context): PennyWiseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    PennyWiseDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Sets the singleton instance. Called by Hilt module to ensure
         * the same instance is used throughout the app.
         */
        fun setInstance(database: PennyWiseDatabase) {
            INSTANCE = database
        }

        /**
         * Manual migration from version 1 to 2.
         * Example of how to write manual migrations when auto-migration isn't sufficient.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Example: Add a new column
                // db.execSQL("ALTER TABLE transactions ADD COLUMN tags TEXT")
            }
        }
        
        /**
         * Manual migration from version 13 to 14.
         * Adds is_deleted column and unique constraint, handling existing duplicates.
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Check if sms_sender column already exists in transactions table
                val cursor = db.query("PRAGMA table_info(transactions)")
                var hasSenderColumn = false
                while (cursor.moveToNext()) {
                    val nameIndex = cursor.getColumnIndex("name")
                    if (nameIndex == -1) continue
                    val columnName = cursor.getString(nameIndex)
                    if (columnName == "sms_sender") {
                        hasSenderColumn = true
                        break
                    }
                }
                cursor.close()
                
                // Add sms_sender column to transactions table only if it doesn't exist
                if (!hasSenderColumn) {
                    db.execSQL("ALTER TABLE transactions ADD COLUMN sms_sender TEXT")
                }
                
                // Check if is_deleted column already exists in unrecognized_sms table
                val cursor2 = db.query("PRAGMA table_info(unrecognized_sms)")
                var hasIsDeletedColumn = false
                while (cursor2.moveToNext()) {
                    val nameIndex2 = cursor2.getColumnIndex("name")
                    if (nameIndex2 == -1) continue
                    val columnName = cursor2.getString(nameIndex2)
                    if (columnName == "is_deleted") {
                        hasIsDeletedColumn = true
                        break
                    }
                }
                cursor2.close()
                
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
            override fun migrate(db: SupportSQLiteDatabase) {
                // Same as MIGRATION_13_14 since we need to handle both cases
                MIGRATION_13_14.migrate(db)
            }
        }
        
        /**
         * Manual migration from version 14 to 15.
         * Adds sms_body column to subscriptions table.
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
            override fun migrate(db: SupportSQLiteDatabase) {
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
            override fun migrate(db: SupportSQLiteDatabase) {
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
            override fun migrate(db: SupportSQLiteDatabase) {
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
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `account_balances` ADD COLUMN `profile_id` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_45_46 = object : Migration(45, 46) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `profile_id` INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_46_47 = object : Migration(46, 47) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
            override fun migrate(db: SupportSQLiteDatabase) {
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
            override fun migrate(db: SupportSQLiteDatabase) {
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
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `account_balances` ADD COLUMN `alias` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_50_51 = object : Migration(50, 51) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Per-transaction "exclude from analytics" flag (#451).
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `excluded_from_analytics` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_51_52 = object : Migration(51, 52) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
            override fun migrate(db: SupportSQLiteDatabase) {
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
            override fun migrate(db: SupportSQLiteDatabase) {
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
            override fun migrate(db: SupportSQLiteDatabase) {
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

        val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add receipt_path to transactions if missing
                if (!hasColumn(db, "transactions", "receipt_path")) {
                    db.execSQL("ALTER TABLE `transactions` ADD COLUMN `receipt_path` TEXT DEFAULT NULL")
                }
                // Add statement_day to account_balances if missing (added in main's v38)
                if (!hasColumn(db, "account_balances", "statement_day")) {
                    db.execSQL("ALTER TABLE `account_balances` ADD COLUMN `statement_day` INTEGER DEFAULT NULL")
                }
            }

            private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
                val cursor = db.query("PRAGMA table_info($table)")
                try {
                    while (cursor.moveToNext()) {
                        val nameIndex = cursor.getColumnIndex("name")
                        if (nameIndex != -1 && cursor.getString(nameIndex) == column) {
                            return true
                        }
                    }
                } finally {
                    cursor.close()
                }
                return false
            }
        }

        /**
         * Single source of truth for the migration list. Both the Hilt-built
         * database (DatabaseModule.providePennyWiseDatabase) and the
         * BroadcastReceiver fallback path (getInstance above) reference this
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
    //     override fun onPostMigrate(db: SupportSQLiteDatabase) {
    //         // Perform additional operations after migration if needed
    //         // Example: Update default values, create indexes, etc.
    //     }
    // }
}

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
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
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
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
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
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
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
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        super.onPostMigrate(db)
        db.execSQL("INSERT OR IGNORE INTO profiles (id, name, color_hex, sort_order) VALUES (1, 'Personal', '#4CAF50', 0)")
        db.execSQL("INSERT OR IGNORE INTO profiles (id, name, color_hex, sort_order) VALUES (2, 'Business', '#2196F3', 1)")
    }
}
