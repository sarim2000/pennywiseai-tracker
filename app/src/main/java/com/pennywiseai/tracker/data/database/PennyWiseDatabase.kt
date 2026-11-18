package com.pennywiseai.tracker.data.database

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.ColumnTypeConverters
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.pennywiseai.tracker.data.database.converter.Converters
import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.dao.ProfileDao
import com.pennywiseai.tracker.data.database.dao.TagDao
import com.pennywiseai.tracker.data.database.dao.BankNotificationDao
import com.pennywiseai.tracker.data.database.dao.BudgetDao
import com.pennywiseai.tracker.data.database.dao.CardDao
import com.pennywiseai.tracker.data.database.dao.CategoryDao
import com.pennywiseai.tracker.data.database.dao.ChatDao
import com.pennywiseai.tracker.data.database.dao.ExchangeRateDao
import com.pennywiseai.tracker.data.database.dao.LoanDao
import com.pennywiseai.tracker.data.database.dao.MerchantMappingDao
import com.pennywiseai.tracker.data.database.dao.MerchantAliasDao
import com.pennywiseai.tracker.data.database.dao.BudgetSnapshotDao
import com.pennywiseai.tracker.data.database.dao.TransactionGroupDao
import com.pennywiseai.tracker.data.database.dao.RuleApplicationDao
import com.pennywiseai.tracker.data.database.dao.RecurringTransactionDao
import com.pennywiseai.tracker.data.database.dao.RuleDao
import com.pennywiseai.tracker.data.database.dao.SubscriptionDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.dao.UnrecognizedSmsDao
import com.pennywiseai.tracker.data.database.dao.GoalDao
import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.database.entity.LoanShareEntity
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TagEntity
import com.pennywiseai.tracker.data.database.entity.TransactionTagCrossRef
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
import com.pennywiseai.tracker.data.database.entity.RecurringTransactionEntity
import com.pennywiseai.tracker.data.database.entity.WebhookProfileEntity
import com.pennywiseai.tracker.data.database.entity.WebhookLogEntity
import com.pennywiseai.tracker.data.database.entity.WebhookDeliveredTransactionEntity
import com.pennywiseai.tracker.data.database.entity.WebhookCursorEntity
import com.pennywiseai.tracker.data.database.dao.WebhookProfileDao
import com.pennywiseai.tracker.data.database.dao.WebhookLogDao
import com.pennywiseai.tracker.data.database.dao.WebhookDeliveredTransactionDao
import com.pennywiseai.tracker.data.database.dao.WebhookCursorDao
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
const val SCHEMA_VERSION = 68

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
    entities = [TransactionEntity::class, SubscriptionEntity::class, ChatMessage::class, MerchantMappingEntity::class, MerchantAliasEntity::class, CategoryEntity::class, AccountBalanceEntity::class, UnrecognizedSmsEntity::class, CardEntity::class, RuleEntity::class, RuleApplicationEntity::class, ExchangeRateEntity::class, BudgetEntity::class, BudgetCategoryEntity::class, BudgetMonthSnapshotEntity::class, BudgetCategoryMonthSnapshotEntity::class, TransactionSplitEntity::class, BankNotificationEntity::class, LoanEntity::class, TransactionGroupEntity::class, ProfileEntity::class, TagEntity::class, TransactionTagCrossRef::class, RecurringTransactionEntity::class, WebhookProfileEntity::class, WebhookLogEntity::class, WebhookCursorEntity::class, WebhookDeliveredTransactionEntity::class, GoalEntity::class, GoalContributionEntity::class, LoanShareEntity::class],
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
        AutoMigration(from = 55, to = 56),
        // 56→57 adds a nullable account_last4 column to subscriptions — a pure
        // additive change, so Room generates the ALTER TABLE automatically (#570).
        AutoMigration(from = 56, to = 57),
        // 57→65 are manual migrations (ALL_MIGRATIONS). 65→66 adds the goals and
        // goal_contributions tables (#914) — purely additive.
        AutoMigration(from = 65, to = 66),
        // 66→67 adds the loan_shares table and transactions.lent_shares_total
        // for split bills (#346) — purely additive.
        AutoMigration(from = 66, to = 67)
        // 67→68 adds transactions.transfer_pair_id and links existing pairs: manual.
    ]
)
@ColumnTypeConverters(Converters::class)
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
    abstract fun tagDao(): TagDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun webhookProfileDao(): WebhookProfileDao
    abstract fun webhookLogDao(): WebhookLogDao
    abstract fun webhookDeliveredTransactionDao(): WebhookDeliveredTransactionDao
    abstract fun webhookCursorDao(): WebhookCursorDao
    abstract fun goalDao(): GoalDao

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
                val instance = androidx.room3.Room.databaseBuilder(
                    context.applicationContext,
                    PennyWiseDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*PennyWiseMigrations.ALL_MIGRATIONS)
                    // System SQLite, the engine Room 2 used on this file. Keeps
                    // migrations on the phone's own SQLite (docs/database-migrations.md)
                    // rather than switching existing users' databases to a bundled build.
                    .setDriver(AndroidSQLiteDriver())
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
    }
}
