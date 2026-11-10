package com.pennywiseai.tracker.domain.model

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity

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
    return transactions.filter { tx ->
        // Explicit override > account inheritance > default Personal
        val effectiveProfileId = tx.profileId ?: run {
            if (tx.bankName != null && tx.accountNumber != null) {
                val key = "${tx.bankName}_${tx.accountNumber}"
                profileAccountKeys.entries.firstOrNull { (_, keys) -> keys.contains(key) }?.key
            } else null
        } ?: ProfileEntity.PERSONAL_ID
        effectiveProfileId == selectedProfileId
    }
}

/**
 * Builds a map of profileId → set of "bankName_accountLast4" keys from account balances.
 */
fun buildProfileAccountKeys(accounts: List<AccountBalanceEntity>): Map<Long, Set<String>> {
    return accounts.groupBy { it.profileId }
        .mapValues { (_, accs) -> accs.map { "${it.bankName}_${it.accountLast4}" }.toSet() }
}
