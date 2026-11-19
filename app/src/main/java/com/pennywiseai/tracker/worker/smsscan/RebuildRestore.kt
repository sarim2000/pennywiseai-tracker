package com.pennywiseai.tracker.worker.smsscan

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.ledger.cashWallets
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Runs [scan], then puts back on the rebuilt balance rows what a force resync deleted, even
 * when [scan] throws or is cancelled: the retry scan isn't a rebuild and has no snapshot,
 * so what isn't restored now is gone. The helpers only fill missing fields, so a scan that
 * ended part-way is safe to restore after. With no rebuild, both lists are empty.
 */
internal suspend fun <T> restoringAccountsAfterRebuild(
    accounts: AccountBalanceRepository,
    typedBeforeRebuild: Collection<AccountBalanceEntity>,
    latestBeforeRebuild: List<AccountBalanceEntity>,
    rebuilt: Boolean,
    scan: suspend () -> T,
): T = try {
    scan()
} finally {
    withContext(NonCancellable) {
        // Rows rebuilt from balance-only SMS don't pass through processBalanceUpdate,
        // so put each typed account's type back on the rows that lost it (#792).
        for (row in typedBeforeRebuild) accounts.restoreAccountType(row)
        // Likewise the statement and due day and card bill the user set.
        for (row in latestBeforeRebuild) accounts.keepAccountFields(row)
        // A rebuild drops ATM transfers into Cash wallets (#1062) and may not route
        // them again (setting since turned off), so re-derive every Cash wallet.
        if (rebuilt) {
            accounts.getAllLatestBalancesOnce().cashWallets().forEach {
                accounts.recomputeManualBalance(it.bankName, it.accountLast4)
            }
        }
    }
}
