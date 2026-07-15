package com.pennywiseai.tracker.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.dao.ProfileDao
import com.pennywiseai.tracker.data.database.dao.BankNotificationDao
import com.pennywiseai.tracker.data.database.dao.BudgetDao
import com.pennywiseai.tracker.data.database.dao.BudgetSnapshotDao
import com.pennywiseai.tracker.data.database.dao.CardDao
import com.pennywiseai.shared.data.bootstrap.DefaultCategoryData
import com.pennywiseai.tracker.data.database.dao.CategoryDao
import com.pennywiseai.tracker.data.database.dao.ChatDao
import com.pennywiseai.tracker.data.database.dao.ExchangeRateDao
import com.pennywiseai.tracker.data.database.dao.LoanDao
import com.pennywiseai.tracker.data.database.dao.TransactionGroupDao
import com.pennywiseai.tracker.data.database.dao.MerchantMappingDao
import com.pennywiseai.tracker.data.database.dao.MerchantAliasDao
import com.pennywiseai.tracker.data.database.dao.RuleApplicationDao
import com.pennywiseai.tracker.data.database.dao.RuleDao
import com.pennywiseai.tracker.data.database.dao.SubscriptionDao
import com.pennywiseai.tracker.data.database.dao.TagDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.dao.UnrecognizedSmsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Singleton

