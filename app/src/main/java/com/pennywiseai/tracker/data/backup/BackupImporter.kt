package com.pennywiseai.tracker.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.*
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
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
                // backupJson tolerates missing keys (older backups → Kotlin
                // defaults) and unknown keys (newer backups → ignored). The
                // old Gson `normalized()` net is no longer needed because the
                // `= emptyList()` / field defaults on the models now actually
                // apply. See BackupSerializers + docs/backup-format.md.
                backupJson.decodeFromString<PennyWiseBackup>(content)
            } ?: throw Exception("Failed to read backup file")
        }
    }

    /**
     * Check if backup version is compatible. Accepts any `v1.x` backup; real
     * cross-version tolerance is handled by [backupJson], not by this gate.
     */
    private fun isCompatibleVersion(backup: PennyWiseBackup): Boolean {
        return backup.format.startsWith(PennyWiseBackup.COMPATIBLE_PREFIX)
    }

    /**
     * Insert each item, skipping (and counting via [onSkip]) any single row
     * that fails so one bad/corrupt record can't abort the entire restore.
     * Schema-drift safety (older backups missing newer columns) is already
     * handled upstream by [backupJson] defaults — this guards the rarer case
     * of a genuinely malformed row or a constraint violation.
     */
    private inline fun <T> List<T>.insertEachCounting(
        onSkip: () -> Unit,
        action: (T) -> Unit
    ) {
        for (item in this) {
            try {
                action(item)
            } catch (e: Exception) {
                Log.w("BackupImporter", "Skipped a row during import: ${e.message}")
                onSkip()
            }
        }
    }
    
    /**
     * Replace all existing data with backup data
     */
    private suspend fun replaceAllData(backup: PennyWiseBackup): ImportResult {
        var importedTransactions = 0
        var importedCategories = 0
        var skippedRows = 0

        return database.withTransaction {
            try {
                // Clear existing data
                database.transactionDao().deleteAllTransactions()
                database.categoryDao().deleteAllCategories()
                database.cardDao().deleteAllCards()
                database.accountBalanceDao().deleteAllBalances()
                database.subscriptionDao().deleteAllSubscriptions()
                database.merchantMappingDao().deleteAllMappings()
                database.merchantAliasDao().deleteAllAliases()
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
                // Cross-refs are cascade-deleted when transactions are cleared,
                // but tags live in their own table — clear both explicitly.
                database.tagDao().deleteAllCrossRefs()
                database.tagDao().deleteAllTags()
                // Note: budget categories and transaction splits are deleted via cascade (budget categories via budget deletion, transaction splits via transaction deletion)
                // Profiles deliberately preserved — defaults (Personal=1, Business=2)
                // are seeded on first launch and we don't want to wipe them.

                // Import loans / groups / profiles BEFORE transactions and
                // account balances so the foreign-key references on those
                // children (loan_id, group_id, profile_id) resolve.
                //
                // Loans & groups: both DAOs respect the explicit `id` field on
                // @Insert when non-zero, so backup IDs are preserved.
                // Profiles: explicit primary key, but local defaults (1, 2)
                // already exist — importProfilesAndBuildMap dedups by name and
                // returns a backup-id → final-local-id map so callers can
                // remap each entity's profileId field.
                backup.database.loans.insertEachCounting({ skippedRows++ }) { loan ->
                    database.loanDao().insertLoan(loan)
                }
                backup.database.transactionGroups.insertEachCounting({ skippedRows++ }) { group ->
                    database.transactionGroupDao().insertGroup(group)
                }
                val profileIdMap = importProfilesAndBuildMap(backup.database.profiles)

                // Older backups (pre-v1.1) didn't serialize loans / groups,
                // and profiles were never in v1.0 either. The transactions
                // inside such backups still carry loan_id / group_id /
                // profile_id values that point at entities we never restored.
                // When the user later creates a new loan/group/profile, Room
                // auto-assigns an ID that may silently collide with the
                // orphan pointer (ghost-linking unrelated transactions).
                // Strip orphan refs at import so those columns are NULL by
                // the time anything new is inserted; for profile_id we keep
                // refs that still point at a surviving local profile.
                val backupLoanIds = backup.database.loans.map { it.id }.toSet()
                val backupGroupIds = backup.database.transactionGroups.map { it.id }.toSet()
                val survivingProfileIds = database.profileDao().getAllProfiles()
                    .map { it.id }.toSet()
                fun resolveProfileId(oldId: Long?): Long? = when {
                    oldId == null -> null
                    profileIdMap.containsKey(oldId) -> profileIdMap[oldId]
                    oldId in survivingProfileIds -> oldId
                    else -> null
                }
                fun cleanTransaction(tx: TransactionEntity): TransactionEntity = tx.copy(
                    loanId = tx.loanId?.takeIf { it in backupLoanIds },
                    groupId = tx.groupId?.takeIf { it in backupGroupIds },
                    profileId = resolveProfileId(tx.profileId)
                )

                // Import all data
                backup.database.categories.insertEachCounting({ skippedRows++ }) { category ->
                    database.categoryDao().insertCategory(category)
                    importedCategories++
                }

                backup.database.transactions.insertEachCounting({ skippedRows++ }) { transaction ->
                    database.transactionDao().insertTransaction(cleanTransaction(transaction))
                    importedTransactions++
                }

                backup.database.cards.insertEachCounting({ skippedRows++ }) { card ->
                    database.cardDao().insertCard(card)
                }

                backup.database.accountBalances.insertEachCounting({ skippedRows++ }) { balance ->
                    // Remap profile_id the same way as transactions; fall back
                    // to PERSONAL when the source profile didn't survive (the
                    // column is non-null with a default of 1).
                    val mappedProfileId = resolveProfileId(balance.profileId)
                        ?: ProfileEntity.PERSONAL_ID
                    database.accountBalanceDao().insertBalance(
                        balance.copy(profileId = mappedProfileId)
                    )
                }
                
                backup.database.subscriptions.insertEachCounting({ skippedRows++ }) { subscription ->
                    database.subscriptionDao().insertSubscription(subscription)
                }

                backup.database.merchantMappings.insertEachCounting({ skippedRows++ }) { mapping ->
                    database.merchantMappingDao().insertMapping(mapping)
                }

                backup.database.merchantAliases.insertEachCounting({ skippedRows++ }) { alias ->
                    database.merchantAliasDao().insertOrUpdateAlias(alias)
                }

                backup.database.unrecognizedSms.insertEachCounting({ skippedRows++ }) { sms ->
                    database.unrecognizedSmsDao().insert(sms)
                }

                backup.database.chatMessages.insertEachCounting({ skippedRows++ }) { message ->
                    database.chatDao().insertMessage(message)
                }

                // Import new entities
                backup.database.rules.insertEachCounting({ skippedRows++ }) { rule ->
                    database.ruleDao().insertRule(rule)
                }
                backup.database.ruleApplications.insertEachCounting({ skippedRows++ }) { application ->
                    database.ruleApplicationDao().insertApplication(application)
                }
                backup.database.exchangeRates.insertEachCounting({ skippedRows++ }) { rate ->
                    database.exchangeRateDao().insertExchangeRate(rate)
                }
                backup.database.budgets.insertEachCounting({ skippedRows++ }) { budget ->
                    database.budgetDao().insertBudget(budget)
                }
                backup.database.budgetCategories.insertEachCounting({ skippedRows++ }) { category ->
                    database.budgetDao().insertBudgetCategory(category)
                }
                backup.database.transactionSplits.insertEachCounting({ skippedRows++ }) { split ->
                    database.transactionSplitDao().insertSplit(split)
                }
                backup.database.bankNotifications.insertEachCounting({ skippedRows++ }) { notification ->
                    database.bankNotificationDao().insertOrReplace(notification)
                }

                // Tags + cross-refs: ids are preserved in REPLACE_ALL (so are
                // transaction ids), so insert tags first then the links. Insert
                // tags before cross-refs to satisfy the foreign keys.
                backup.database.tags.insertEachCounting({ skippedRows++ }) { tag ->
                    database.tagDao().insertTag(tag)
                }
                backup.database.transactionTagCrossRefs.insertEachCounting({ skippedRows++ }) { ref ->
                    database.tagDao().insertCrossRef(ref)
                }

                // Profiles were already imported earlier (before transactions /
                // account balances) so foreign-key remapping could happen.

                try {
                    database.budgetSnapshotDao().insertGroupSnapshots(backup.database.budgetMonthSnapshots)
                } catch (e: Exception) {
                    Log.w("BackupImporter", "Skipped snapshot batch: ${e.message}")
                    skippedRows += backup.database.budgetMonthSnapshots.size
                }
                try {
                    database.budgetSnapshotDao().insertCategorySnapshots(backup.database.budgetCategoryMonthSnapshots)
                } catch (e: Exception) {
                    Log.w("BackupImporter", "Skipped snapshot batch: ${e.message}")
                    skippedRows += backup.database.budgetCategoryMonthSnapshots.size
                }

                // Import preferences
                importPreferences(backup.preferences)

                ImportResult.Success(
                    importedTransactions = importedTransactions,
                    importedCategories = importedCategories,
                    skippedDuplicates = 0,
                    skippedRows = skippedRows
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
        var skippedRows = 0

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
                backup.database.categories.insertEachCounting({ skippedRows++ }) { category ->
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
                //
                // Dedup by an immutable composite key so a repeat MERGE of the
                // same backup maps each backup row to its already-imported
                // local row instead of inserting a ghost duplicate. We avoid
                // mutable fields (amount, note, status) so user edits between
                // imports don't break the match.
                val existingLoans = database.loanDao().getAllLoans().first()
                val existingLoanKeyToId = existingLoans.associate {
                    Triple(it.personName, it.direction, it.createdAt) to it.id
                }
                val oldToNewLoanIdMap = mutableMapOf<Long, Long>()
                backup.database.loans.insertEachCounting({ skippedRows++ }) { loan ->
                    val key = Triple(loan.personName, loan.direction, loan.createdAt)
                    val existingId = existingLoanKeyToId[key]
                    val newId = existingId
                        ?: database.loanDao().insertLoan(loan.copy(id = 0))
                    if (loan.id != 0L) oldToNewLoanIdMap[loan.id] = newId
                }

                val existingGroups = database.transactionGroupDao().getAllGroups().first()
                val existingGroupKeyToId = existingGroups.associate {
                    (it.name to it.createdAt) to it.id
                }
                val oldToNewGroupIdMap = mutableMapOf<Long, Long>()
                backup.database.transactionGroups.insertEachCounting({ skippedRows++ }) { group ->
                    val key = group.name to group.createdAt
                    val existingId = existingGroupKeyToId[key]
                    val newId = existingId
                        ?: database.transactionGroupDao().insertGroup(group.copy(id = 0))
                    if (group.id != 0L) oldToNewGroupIdMap[group.id] = newId
                }

                // Build profile id map up-front so transactions and account
                // balances can remap profile_id during insert (same hazard as
                // loans/groups — a stale backup profile_id can collide with a
                // future user-created profile and ghost-link transactions).
                val profileIdMap = importProfilesAndBuildMap(backup.database.profiles)
                val survivingProfileIds = database.profileDao().getAllProfiles()
                    .map { it.id }.toSet()
                fun resolveProfileId(oldId: Long?): Long? = when {
                    oldId == null -> null
                    profileIdMap.containsKey(oldId) -> profileIdMap[oldId]
                    oldId in survivingProfileIds -> oldId
                    else -> null
                }

                // Import transactions (skip duplicates by hash)
                // Build mapping from old transaction IDs to new IDs for split/application imports
                val oldToNewTransactionIdMap = mutableMapOf<Long, Long>()

                backup.database.transactions.insertEachCounting({ skippedRows++ }) { transaction ->
                    if (!existingTransactionHashes.contains(transaction.transactionHash)) {
                        val oldId = transaction.id
                        val newTransaction = transaction.copy(
                            id = 0,
                            loanId = transaction.loanId?.let { oldToNewLoanIdMap[it] },
                            groupId = transaction.groupId?.let { oldToNewGroupIdMap[it] },
                            profileId = resolveProfileId(transaction.profileId)
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
                importCardsWithMerge(backup.database.cards) { skippedRows++ }
                importAccountBalancesWithMerge(backup.database.accountBalances, { resolveProfileId(it) }) { skippedRows++ }
                importSubscriptionsWithMerge(backup.database.subscriptions) { skippedRows++ }
                importMerchantMappingsWithMerge(backup.database.merchantMappings) { skippedRows++ }
                importMerchantAliasesWithMerge(backup.database.merchantAliases) { skippedRows++ }

                // Import new entities with correct ID mapping for splits and applications
                // Rules and budgets: skip if exists locally (merge semantics - don't overwrite local changes)
                importRulesWithMerge(backup.database.rules) { skippedRows++ }
                
                // Get existing rule application IDs to avoid duplicates on repeat MERGE
                val existingRuleAppIds = database.ruleApplicationDao().getAllApplications().first()
                    .map { it.id }.toSet()
                backup.database.ruleApplications.insertEachCounting({ skippedRows++ }) { application ->
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
                backup.database.exchangeRates.insertEachCounting({ skippedRows++ }) { rate ->
                    // Skip if currency pair already exists locally to preserve custom rates
                    val existingPair = existingRates.find { 
                        it.fromCurrency == rate.fromCurrency && it.toCurrency == rate.toCurrency 
                    }
                    if (existingPair == null) {
                        database.exchangeRateDao().insertExchangeRate(rate)
                    }
                }
                importBudgetsWithMerge(backup.database.budgets, backup.database.budgetCategories) { skippedRows++ }
                
                // Get existing split keys to avoid duplicates on repeat MERGE
                // A split is unique by (transaction_id, category, amount)
                val existingSplits = database.transactionSplitDao().getAllSplits().first()
                val existingSplitKeys = existingSplits.map { "${it.transactionId}|${it.category}|${it.amount}" }.toSet()
                backup.database.transactionSplits.insertEachCounting({ skippedRows++ }) { split ->
                    // Map transaction ID to new ID if available
                    val mappedTransactionId = oldToNewTransactionIdMap[split.transactionId] ?: split.transactionId
                    // Skip if split already exists locally (preserves local splits)
                    val splitKey = "${mappedTransactionId}|${split.category}|${split.amount}"
                    if (!existingSplitKeys.contains(splitKey)) {
                        val updatedSplit = split.copy(id = 0, transactionId = mappedTransactionId)
                        database.transactionSplitDao().insertSplit(updatedSplit)
                    }
                }
                backup.database.bankNotifications.insertEachCounting({ skippedRows++ }) { notification ->
                    database.bankNotificationDao().insertOrReplace(notification)
                }

                // Tags: dedup by name (case-insensitive) like categories, then
                // remap the cross-refs' tag_id and transaction_id to their new
                // local ids. A ref is skipped if its transaction wasn't imported
                // (missing from the id map). insertCrossRef IGNOREs duplicates,
                // so a repeat MERGE is idempotent.
                val existingTagIdByName = database.tagDao().getAllTagsSync()
                    .associate { it.name.lowercase() to it.id }
                    .toMutableMap()
                val oldToNewTagIdMap = mutableMapOf<Long, Long>()
                backup.database.tags.insertEachCounting({ skippedRows++ }) { tag ->
                    val key = tag.name.trim().lowercase()
                    if (key.isNotEmpty()) {
                        val newId = existingTagIdByName[key]
                            ?: database.tagDao().insertTag(tag.copy(id = 0))
                                .also { id -> if (id > 0L) existingTagIdByName[key] = id }
                        if (newId > 0L && tag.id != 0L) oldToNewTagIdMap[tag.id] = newId
                    }
                }
                backup.database.transactionTagCrossRefs.insertEachCounting({ skippedRows++ }) { ref ->
                    val mappedTransactionId = oldToNewTransactionIdMap[ref.transactionId]
                    val mappedTagId = oldToNewTagIdMap[ref.tagId]
                    if (mappedTransactionId != null && mappedTagId != null) {
                        database.tagDao().insertCrossRef(
                            ref.copy(transactionId = mappedTransactionId, tagId = mappedTagId)
                        )
                    }
                }

                // Profiles were already imported earlier (before transactions /
                // account balances) so foreign-key remapping could happen.

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
                        val groupSnapshots = (groupByMonth[year to month] ?: emptyList())
                            .map { it.copy(id = 0) }
                        val categorySnapshots = (catByMonth[year to month] ?: emptyList())
                            .map { it.copy(id = 0) }
                        try {
                            database.budgetSnapshotDao().replaceMonthSnapshots(
                                year = year,
                                month = month,
                                groupSnapshots = groupSnapshots,
                                categorySnapshots = categorySnapshots
                            )
                        } catch (e: Exception) {
                            Log.w("BackupImporter", "Skipped snapshot batch: ${e.message}")
                            skippedRows += groupSnapshots.size + categorySnapshots.size
                        }
                    }
                }

                // Import preferences (merge with existing)
                importPreferences(backup.preferences)
                
                ImportResult.Success(
                    importedTransactions = importedTransactions,
                    importedCategories = importedCategories,
                    skippedDuplicates = skippedDuplicates,
                    skippedRows = skippedRows
                )
            } catch (e: Exception) {
                throw e
            }
        }
    }
    
    /**
     * Import cards with duplicate checking
     */
    private suspend fun importCardsWithMerge(cards: List<CardEntity>, onSkip: () -> Unit) {
        val existingCards = database.cardDao().getAllCards().first()
        val existingCardKeys = existingCards.map { "${it.bankName}_${it.cardLast4}" }.toSet()

        cards.insertEachCounting(onSkip) { card ->
            val key = "${card.bankName}_${card.cardLast4}"
            if (!existingCardKeys.contains(key)) {
                val newCard = card.copy(id = 0)
                database.cardDao().insertCard(newCard)
            }
        }
    }
    
    /**
     * Import account balances with duplicate checking.
     * @param resolveProfileId remaps the source profile_id to its final local
     *   id (handles name-based dedup + orphan stripping). Caller passes a
     *   closure that already knows about the current profile map.
     */
    private suspend fun importAccountBalancesWithMerge(
        balances: List<AccountBalanceEntity>,
        resolveProfileId: (Long?) -> Long?,
        onSkip: () -> Unit
    ) {
        // For balances, we'll import all as they represent historical data
        balances.insertEachCounting(onSkip) { balance ->
            val mappedProfileId = resolveProfileId(balance.profileId)
                ?: ProfileEntity.PERSONAL_ID
            val newBalance = balance.copy(id = 0, profileId = mappedProfileId)
            database.accountBalanceDao().insertBalance(newBalance)
        }
    }
    
    /**
     * Import subscriptions with duplicate checking
     */
    private suspend fun importSubscriptionsWithMerge(subscriptions: List<SubscriptionEntity>, onSkip: () -> Unit) {
        val existingSubscriptions = database.subscriptionDao().getAllSubscriptions().first()
        val existingKeys = existingSubscriptions.map { "${it.merchantName}_${it.amount}" }.toSet()

        subscriptions.insertEachCounting(onSkip) { subscription ->
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
    private suspend fun importMerchantMappingsWithMerge(mappings: List<MerchantMappingEntity>, onSkip: () -> Unit) {
        mappings.insertEachCounting(onSkip) { mapping ->
            database.merchantMappingDao().insertMapping(mapping)
        }
    }
    
    /**
     * Import merchant aliases with merge. Aliases are keyed by merchant_name
     * (the PK), so a REPLACE insert lets a backup's alias overwrite any local
     * one for the same merchant — matching how merchant mappings merge.
     */
    private suspend fun importMerchantAliasesWithMerge(aliases: List<MerchantAliasEntity>, onSkip: () -> Unit) {
        aliases.insertEachCounting(onSkip) { alias ->
            database.merchantAliasDao().insertOrUpdateAlias(alias)
        }
    }

    /**
     * Import rules with merge semantics - skip if exists locally
     */
    private suspend fun importRulesWithMerge(rules: List<RuleEntity>, onSkip: () -> Unit) {
        val existingRuleIds = database.ruleDao().getAllRules().first().map { it.id }.toSet()

        rules.insertEachCounting(onSkip) { rule ->
            if (!existingRuleIds.contains(rule.id)) {
                database.ruleDao().insertRule(rule)
            }
        }
    }
    
    /**
     * Import budgets and budget categories with merge semantics - skip if exists locally
     */
    private suspend fun importBudgetsWithMerge(budgets: List<BudgetEntity>, budgetCategories: List<BudgetCategoryEntity>, onSkip: () -> Unit) {
        val existingBudgetNames = database.budgetDao().getAllBudgets().first().map { it.name }.toSet()

        budgets.insertEachCounting(onSkip) { budget ->
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
     * Insert backup profiles and return a `backup-id → local-id` map so other
     * tables (transactions, account balances) can rewrite their profile_id
     * column to point at the surviving local profile.
     *
     * Dedup priority:
     *  1. Match by `name` — if a local profile with the same name already
     *     exists, the backup profile is treated as the same logical profile
     *     and we map to its local id (no insert).
     *  2. No name match, original id unused locally — insert with the
     *     backup's original id.
     *  3. No name match, original id collides with a different local
     *     profile — insert under a fresh id (max existing + 1) so we don't
     *     overwrite the local row of a different identity.
     *
     * This keeps default profiles (Personal=1, Business=2) stable across
     * imports because they match by name; only genuine new custom profiles
     * get inserted.
     */
    private suspend fun importProfilesAndBuildMap(
        profiles: List<ProfileEntity>
    ): Map<Long, Long> {
        if (profiles.isEmpty()) return emptyMap()

        val existing = database.profileDao().getAllProfiles()
        val existingByName = existing.associateBy { it.name }
        val takenIds = existing.map { it.id }.toMutableSet()
        var nextId = (takenIds.maxOrNull() ?: 0L) + 1

        val map = mutableMapOf<Long, Long>()
        for (profile in profiles) {
            val matched = existingByName[profile.name]
            val finalId = when {
                matched != null -> matched.id
                profile.id !in takenIds -> {
                    database.profileDao().insert(profile)
                    takenIds.add(profile.id)
                    profile.id
                }
                else -> {
                    // Advance past any ids that case 2 above has inserted
                    // during this same loop, otherwise `nextId` might already
                    // belong to a freshly-imported profile and trip the
                    // UNIQUE PK constraint on insert.
                    while (nextId in takenIds) nextId++
                    val newId = nextId++
                    database.profileDao().insert(profile.copy(id = newId))
                    takenIds.add(newId)
                    newId
                }
            }
            map[profile.id] = finalId
        }
        return map
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
        // Only enable custom-date mode when a paired date is present. A flag-without-date
        // state would leave a "Scan from a custom start date" subtitle while the limited-data
        // banner is suppressed (TransactionsViewModel can't resolve a start date).
        val restoredCustomDate = preferences.sms.smsScanCustomDate
        restoredCustomDate?.let {
            userPreferencesRepository.updateSmsScanCustomDate(it)
        }
        userPreferencesRepository.updateSmsScanUseCustomDate(
            preferences.sms.smsScanUseCustomDate && restoredCustomDate != null
        )
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