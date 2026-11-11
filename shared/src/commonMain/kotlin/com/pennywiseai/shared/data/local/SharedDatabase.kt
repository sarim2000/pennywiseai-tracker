package com.pennywiseai.shared.data.local

import androidx.room3.Database
import androidx.room3.ConstructedBy
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.pennywiseai.shared.data.local.dao.SharedCategoryDao
import com.pennywiseai.shared.data.local.dao.SharedCategoryBudgetLimitDao
import com.pennywiseai.shared.data.local.dao.SharedAccountBalanceDao
import com.pennywiseai.shared.data.local.dao.SharedBudgetDao
import com.pennywiseai.shared.data.local.dao.SharedCardDao
import com.pennywiseai.shared.data.local.dao.SharedExchangeRateDao
import com.pennywiseai.shared.data.local.dao.SharedMerchantMappingDao
import com.pennywiseai.shared.data.local.dao.SharedRuleApplicationDao
import com.pennywiseai.shared.data.local.dao.SharedRuleDao
import com.pennywiseai.shared.data.local.dao.SharedSubscriptionDao
import com.pennywiseai.shared.data.local.dao.SharedTransactionDao
import com.pennywiseai.shared.data.local.dao.SharedTransactionSplitDao
import com.pennywiseai.shared.data.local.dao.SharedUnrecognizedSmsDao
import com.pennywiseai.shared.data.local.entity.SharedAccountBalanceEntity
import com.pennywiseai.shared.data.local.entity.SharedBudgetCategoryEntity
import com.pennywiseai.shared.data.local.entity.SharedBudgetEntity
import com.pennywiseai.shared.data.local.entity.SharedCardEntity
import com.pennywiseai.shared.data.local.entity.SharedCategoryEntity
import com.pennywiseai.shared.data.local.entity.SharedCategoryBudgetLimitEntity
import com.pennywiseai.shared.data.local.entity.SharedExchangeRateEntity
import com.pennywiseai.shared.data.local.entity.SharedMerchantMappingEntity
import com.pennywiseai.shared.data.local.entity.SharedRuleApplicationEntity
import com.pennywiseai.shared.data.local.entity.SharedRuleEntity
import com.pennywiseai.shared.data.local.entity.SharedSubscriptionEntity
import com.pennywiseai.shared.data.local.entity.SharedTransactionEntity
import com.pennywiseai.shared.data.local.entity.SharedTransactionSplitEntity
import com.pennywiseai.shared.data.local.entity.SharedUnrecognizedSmsEntity

@Database(
    entities = [
        SharedTransactionEntity::class,
        SharedCategoryEntity::class,
        SharedSubscriptionEntity::class,
        SharedAccountBalanceEntity::class,
        SharedCardEntity::class,
        SharedTransactionSplitEntity::class,
        SharedMerchantMappingEntity::class,
        SharedRuleEntity::class,
        SharedRuleApplicationEntity::class,
        SharedExchangeRateEntity::class,
        SharedBudgetEntity::class,
        SharedBudgetCategoryEntity::class,
        SharedCategoryBudgetLimitEntity::class,
        SharedUnrecognizedSmsEntity::class
    ],
    version = 3,
    exportSchema = false
)
@ConstructedBy(SharedDatabaseConstructor::class)
abstract class SharedDatabase : RoomDatabase() {
    abstract fun transactionDao(): SharedTransactionDao
    abstract fun categoryDao(): SharedCategoryDao
    abstract fun subscriptionDao(): SharedSubscriptionDao
    abstract fun accountBalanceDao(): SharedAccountBalanceDao
    abstract fun cardDao(): SharedCardDao
    abstract fun transactionSplitDao(): SharedTransactionSplitDao
    abstract fun merchantMappingDao(): SharedMerchantMappingDao
    abstract fun ruleDao(): SharedRuleDao
    abstract fun ruleApplicationDao(): SharedRuleApplicationDao
    abstract fun exchangeRateDao(): SharedExchangeRateDao
    abstract fun budgetDao(): SharedBudgetDao
    abstract fun categoryBudgetLimitDao(): SharedCategoryBudgetLimitDao
    abstract fun unrecognizedSmsDao(): SharedUnrecognizedSmsDao

    companion object {
        const val DATABASE_NAME: String = "pennywise_shared.db"

        /**
         * 3-decimal currencies move from value × 100 to value × 1000 (MinorUnits): rows already
         * stored were rounded to 2 places, so they are only rescaled (JOD 1.24 → 1.240), never
         * given digits they lost. The list is frozen at v3; a code added to
         * MinorUnits.THREE_DECIMAL_CURRENCIES later needs its own migration.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override suspend fun migrate(connection: SQLiteConnection) {
                val inDinars = "UPPER(currency) IN ('BHD','JOD','KWD','LYD','OMR','TND')"
                // ponytail: rows past ±Long.MAX_VALUE / 10 (over ~9.2e14 JOD, only a misparse makes
                // one) can't take × 10 without wrapping, so they stay as they are and read ÷ 10 after
                // the upgrade. Garbage either way; add recovery only if a real amount ever gets there.
                // BETWEEN, not ABS(): ABS(Long.MIN_VALUE) is an SQLite overflow error.
                val limit = Long.MAX_VALUE / 10
                fun scale(table: String, column: String, rows: String) = connection.execSQL(
                    "UPDATE $table SET $column = $column * 10 WHERE $column BETWEEN -$limit AND $limit AND $rows"
                )
                // Children find their currency through the parent row.
                scale("shared_transaction_splits", "amount_minor", "transaction_id IN (SELECT id FROM shared_transactions WHERE $inDinars)")
                scale("shared_budget_categories", "budget_amount_minor", "budget_id IN (SELECT id FROM shared_budgets WHERE $inDinars)")
                scale("shared_transactions", "amount_minor", inDinars)
                scale("shared_transactions", "balance_after_minor", inDinars)
                scale("shared_account_balances", "balance_minor", inDinars)
                scale("shared_cards", "last_balance_minor", inDinars)
                scale("shared_budgets", "limit_minor", inDinars)
                scale("shared_subscriptions", "amount_minor", inDinars)
                // shared_category_budget_limits has no currency column, so it can't be told apart; left as is.
            }
        }
    }
}

@Suppress("KotlinNoActualForExpect")
expect object SharedDatabaseConstructor : RoomDatabaseConstructor<SharedDatabase> {
    override fun initialize(): SharedDatabase
}
