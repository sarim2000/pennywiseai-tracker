package com.pennywiseai.tracker.data.backup

import com.pennywiseai.tracker.data.preferences.BankAccountMergeStore
import com.pennywiseai.tracker.data.database.entity.builtinKey

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.*
import com.pennywiseai.tracker.billing.license.LicenseManager
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.decodeFromStream
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: PennyWiseDatabase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val licenseManager: LicenseManager,
    private val ignoredAccountsStore: IgnoredAccountsStore
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
            val result = when (strategy) {
                ImportStrategy.REPLACE_ALL -> replaceAllData(backup)
                ImportStrategy.MERGE -> mergeData(backup)
                ImportStrategy.SELECTIVE -> mergeData(backup) // For now, same as merge
            }
            // Receipt photos (#839), after the DB commit: write only the files
            // a transaction now in the DB points at, so a merge restores the
            // photos of the rows it kept (row ids get remapped; paths don't).
            if (result is ImportResult.Success && backup.receipts.isNotEmpty()) {
                val referenced = database.transactionDao().getAllTransactions().first()
                    .mapNotNullTo(HashSet()) { it.receiptPath }
                val restored = BackupReceipts.restore(context.filesDir, backup.receipts, referenced)
                Log.i("BackupImporter", "Restored $restored of ${backup.receipts.size} receipt photos")
            }
            result
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
                // Streamed, so a large backup isn't also held as one big String.
                // backupJson tolerates missing keys (older backups → Kotlin
                // defaults) and unknown keys (newer backups → ignored). The
                // old Gson `normalized()` net is no longer needed because the
                // `= emptyList()` / field defaults on the models now actually
                // apply. See BackupSerializers + docs/backup-format.md.
                backupJson.decodeFromStream<PennyWiseBackup>(inputStream.buffered())
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
                database.recurringTransactionDao().deleteAll()
                database.goalDao().deleteAllGoals() // cascades goal_contributions
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
                    // Rebuilt from the shares that restore (#346), below.
                    lentSharesTotal = null,
                    groupId = tx.groupId?.takeIf { it in backupGroupIds },
                    profileId = resolveProfileId(tx.profileId)
                )

                // Import all data
                backup.database.categories.insertEachCounting({ skippedRows++ }) { category ->
                    database.categoryDao().insertCategory(category)
                    importedCategories++
                }

                // Backup id → inserted id, so goal contributions only keep a
                // link to a transaction that actually restored.
                val restoredTransactionIds = HashMap<Long, Long>()
                backup.database.transactions.insertEachCounting({ skippedRows++ }) { transaction ->
                    val newId = database.transactionDao().insertTransaction(cleanTransaction(transaction))
                    if (transaction.id != 0L && newId > 0L) restoredTransactionIds[transaction.id] = newId
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

                // Recurring / scheduled manual transaction templates (#706).
                // Standalone table (no foreign keys); ids are preserved in
                // REPLACE_ALL like every other cleared table, but the source
                // profile_id is remapped so materialized transactions land under
                // the right local profile. (Greptile)
                backup.database.recurringTransactions.insertEachCounting({ skippedRows++ }) { recurring ->
                    // Fall back to the default Personal profile (1) when the source
                    // profile can't be mapped, rather than keeping a stale/invalid id;
                    // give a fresh uid to any blank/legacy one.
                    database.recurringTransactionDao().insert(
                        recurring.copy(
                            uid = stableUid(recurring),
                            profileId = resolveProfileId(recurring.profileId) ?: 1L
                        )
                    )
                }

                // Goals (#914): ids preserved like every cleared table; goals go
                // before their contributions (FK). A link to a transaction that
                // didn't restore is cleared, keeping the money on the goal.
                backup.database.goals.insertEachCounting({ skippedRows++ }) { goal ->
                    database.goalDao().insertGoal(goal.copy(uid = goal.uid.ifBlank { java.util.UUID.randomUUID().toString() }))
                }
                backup.database.goalContributions.insertEachCounting({ skippedRows++ }) { c ->
                    database.goalDao().insertContribution(
                        c.copy(
                            uid = c.uid.ifBlank { java.util.UUID.randomUUID().toString() },
                            transactionId = c.transactionId?.let { restoredTransactionIds[it] }
                        )
                    )
                }

                // Split-bill shares (#346): loan ids are preserved here, transaction
                // ids go through the restored map; a share whose loan or bill
                // didn't restore is skipped. Then each bill's total is rebuilt.
                importLoanShares(backup.database.loanShares, { it.takeIf { id -> id in backupLoanIds } }, restoredTransactionIds) { skippedRows++ }

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

                val existingCategoryRows = database.categoryDao().getAllCategories().first()
                val existingCategories = existingCategoryRows.map { it.name }.toSet()

                // Import categories (merge by name). Rows get new local IDs, so a
                // sub-category's parent_id (#374) is remapped: parents go first and
                // old id → local id is tracked (an existing same-name category counts).
                val categoryIdMap = HashMap<Long, Long>()
                existingCategoryRows.forEach { local ->
                    backup.database.categories.firstOrNull { it.name == local.name }?.let { categoryIdMap[it.id] = local.id }
                }
                val (topLevel, children) = backup.database.categories.partition { it.parentId == null }
                (topLevel + children).insertEachCounting({ skippedRows++ }) { category ->
                    if (!existingCategories.contains(category.name)) {
                        // Generate new ID for imported category; parent remapped (or dropped if unknown).
                        // A built-in renamed in the backup ("Eating out" for "Food & Dining")
                        // whose built-in already exists here comes in as a plain user
                        // category, so two rows never claim the same built-in (#823).
                        val duplicatesBuiltin = category.builtinKey?.let { key ->
                            existingCategoryRows.any { it.builtinKey == key }
                        } == true
                        val newCategory = category.copy(
                            id = 0,
                            parentId = category.parentId?.let { categoryIdMap[it] },
                            isSystem = category.isSystem && !duplicatesBuiltin,
                            systemName = if (duplicatesBuiltin) null else category.systemName
                        )
                        val newId = database.categoryDao().insertCategory(newCategory)
                        categoryIdMap[category.id] = newId
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
                val insertedLoanIds = HashSet<Long>()
                backup.database.loans.insertEachCounting({ skippedRows++ }) { loan ->
                    val key = Triple(loan.personName, loan.direction, loan.createdAt)
                    val existingId = existingLoanKeyToId[key]
                    val newId = existingId
                        ?: database.loanDao().insertLoan(loan.copy(id = 0)).also { insertedLoanIds += it }
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
                            // Rebuilt from the shares that restore (#346).
                            lentSharesTotal = null,
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
                importRecurringTransactionsWithMerge(backup.database.recurringTransactions, { resolveProfileId(it) }) { skippedRows++ }
                importMerchantMappingsWithMerge(backup.database.merchantMappings) { skippedRows++ }
                importMerchantAliasesWithMerge(backup.database.merchantAliases) { skippedRows++ }
                importGoalsWithMerge(
                    backup.database.goals, backup.database.goalContributions, oldToNewTransactionIdMap
                ) { skippedRows++ }
                // Split-bill shares (#346): only onto a loan this merge inserted,
                // whose principal was sized with them; a matched local loan wins
                // as it is, like a local goal with the same uid, so a repeat
                // merge is a no-op. The bill may be new or local; see importLoanShares.
                importLoanShares(
                    backup.database.loanShares,
                    { old -> oldToNewLoanIdMap[old]?.takeIf { it in insertedLoanIds } },
                    oldToNewTransactionIdMap
                ) { skippedRows++ }

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
     * Import recurring / scheduled manual transaction templates with duplicate
     * checking (#706). The dedup key covers every schedule-distinguishing field
     * (merchant, amount, currency, category, type, frequency, day-of-month/week,
     * profile), so a repeat MERGE of the same backup doesn't stack duplicates
     * while genuinely distinct templates are never collapsed (Greptile). The
     * source profile_id is remapped so materialized transactions land under the
     * right local profile, and a fresh id avoids colliding with local rows.
     */
    /**
     * A stable uid for a template: the real one, or — for a blank/legacy uid —
     * a deterministic content-derived id so re-importing the same legacy row is
     * idempotent (same content → same uid → deduped), while distinct legacy rows
     * still get distinct uids. New templates always carry a real uid.
     */
    private fun stableUid(t: RecurringTransactionEntity): String = t.uid.ifEmpty {
        "legacy:" + listOf(
            t.merchantName,
            t.amount.stripTrailingZeros().toPlainString(),
            t.currency,
            t.category,
            t.transactionType,
            t.frequency,
            t.dayOfMonth,
            t.dayOfWeek,
            t.bankName,
            t.accountLast4,
            t.note,
            t.profileId
        ).joinToString("|")
    }

    private suspend fun importRecurringTransactionsWithMerge(
        recurring: List<RecurringTransactionEntity>,
        resolveProfileId: (Long?) -> Long?,
        onSkip: () -> Unit
    ) {
        // Dedup on the stable [RecurringTransactionEntity.uid], not on mutable
        // content: a repeated restore of the same backup can't duplicate a
        // template (same uid), while genuinely distinct templates always have
        // distinct uids and are always kept. A blank/legacy uid gets a fresh one
        // so it still inserts.
        val seenUids = database.recurringTransactionDao().getAll().first()
            .map { it.uid }.filter { it.isNotEmpty() }.toMutableSet()

        recurring.insertEachCounting(onSkip) { template ->
            val uid = stableUid(template)
            if (seenUids.add(uid)) {
                database.recurringTransactionDao().insert(
                    template.copy(
                        id = 0,
                        uid = uid,
                        // Fall back to the default Personal profile (1) when unmapped,
                        // so a template never restores under a stale/invalid id.
                        profileId = resolveProfileId(template.profileId) ?: 1L
                    )
                )
            }
        }
    }

    /**
     * Goals + contributions (#914). Goals dedup on their stable uid (a local
     * goal with the same uid wins, like other merges); contributions dedup on
     * theirs, so a repeat MERGE is idempotent while new contributions made on
     * another device still come in. goal_id is remapped to the local goal, and
     * transaction_id through [transactionIdMap] (new + duplicate-by-hash rows);
     * an unmapped link is cleared so the money stays on the goal. Finally each
     * touched transaction's shares are capped to its amount, since local and
     * backup contributions could now both claim it.
     *
     * A uid match whose currency differs (an empty goal may change currency on
     * one device) keeps the local goal, and that goal's backup contributions
     * are skipped and counted: amounts are never converted implicitly.
     */
    @androidx.annotation.VisibleForTesting
    internal suspend fun importGoalsWithMerge(
        goals: List<GoalEntity>,
        contributions: List<GoalContributionEntity>,
        transactionIdMap: Map<Long, Long>,
        onSkip: () -> Unit
    ) {
        if (goals.isEmpty() && contributions.isEmpty()) return
        val dao = database.goalDao()
        val localGoalByUid = dao.getAllGoals().associateBy { it.uid }.toMutableMap()
        val goalIdMap = HashMap<Long, Long>()
        val otherCurrencyGoalIds = HashSet<Long>()
        goals.insertEachCounting(onSkip) { goal ->
            val uid = goal.uid.ifBlank { java.util.UUID.randomUUID().toString() }
            val local = localGoalByUid[uid]
            when {
                local == null -> {
                    val newId = dao.insertGoal(goal.copy(id = 0, uid = uid))
                    localGoalByUid[uid] = goal.copy(id = newId, uid = uid)
                    goalIdMap[goal.id] = newId
                }
                local.currency == goal.currency -> goalIdMap[goal.id] = local.id
                else -> otherCurrencyGoalIds += goal.id
            }
        }

        val seenUids = dao.getAllContributions().mapTo(HashSet()) { it.uid }
        val touchedTransactions = HashSet<Long>()
        contributions.insertEachCounting(onSkip) { c ->
            check(c.goalId !in otherCurrencyGoalIds) { "Goal ${c.goalId} is in another currency on this device" }
            val goalId = checkNotNull(goalIdMap[c.goalId]) { "Goal ${c.goalId} not in backup" }
            val uid = c.uid.ifBlank { java.util.UUID.randomUUID().toString() }
            if (seenUids.add(uid)) {
                val transactionId = c.transactionId?.let { transactionIdMap[it] }
                dao.insertContribution(c.copy(id = 0, uid = uid, goalId = goalId, transactionId = transactionId))
                transactionId?.let(touchedTransactions::add)
            }
        }
        touchedTransactions.forEach { id ->
            val amount = database.transactionDao().getTransactionById(id)?.amount ?: return@forEach
            val capped = com.pennywiseai.tracker.data.repository.GoalRules.capContributions(
                dao.getContributionsForTransaction(id), amount, LocalDateTime.now()
            )
            if (capped.isNotEmpty()) dao.updateContributions(capped)
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
     * Split-bill shares (#346). A share is restored only onto a loan this
     * import inserted ([loanIdFor]; its principal was sized with the share)
     * and onto its bill ([transactionIdMap]: new, or an existing one with the
     * same hash) while that bill can still take it: an unlinked expense in the
     * loan's currency, room left in its amount, not already split with that
     * person, under the people limit. Otherwise it is skipped and counted.
     *
     * Loans are never changed here: a skipped share's debt stays on its
     * restored loan, as a loan-linked bill's does, so a restore can't erase
     * money someone owes. Every touched bill's total is then rebuilt.
     */
    private suspend fun importLoanShares(
        shares: List<LoanShareEntity>,
        loanIdFor: (Long) -> Long?,
        transactionIdMap: Map<Long, Long>,
        onSkip: () -> Unit
    ) {
        if (shares.isEmpty()) return
        val dao = database.loanDao()
        val touched = HashSet<Long>()
        shares.insertEachCounting(onSkip) { share ->
            val loanId = checkNotNull(loanIdFor(share.loanId)) { "Loan ${share.loanId} not restored" }
            val transactionId = checkNotNull(transactionIdMap[share.transactionId]) { "Transaction ${share.transactionId} not restored" }
            val bill = checkNotNull(database.transactionDao().getTransactionById(transactionId)) { "Transaction $transactionId missing" }
            val loan = checkNotNull(dao.getLoanById(loanId)) { "Loan $loanId missing" }
            check(bill.transactionType == TransactionType.EXPENSE && bill.loanId == null) { "Bill $transactionId can't be split" }
            check(bill.currency.equals(loan.currency, ignoreCase = true)) { "Share currency differs from its bill" }
            val existing = dao.getSharesForTransaction(transactionId)
            check(existing.size < com.pennywiseai.tracker.domain.loan.SplitRules.MAX_PEOPLE) { "Bill $transactionId is full" }
            check(existing.none { dao.getLoanById(it.loanId)?.personName == loan.personName }) { "Bill $transactionId is already split with that person" }
            val taken = existing.fold(java.math.BigDecimal.ZERO) { sum, s -> sum + s.amount }
            check(share.amount.signum() > 0 && taken + share.amount <= bill.amount) { "Shares would exceed bill $transactionId" }
            dao.insertShare(share.copy(id = 0, loanId = loanId, transactionId = transactionId))
            touched += transactionId
        }
        touched.forEach { database.transactionDao().syncLentSharesTotal(it) }
    }

    /**
     * Import user preferences
     */
    @androidx.annotation.VisibleForTesting
    internal suspend fun importPreferences(preferences: PreferencesSnapshot) {
        // Theme preferences
        preferences.theme.isDarkThemeEnabled?.let {
            userPreferencesRepository.updateDarkTheme(it)
        }
        userPreferencesRepository.updateDynamicColor(preferences.theme.isDynamicColorEnabled)
        preferences.theme.hiddenNavTabs?.let {
            userPreferencesRepository.updateHiddenNavTabs(it.toSet())
        }
        
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
        BankAccountMergeStore(context)
            .restore(preferences.app.bankAccountMerges)
        userPreferencesRepository.updateHasShownScanTutorial(preferences.app.hasShownScanTutorial)
        preferences.app.firstLaunchTime?.let {
            userPreferencesRepository.updateFirstLaunchTime(it)
        }
        userPreferencesRepository.updateHasShownReviewPrompt(preferences.app.hasShownReviewPrompt)
        // Additive: a restore can start ignoring an account but never silently
        // un-ignores one the user set up on this device (#826).
        if (preferences.app.ignoredAccounts.isNotEmpty()) {
            ignoredAccountsStore.replaceAll(
                ignoredAccountsStore.keys() + preferences.app.ignoredAccounts
            )
        }
        preferences.app.lastReviewPromptTime?.let {
            userPreferencesRepository.updateLastReviewPromptTime(it)
        }

        // Like the license: fills in a missing UPI ID, never replaces one set here (#346).
        preferences.app.upiId?.takeIf { com.pennywiseai.tracker.domain.loan.UpiRequest.isValidUpiId(it) }?.let { id ->
            if (userPreferencesRepository.upiId.first() == null) userPreferencesRepository.setUpiId(id)
        }

        // A license in the backup only overrides when this install has none —
        // never clobber a key the user already activated here.
        preferences.app.licenseKey?.let { key ->
            if (userPreferencesRepository.storedLicense.first() == null) {
                licenseManager.restore(key, preferences.app.licenseInstanceId)
            }
        }
    }
}
