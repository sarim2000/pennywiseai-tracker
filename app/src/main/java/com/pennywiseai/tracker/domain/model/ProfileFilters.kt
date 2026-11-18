package com.pennywiseai.tracker.domain.model

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore

/**
 * Filters transactions by the selected profile.
 *
 * A transaction's effective profile is:
 *   - [TransactionEntity.profileId] if explicitly set
 *   - otherwise inherited from the account it belongs to (looked up via [profileAccountKeys])
 *
 * @param selectedProfileId null means "All profiles" (no filtering)
 * @param profileAccountKeys map of profileId → set of "bankName_accountLast4" keys
 */
fun filterTransactionsByProfile(
    transactions: List<TransactionEntity>,
    selectedProfileId: Long?,
    profileAccountKeys: Map<Long, Set<String>>
): List<TransactionEntity> {
    if (selectedProfileId == null) return transactions
    return transactions.filter { tx -> tx.effectiveProfileId(profileAccountKeys) == selectedProfileId }
}

/**
 * The rows a list or total counts: [profileId]'s (null for all), and none from an ignored
 * account (#826). [ignoredAccountKeys] should already hold those accounts' linked cards
 * ([IgnoredAccountsStore.withLinkedCards]). Home, the chat and the Transactions list share it.
 */
fun TransactionEntity.isTracked(
    profileId: Long?,
    ignoredAccountKeys: Set<String>,
    profileAccountKeys: Map<Long, Set<String>>,
): Boolean =
    // Checked apart from the profile: "All profiles" keeps everything and an unattributed
    // row falls back to Personal, so the profile rule can't carry the exclusion.
    !IgnoredAccountsStore.isIgnored(ignoredAccountKeys, bankName, accountNumber) &&
        (profileId == null || effectiveProfileId(profileAccountKeys) == profileId)

/** [transactions] that are [isTracked]. */
fun filterTrackedByProfile(
    transactions: List<TransactionEntity>,
    profileId: Long?,
    ignoredAccountKeys: Set<String>,
    profileAccountKeys: Map<Long, Set<String>>,
): List<TransactionEntity> = transactions.filter { it.isTracked(profileId, ignoredAccountKeys, profileAccountKeys) }

/** Explicit override > account inheritance > default Personal. */
fun TransactionEntity.effectiveProfileId(profileAccountKeys: Map<Long, Set<String>>): Long =
    profileId ?: run {
        if (bankName != null && accountNumber != null) {
            val key = "${bankName}_$accountNumber"
            profileAccountKeys.entries.firstOrNull { (_, keys) -> keys.contains(key) }?.key
        } else null
    } ?: ProfileEntity.PERSONAL_ID

/**
 * Builds a map of profileId → set of "bankName_accountLast4" keys from account balances.
 */
fun buildProfileAccountKeys(accounts: List<AccountBalanceEntity>): Map<Long, Set<String>> {
    return accounts.groupBy { it.profileId }
        .mapValues { (_, accs) -> accs.map { "${it.bankName}_${it.accountLast4}" }.toSet() }
}