/**
 * Hilt module that provides database-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    /**
     * Provides the singleton instance of PennyWiseDatabase.
     * 
     * @param context Application context
     * @return Configured Room database instance
     */
    @Provides
    @Singleton
    fun providePennyWiseDatabase(
        @ApplicationContext context: Context
    ): PennyWiseDatabase {
        val database = Room.databaseBuilder(
            context,
            PennyWiseDatabase::class.java,
            PennyWiseDatabase.DATABASE_NAME
        )
            // Single source of truth lives in PennyWiseDatabase.ALL_MIGRATIONS
            // so the BroadcastReceiver fallback builder stays in sync.
            .addMigrations(*PennyWiseDatabase.ALL_MIGRATIONS)

            // Enable auto-migrations
            // Room will automatically detect schema changes between versions

            // Add callback to seed default data on first creation
            .addCallback(DatabaseCallback())

            .build()

        // Set the singleton instance so BroadcastReceivers can access it
        PennyWiseDatabase.setInstance(database)

        return database
    }
    
    /**
     * Provides the TransactionDao from the database.
     * 
     * @param database The PennyWiseDatabase instance
     * @return TransactionDao for accessing transaction data
     */
    @Provides
    @Singleton
    fun provideTransactionDao(database: PennyWiseDatabase): TransactionDao {
        return database.transactionDao()
    }
    
    /**
     * Provides the SubscriptionDao from the database.
     * 
     * @param database The PennyWiseDatabase instance
     * @return SubscriptionDao for accessing subscription data
     */
    @Provides
    @Singleton
    fun provideSubscriptionDao(database: PennyWiseDatabase): SubscriptionDao {
        return database.subscriptionDao()
    }
    
    /**
     * Provides the ChatDao from the database.
     * 
     * @param database The PennyWiseDatabase instance
     * @return ChatDao for accessing chat message data
     */
    @Provides
    @Singleton
    fun provideChatDao(database: PennyWiseDatabase): ChatDao {
        return database.chatDao()
    }
    
    /**
     * Provides the MerchantMappingDao from the database.
     * 
     * @param database The PennyWiseDatabase instance
     * @return MerchantMappingDao for accessing merchant mapping data
     */
    @Provides
    @Singleton
    fun provideMerchantMappingDao(database: PennyWiseDatabase): MerchantMappingDao {
        return database.merchantMappingDao()
    }

    /**
     * Provides the MerchantAliasDao from the database.
     *
     * @param database The PennyWiseDatabase instance
     * @return MerchantAliasDao for accessing merchant alias data
     */
    @Provides
    @Singleton
    fun provideMerchantAliasDao(database: PennyWiseDatabase): MerchantAliasDao {
        return database.merchantAliasDao()
    }

    /**
     * Provides the CategoryDao from the database.
     * 
     * @param database The PennyWiseDatabase instance
     * @return CategoryDao for accessing category data
     */
    @Provides
    @Singleton
    fun provideCategoryDao(database: PennyWiseDatabase): CategoryDao {
        return database.categoryDao()
    }
    
    /**
     * Provides the AccountBalanceDao from the database.
     * 
     * @param database The PennyWiseDatabase instance
     * @return AccountBalanceDao for accessing account balance data
     */
    @Provides
    @Singleton
    fun provideAccountBalanceDao(database: PennyWiseDatabase): AccountBalanceDao {
        return database.accountBalanceDao()
    }
    
    /**
     * Provides the UnrecognizedSmsDao from the database.
     * 
     * @param database The PennyWiseDatabase instance
     * @return UnrecognizedSmsDao for accessing unrecognized SMS data
     */
    @Provides
    @Singleton
    fun provideUnrecognizedSmsDao(database: PennyWiseDatabase): UnrecognizedSmsDao {
        return database.unrecognizedSmsDao()
    }
    
    /**
     * Provides the CardDao from the database.
     *
     * @param database The PennyWiseDatabase instance
     * @return CardDao for accessing card data
     */
    @Provides
    @Singleton
    fun provideCardDao(database: PennyWiseDatabase): CardDao {
        return database.cardDao()
    }

    /**
     * Provides the RuleDao from the database.
     *
     * @param database The PennyWiseDatabase instance
     * @return RuleDao for accessing rule data
     */
    @Provides
    @Singleton
    fun provideRuleDao(database: PennyWiseDatabase): RuleDao {
        return database.ruleDao()
    }

    /**
     * Provides the RuleApplicationDao from the database.
     *
     * @param database The PennyWiseDatabase instance
     * @return RuleApplicationDao for accessing rule application data
     */
    @Provides
    @Singleton
    fun provideRuleApplicationDao(database: PennyWiseDatabase): RuleApplicationDao {
        return database.ruleApplicationDao()
    }

    /**
     * Provides the ExchangeRateDao from the database.
     *
     * @param database The PennyWiseDatabase instance
     * @return ExchangeRateDao for accessing exchange rate data
     */
    @Provides
    @Singleton
    fun provideExchangeRateDao(database: PennyWiseDatabase): ExchangeRateDao {
        return database.exchangeRateDao()
    }

    /**
     * Provides the BudgetDao from the database.
     *
     * @param database The PennyWiseDatabase instance
     * @return BudgetDao for accessing budget data
     */
    @Provides
    @Singleton
    fun provideBudgetDao(database: PennyWiseDatabase): BudgetDao {
        return database.budgetDao()
    }

    /**
     * Provides the TransactionSplitDao from the database.
     *
     * @param database The PennyWiseDatabase instance
     * @return TransactionSplitDao for accessing transaction split data
     */
    @Provides
    @Singleton
    fun provideTransactionSplitDao(database: PennyWiseDatabase): TransactionSplitDao {
        return database.transactionSplitDao()
    }

    @Provides
    @Singleton
    fun provideBankNotificationDao(database: PennyWiseDatabase): BankNotificationDao {
        return database.bankNotificationDao()
    }

    @Provides
    @Singleton
    fun provideLoanDao(database: PennyWiseDatabase): LoanDao {
        return database.loanDao()
    }

    @Provides
    @Singleton
    fun provideTransactionGroupDao(database: PennyWiseDatabase): TransactionGroupDao {
        return database.transactionGroupDao()
    }

    @Provides
    @Singleton
    fun provideBudgetSnapshotDao(database: PennyWiseDatabase): BudgetSnapshotDao {
        return database.budgetSnapshotDao()
    }

    @Provides
    @Singleton
    fun provideProfileDao(database: PennyWiseDatabase): ProfileDao {
        return database.profileDao()
    }

    @Provides
    @Singleton
    fun provideTagDao(database: PennyWiseDatabase): TagDao {
        return database.tagDao()
    }
}

/**
 * Database callback to seed initial data when database is first created
 */
class DatabaseCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        
        // Seed default categories for new installations
        CoroutineScope(Dispatchers.IO).launch {
            seedCategories(db)
            seedProfiles(db)
        }
    }
    
    private fun seedCategories(db: SupportSQLiteDatabase) {
        val categories = DefaultCategoryData.ALL

        categories.forEachIndexed { index, seed ->
            db.execSQL("""
                INSERT OR IGNORE INTO categories (name, color, is_system, is_income, display_order, created_at, updated_at)
                VALUES (?, ?, 1, ?, ?, datetime('now'), datetime('now'))
            """.trimIndent(), arrayOf<Any>(seed.name, seed.colorHex, if (seed.isIncome) 1 else 0, index + 1))
        }
    }

    private fun seedProfiles(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO profiles (id, name, color_hex, sort_order) VALUES (1, 'Personal', '#4CAF50', 0)")
        db.execSQL("INSERT OR IGNORE INTO profiles (id, name, color_hex, sort_order) VALUES (2, 'Business', '#2196F3', 1)")
    }
}
