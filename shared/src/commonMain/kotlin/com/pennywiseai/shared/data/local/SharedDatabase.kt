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
                val dinars = "('BHD','JOD','KWD','LYD','OMR','TND')"
                val inDinars = "UPPER(currency) IN $dinars"
                // Children first: they find their currency through the parent row.
                connection.execSQL(
                    "UPDATE shared_transaction_splits SET amount_minor = amount_minor * 10 " +
                        "WHERE transaction_id IN (SELECT id FROM shared_transactions WHERE $inDinars)"
                )
                connection.execSQL(
                    "UPDATE shared_budget_categories SET budget_amount_minor = budget_amount_minor * 10 " +
                        "WHERE budget_id IN (SELECT id FROM shared_budgets WHERE $inDinars)"
                )
                connection.execSQL(
                    "UPDATE shared_transactions SET amount_minor = amount_minor * 10, " +
                        "balance_after_minor = balance_after_minor * 10 WHERE $inDinars"
                )
                connection.execSQL("UPDATE shared_account_balances SET balance_minor = balance_minor * 10 WHERE $inDinars")
                connection.execSQL("UPDATE shared_cards SET last_balance_minor = last_balance_minor * 10 WHERE $inDinars")
                connection.execSQL("UPDATE shared_budgets SET limit_minor = limit_minor * 10 WHERE $inDinars")
                connection.execSQL("UPDATE shared_subscriptions SET amount_minor = amount_minor * 10 WHERE $inDinars")
                // shared_category_budget_limits has no currency column, so it can't be told apart; left as is.
            }
        }
    }
}

@Suppress("KotlinNoActualForExpect")
expect object SharedDatabaseConstructor : RoomDatabaseConstructor<SharedDatabase> {
    override fun initialize(): SharedDatabase
}
