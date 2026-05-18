package com.pennywiseai.tracker.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.GsonBuilder
import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.*
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: PennyWiseDatabase,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    
    private val gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
        .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
        .registerTypeAdapter(java.math.BigDecimal::class.java, BigDecimalTypeAdapter())
        .create()
    
    /**
     * Import backup from a file URI
     */
    suspend fun importBackup(
        uri: Uri,
        strategy: ImportStrategy = ImportStrategy.MERGE
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            // Read and parse the backup file
            val backup = readBackupFile(uri)
            
            // Validate backup version
            if (!isCompatibleVersion(backup)) {
                return@withContext ImportResult.Error("Incompatible backup version")
            }
            
            // Import based on strategy
            when (strategy) {
                ImportStrategy.REPLACE_ALL -> replaceAllData(backup)
                ImportStrategy.MERGE -> mergeData(backup)
                ImportStrategy.SELECTIVE -> mergeData(backup) // For now, same as merge
            }
        } catch (e: Exception) {
            Log.e("BackupImporter", "Import failed", e)
            ImportResult.Error("Import failed: ${e.message}")
        }
    }
    
    /**
     * Read and parse backup file
     */
    private suspend fun readBackupFile(uri: Uri): PennyWiseBackup {
        return withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = reader.readText()
                gson.fromJson(content, PennyWiseBackup::class.java)
            } ?: throw Exception("Failed to read backup file")
        }
    }
    
    /**
     * Check if backup version is compatible
     */
    private fun isCompatibleVersion(backup: PennyWiseBackup): Boolean {
        // For now, accept all v1.x backups
        return backup.format.startsWith("PennyWise Backup v1")
    }
    
    /**
     * Replace all existing data with backup data
     */
    private suspend fun replaceAllData(backup: PennyWiseBackup): ImportResult {
        var importedTransactions = 0
        var importedCategories = 0

        // Older backups (pre-v1.1) didn't serialize loans / transaction_groups.
        // The transactions inside such backups still carry loan_id / group_id
        // values that point at entities we never restored — and when the user
        // later creates a new loan/group, Room auto-assigns an ID starting at 1
        // and silently collides with those orphaned pointers (ghost-linking
        // unrelated transactions). Strip the orphan refs at import time so
        // those columns are NULL by the time anything new is inserted.
        val backupLoanIds = backup.database.loans.map { it.id }.toSet()
        val backupGroupIds = backup.database.transactionGroups.map { it.id }.toSet()
        fun cleanTransaction(tx: TransactionEntity): TransactionEntity = tx.copy(
            loanId = tx.loanId?.takeIf { it in backupLoanIds },
            groupId = tx.groupId?.takeIf { it in backupGroupIds }
        )

        return database.withTransaction {
            try {
                // Clear existing data
                database.transactionDao().deleteAllTransactions()
                database.categoryDao().deleteAllCategories()
                database.cardDao().deleteAllCards()
                database.accountBalanceDao().deleteAllBalances()
                database.subscriptionDao().deleteAllSubscriptions()
                database.merchantMappingDao().deleteAllMappings()
                database.unrecognizedSmsDao().deleteAll()
                database.chatDao().deleteAllMessages()
                database.ruleDao().deleteAllRules()
                database.ruleApplicationDao().deleteAllApplications()
                database.budgetDao().deleteAllBudgets()
                database.exchangeRateDao().deleteAllRates()
                database.bankNotificationDao().deleteAllNotifications()
                database.loanDao().deleteAllLoans()
                database.transactionGroupDao().deleteAllGroups()
                database.budgetSnapshotDao().deleteAllGroupSnapshots()
                database.budgetSnapshotDao().deleteAllCategorySnapshots()
                // Note: budget categories and transaction splits are deleted via cascade (budget categories via budget deletion, transaction splits via transaction deletion)
                // Profiles deliberately preserved — defaults (Personal=1, Business=2)
                // are seeded on first launch and we don't want to wipe them.

                // Import loans / groups BEFORE transactions so the foreign-key
                // references on TransactionEntity.loan_id / group_id resolve.
                // Both DAOs respect the explicit `id` field on @Insert when it
                // is non-zero, so the backup's original IDs are preserved.
                backup.database.loans.forEach { loan ->
                    database.loanDao().insertLoan(loan)
                }
                backup.database.transactionGroups.forEach { group ->
                    database.transactionGroupDao().insertGroup(group)
                }

                // Import all data
                backup.database.categories.forEach { category ->
                    database.categoryDao().insertCategory(category)
                    importedCategories++
                }

                backup.database.transactions.forEach { transaction ->
                    database.transactionDao().insertTransaction(cleanTransaction(transaction))
                    importedTransactions++
                }
                
                backup.database.cards.forEach { card ->
                    database.cardDao().insertCard(card)
                }
                
                backup.database.accountBalances.forEach { balance ->
                    database.accountBalanceDao().insertBalance(balance)
                }
                
                backup.database.subscriptions.forEach { subscription ->
                    database.subscriptionDao().insertSubscription(subscription)
                }
                
                backup.database.merchantMappings.forEach { mapping ->
                    database.merchantMappingDao().insertMapping(mapping)
                }
                
                backup.database.unrecognizedSms.forEach { sms ->
                    database.unrecognizedSmsDao().insert(sms)
                }
                
                backup.database.chatMessages.forEach { message ->
                    database.chatDao().insertMessage(message)
                }
                
                // Import new entities
                backup.database.rules.forEach { rule ->
                    database.ruleDao().insertRule(rule)
                }
                backup.database.ruleApplications.forEach { application ->
                    database.ruleApplicationDao().insertApplication(application)
                }
                backup.database.exchangeRates.forEach { rate ->
                    database.exchangeRateDao().insertExchangeRate(rate)
                }
                backup.database.budgets.forEach { budget ->
                    database.budgetDao().insertBudget(budget)
                }
                backup.database.budgetCategories.forEach { category ->
                    database.budgetDao().insertBudgetCategory(category)
                }
                backup.database.transactionSplits.forEach { split ->
                    database.transactionSplitDao().insertSplit(split)
                }
                backup.database.bankNotifications.forEach { notification ->
                    database.bankNotificationDao().insertOrReplace(notification)
                }

                // Profiles: insert only ones we don't already have (preserves
                // the default Personal=1 / Business=2 rows and any custom
                // profiles the user has set up locally).
                if (backup.database.profiles.isNotEmpty()) {
                    val existingProfileIds = database.profileDao().getAllProfiles()
                        .map { it.id }.toSet()
                    backup.database.profiles
                        .filter { it.id !in existingProfileIds }
                        .takeIf { it.isNotEmpty() }
                        ?.let { database.profileDao().insertAll(it) }
                }

                database.budgetSnapshotDao().insertGroupSnapshots(backup.database.budgetMonthSnapshots)
                database.budgetSnapshotDao().insertCategorySnapshots(backup.database.budgetCategoryMonthSnapshots)

                // Import preferences
                importPreferences(backup.preferences)

                ImportResult.Success(
                    importedTransactions = importedTransactions,
                    importedCategories = importedCategories,
                    skippedDuplicates = 0
                )
            } catch (e: Exception) {
                throw e
            }
        }
    }
    
    /**
     * Merge backup data with existing data
     */
    private suspend fun mergeData(backup: PennyWiseBackup): ImportResult {
        var importedTransactions = 0
        var importedCategories = 0
        var skippedDuplicates = 0

        return database.withTransaction {
            try {
                // Get existing data for duplicate checking
                val existingTransactions = database.transactionDao()
                    .getAllTransactions().first()
                val existingTransactionHashes = existingTransactions.map { it.transactionHash }.toSet()
                val existingHashToIdMap = existingTransactions.associateBy({ it.transactionHash }, { it.id })

                val existingCategories = database.categoryDao()
                    .getAllCategories().first()
                    .map { it.name }
                    .toSet()

                // Import categories (merge by name)
                backup.database.categories.forEach { category ->
                    if (!existingCategories.contains(category.name)) {
                        // Generate new ID for imported category
                        val newCategory = category.copy(id = 0)
                        database.categoryDao().insertCategory(newCategory)
                        importedCategories++
                    }
                }

                // Import loans / groups BEFORE transactions so we can remap
                // TransactionEntity.loan_id and group_id to the new local IDs
                // (Room hands us fresh IDs because we insert with id = 0 in
                // merge mode). For older backups that don't carry these
                // entities at all, the maps stay empty and the corresponding
                // refs on transactions get stripped to NULL — preventing the
                // ghost-link bug where an old loan_id silently collides with
                // a future auto-generated loan ID.
                val oldToNewLoanIdMap = mutableMapOf<Long, Long>()
                backup.database.loans.forEach { loan ->
                    val newId = database.loanDao().insertLoan(loan.copy(id = 0))
                    if (loan.id != 0L) oldToNewLoanIdMap[loan.id] = newId
                }
                val oldToNewGroupIdMap = mutableMapOf<Long, Long>()
                backup.database.transactionGroups.forEach { group ->
                    val newId = database.transactionGroupDao().insertGroup(group.copy(id = 0))
                    if (group.id != 0L) oldToNewGroupIdMap[group.id] = newId
                }

                // Import transactions (skip duplicates by hash)
                // Build mapping from old transaction IDs to new IDs for split/application imports
                val oldToNewTransactionIdMap = mutableMapOf<Long, Long>()

                backup.database.transactions.forEach { transaction ->
                    if (!existingTransactionHashes.contains(transaction.transactionHash)) {
                        val oldId = transaction.id
                        val newTransaction = transaction.copy(
                            id = 0,
                            loanId = transaction.loanId?.let { oldToNewLoanIdMap[it] },
                            groupId = transaction.groupId?.let { oldToNewGroupIdMap[it] }
                        )
                        val newId = database.transactionDao().insertTransaction(newTransaction)
                        // Track old ID -> new ID mapping directly from insert result
                        if (oldId != 0L) {
                            oldToNewTransactionIdMap[oldId] = newId
                        }
                        importedTransactions++
                    } else {
                        // Also map IDs for duplicate transactions so splits/applications reference correct local ID
                        val localId = existingHashToIdMap[transaction.transactionHash]
                        if (transaction.id != 0L && localId != null) {
                            oldToNewTransactionIdMap[transaction.id] = localId
                        }
                        skippedDuplicates++
                    }
                }
                
                // Import other entities with duplicate checking
                importCardsWithMerge(backup.database.cards)
                importAccountBalancesWithMerge(backup.database.accountBalances)
                importSubscriptionsWithMerge(backup.database.subscriptions)
                importMerchantMappingsWithMerge(backup.database.merchantMappings)
                
                // Import new entities with correct ID mapping for splits and applications
                // Rules and budgets: skip if exists locally (merge semantics - don't overwrite local changes)
                importRulesWithMerge(backup.database.rules)
                
                // Get existing rule application IDs to avoid duplicates on repeat MERGE
                val existingRuleAppIds = database.ruleApplicationDao().getAllApplications().first()
                    .map { it.id }.toSet()
                backup.database.ruleApplications.forEach { application ->
                    // Skip if application already exists locally (preserves local rule applications)
                    if (!existingRuleAppIds.contains(application.id)) {
                        val mappedTransactionId = application.transactionId.toLongOrNull()?.let { oldId ->
                            oldToNewTransactionIdMap[oldId]?.toString() ?: application.transactionId
                        } ?: application.transactionId
                        val updatedApplication = application.copy(transactionId = mappedTransactionId)
                        database.ruleApplicationDao().insertApplication(updatedApplication)
                    }
                }
                // Get existing rates once to avoid N+1 queries
                val existingRates = database.exchangeRateDao().getAllRatesFlow().first()
                backup.database.exchangeRates.forEach { rate ->
                    // Skip if currency pair already exists locally to preserve custom rates
                    val existingPair = existingRates.find { 
                        it.fromCurrency == rate.fromCurrency && it.toCurrency == rate.toCurrency 
                    }
                    if (existingPair == null) {
                        database.exchangeRateDao().insertExchangeRate(rate)
                    }
                }
                importBudgetsWithMerge(backup.database.budgets, backup.database.budgetCategories)
                
                // Get existing split keys to avoid duplicates on repeat MERGE
                // A split is unique by (transaction_id, category, amount)
                val existingSplits = database.transactionSplitDao().getAllSplits().first()
                val existingSplitKeys = existingSplits.map { "${it.transactionId}|${it.category}|${it.amount}" }.toSet()
                backup.database.transactionSplits.forEach { split ->
                    // Map transaction ID to new ID if available
                    val mappedTransactionId = oldToNewTransactionIdMap[split.transactionId] ?: split.transactionId
                    // Skip if split already exists locally (preserves local splits)
                    val splitKey = "${mappedTransactionId}|${split.category}|${split.amount}"
                    if (!existingSplitKeys.contains(splitKey)) {
                        val updatedSplit = split.copy(id = 0, transactionId = mappedTransactionId)
                        database.transactionSplitDao().insertSplit(updatedSplit)
                    }
                }
                backup.database.bankNotifications.forEach { notification ->
                    database.bankNotificationDao().insertOrReplace(notification)
                }

                // Profiles: insert ones we don't have locally yet so custom
                // profiles from the backup come along, but the user's existing
                // defaults / customisations are preserved.
                if (backup.database.profiles.isNotEmpty()) {
                    val existingProfileIds = database.profileDao().getAllProfiles()
                        .map { it.id }.toSet()
                    backup.database.profiles
                        .filter { it.id !in existingProfileIds }
                        .takeIf { it.isNotEmpty() }
                        ?.let { database.profileDao().insertAll(it) }
                }

                // Budget month snapshots: merge by (year, month) pair —
                // backup wins for any month the local DB also has so a stale
                // local snapshot doesn't double up with the imported one.
                if (backup.database.budgetMonthSnapshots.isNotEmpty() ||
                    backup.database.budgetCategoryMonthSnapshots.isNotEmpty()
                ) {
                    val groupByMonth = backup.database.budgetMonthSnapshots
                        .groupBy { it.year to it.month }
                    val catByMonth = backup.database.budgetCategoryMonthSnapshots
                        .groupBy { it.year to it.month }
                    val allMonths = (groupByMonth.keys + catByMonth.keys)
                    allMonths.forEach { (year, month) ->
                        database.budgetSnapshotDao().replaceMonthSnapshots(
                            year = year,
                            month = month,
                            groupSnapshots = (groupByMonth[year to month] ?: emptyList())
                                .map { it.copy(id = 0) },
                            categorySnapshots = (catByMonth[year to month] ?: emptyList())
                                .map { it.copy(id = 0) }
                        )
                    }
                }

                // Import preferences (merge with existing)
                importPreferences(backup.preferences)
                
                ImportResult.Success(
                    importedTransactions = importedTransactions,
                    importedCategories = importedCategories,
                    skippedDuplicates = skippedDuplicates
                )
            } catch (e: Exception) {
                throw e
            }
        }
    }
    
    /**
     * Import cards with duplicate checking
     */
    private suspend fun importCardsWithMerge(cards: List<CardEntity>) {
        val existingCards = database.cardDao().getAllCards().first()
        val existingCardKeys = existingCards.map { "${it.bankName}_${it.cardLast4}" }.toSet()
        
        cards.forEach { card ->
            val key = "${card.bankName}_${card.cardLast4}"
            if (!existingCardKeys.contains(key)) {
                val newCard = card.copy(id = 0)
                database.cardDao().insertCard(newCard)
            }
        }
    }
    
    /**
     * Import account balances with duplicate checking
     */
    private suspend fun importAccountBalancesWithMerge(balances: List<AccountBalanceEntity>) {
        // For balances, we'll import all as they represent historical data
        balances.forEach { balance ->
            val newBalance = balance.copy(id = 0)
            database.accountBalanceDao().insertBalance(newBalance)
        }
    }
    
    /**
     * Import subscriptions with duplicate checking
     */
    private suspend fun importSubscriptionsWithMerge(subscriptions: List<SubscriptionEntity>) {
        val existingSubscriptions = database.subscriptionDao().getAllSubscriptions().first()
        val existingKeys = existingSubscriptions.map { "${it.merchantName}_${it.amount}" }.toSet()
        
        subscriptions.forEach { subscription ->
            val key = "${subscription.merchantName}_${subscription.amount}"
            if (!existingKeys.contains(key)) {
                val newSubscription = subscription.copy(id = 0)
                database.subscriptionDao().insertSubscription(newSubscription)
            }
        }
    }
    
    /**
     * Import merchant mappings with merge
     */
    private suspend fun importMerchantMappingsWithMerge(mappings: List<MerchantMappingEntity>) {
        mappings.forEach { mapping ->
            database.merchantMappingDao().insertMapping(mapping)
        }
    }
    
    /**
     * Import rules with merge semantics - skip if exists locally
     */
    private suspend fun importRulesWithMerge(rules: List<RuleEntity>) {
        val existingRuleIds = database.ruleDao().getAllRules().first().map { it.id }.toSet()
        
        rules.forEach { rule ->
            if (!existingRuleIds.contains(rule.id)) {
                database.ruleDao().insertRule(rule)
            }
        }
    }
    
    /**
     * Import budgets and budget categories with merge semantics - skip if exists locally
     */
    private suspend fun importBudgetsWithMerge(budgets: List<BudgetEntity>, budgetCategories: List<BudgetCategoryEntity>) {
        val existingBudgetNames = database.budgetDao().getAllBudgets().first().map { it.name }.toSet()
        
        budgets.forEach { budget ->
            if (!existingBudgetNames.contains(budget.name)) {
                // Use the return value from insertBudget to get the new ID directly
                // instead of querying (which would miss inactive budgets)
                val newBudgetId = database.budgetDao().insertBudget(budget.copy(id = 0))
                
                // Import categories for this budget using the returned ID
                budgetCategories.filter { it.budgetId == budget.id }.forEach { category ->
                    val updatedCategory = category.copy(id = 0, budgetId = newBudgetId)
                    database.budgetDao().insertBudgetCategory(updatedCategory)
                }
            }
        }
    }
    
    /**
     * Import user preferences
     */
    private suspend fun importPreferences(preferences: PreferencesSnapshot) {
        // Theme preferences
        preferences.theme.isDarkThemeEnabled?.let {
            userPreferencesRepository.updateDarkTheme(it)
        }
        userPreferencesRepository.updateDynamicColor(preferences.theme.isDynamicColorEnabled)
        
        // SMS preferences
        userPreferencesRepository.updateHasSkippedSmsPermission(preferences.sms.hasSkippedSmsPermission)
        userPreferencesRepository.updateSmsScanMonths(preferences.sms.smsScanMonths)
        preferences.sms.lastScanTimestamp?.let {
            userPreferencesRepository.updateLastScanTimestamp(it)
        }
        preferences.sms.lastScanPeriod?.let {
            userPreferencesRepository.updateLastScanPeriod(it)
        }
        
        // Developer preferences
        userPreferencesRepository.updateDeveloperMode(preferences.developer.isDeveloperModeEnabled)
        preferences.developer.systemPrompt?.let {
            userPreferencesRepository.updateSystemPrompt(it)
        }
        
        // App preferences
        userPreferencesRepository.updateHasShownScanTutorial(preferences.app.hasShownScanTutorial)
        preferences.app.firstLaunchTime?.let {
            userPreferencesRepository.updateFirstLaunchTime(it)
        }
        userPreferencesRepository.updateHasShownReviewPrompt(preferences.app.hasShownReviewPrompt)
        preferences.app.lastReviewPromptTime?.let {
            userPreferencesRepository.updateLastReviewPromptTime(it)
        }
    }
}