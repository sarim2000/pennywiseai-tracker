package com.pennywiseai.shared.data

import com.pennywiseai.shared.data.bootstrap.SharedDataInitializer
import com.pennywiseai.shared.data.local.SharedDatabase
import com.pennywiseai.shared.data.local.SharedDatabaseFactory
import com.pennywiseai.shared.data.repository.RoomSharedAccountRepository
import com.pennywiseai.shared.data.repository.RoomSharedBudgetRepository
import com.pennywiseai.shared.data.repository.RoomSharedCategoryRepository
import com.pennywiseai.shared.data.repository.RoomSharedExchangeRateRepository
import com.pennywiseai.shared.data.repository.RoomSharedMerchantMappingRepository
import com.pennywiseai.shared.data.repository.RoomSharedRuleRepository
import com.pennywiseai.shared.data.repository.RoomSharedSplitRepository
import com.pennywiseai.shared.data.repository.RoomSharedSubscriptionRepository
import com.pennywiseai.shared.data.repository.RoomSharedTransactionRepository
import com.pennywiseai.shared.data.repository.RoomSharedUnrecognizedSmsRepository
import com.pennywiseai.shared.data.repository.SharedAccountRepository
import com.pennywiseai.shared.data.repository.SharedBudgetRepository
import com.pennywiseai.shared.data.repository.SharedCategoryRepository
import com.pennywiseai.shared.data.repository.SharedExchangeRateRepository
import com.pennywiseai.shared.data.repository.SharedMerchantMappingRepository
import com.pennywiseai.shared.data.repository.SharedRuleRepository
import com.pennywiseai.shared.data.repository.SharedSplitRepository
import com.pennywiseai.shared.data.repository.SharedSubscriptionRepository
import com.pennywiseai.shared.data.repository.SharedTransactionRepository
import com.pennywiseai.shared.data.repository.SharedUnrecognizedSmsRepository

class SharedDataGraph private constructor(
    val database: SharedDatabase,
    val transactionRepository: SharedTransactionRepository,
    val categoryRepository: SharedCategoryRepository,
    val subscriptionRepository: SharedSubscriptionRepository,
    val accountRepository: SharedAccountRepository,
    val splitRepository: SharedSplitRepository,
    val merchantMappingRepository: SharedMerchantMappingRepository,
    val ruleRepository: SharedRuleRepository,
    val exchangeRateRepository: SharedExchangeRateRepository,
    val budgetRepository: SharedBudgetRepository,
    val unrecognizedSmsRepository: SharedUnrecognizedSmsRepository
) {
    private val initializer = SharedDataInitializer(categoryRepository)

    suspend fun initialize() {
        initializer.seedDefaultCategoriesIfNeeded()
    }

    companion object {
        val instance: SharedDataGraph by lazy { create() }

        fun create(factory: SharedDatabaseFactory = SharedDatabaseFactory()): SharedDataGraph {
            val database = factory.createDatabase()
            return SharedDataGraph(
                database = database,
                transactionRepository = RoomSharedTransactionRepository(database.transactionDao()),
                categoryRepository = RoomSharedCategoryRepository(database.categoryDao()),
                subscriptionRepository = RoomSharedSubscriptionRepository(database.subscriptionDao()),
                accountRepository = RoomSharedAccountRepository(database.accountBalanceDao(), database.cardDao(), database.transactionDao()),
                splitRepository = RoomSharedSplitRepository(database.transactionSplitDao()),
                merchantMappingRepository = RoomSharedMerchantMappingRepository(database.merchantMappingDao()),
                ruleRepository = RoomSharedRuleRepository(database.ruleDao(), database.ruleApplicationDao()),
                exchangeRateRepository = RoomSharedExchangeRateRepository(database.exchangeRateDao()),
                budgetRepository = RoomSharedBudgetRepository(database.budgetDao(), database.categoryBudgetLimitDao()),
                unrecognizedSmsRepository = RoomSharedUnrecognizedSmsRepository(database.unrecognizedSmsDao())
            )
        }
    }
}
