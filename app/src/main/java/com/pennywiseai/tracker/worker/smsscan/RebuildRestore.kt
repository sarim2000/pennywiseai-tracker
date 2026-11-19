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
 *
 * Each repair runs on its own, so one that throws doesn't skip the rest. The scan's own
 * error is what throws, with any repair failure attached; a scan that returned throws the
 * repair failure instead, so an incomplete repair never passes for a clean scan.
 */
internal suspend fun <T> restoringAccountsAfterRebuild(
    accounts: AccountBalanceRepository,
    typedBeforeRebuild: Collection<AccountBalanceEntity>,
    latestBeforeRebuild: List<AccountBalanceEntity>,
    rebuilt: Boolean,
    scan: suspend () -> T,
): T {
    val result = try {
        scan()
    } catch (e: Throwable) {
        restoreAccounts(accounts, typedBeforeRebuild, latestBeforeRebuild, rebuilt)?.let(e::addSuppressed)
        throw e
    }
    restoreAccounts(accounts, typedBeforeRebuild, latestBeforeRebuild, rebuilt)?.let { throw it }
    return result
}

/** Runs every repair, even after one throws; returns the first failure, the others attached. */
private suspend fun restoreAccounts(
    accounts: AccountBalanceRepository,
    typedBeforeRebuild: Collection<AccountBalanceEntity>,
    latestBeforeRebuild: List<AccountBalanceEntity>,
    rebuilt: Boolean,
): Exception? = withContext(NonCancellable) {
    var failure: Exception? = null
    suspend fun repair(step: suspend () -> Unit) = try {
        step()
    } catch (e: Exception) {
        failure?.addSuppressed(e) ?: run { failure = e }
    }
    // Rows rebuilt from balance-only SMS don't pass through processBalanceUpdate,
    // so put each typed account's type back on the rows that lost it (#792).
    for (row in typedBeforeRebuild) repair { accounts.restoreAccountType(row) }
    // Likewise the statement and due day and card bill the user set.
    for (row in latestBeforeRebuild) repair { accounts.keepAccountFields(row) }
    // A rebuild drops ATM transfers into Cash wallets (#1062) and may not route
    // them again (setting since turned off), so re-derive every Cash wallet.
    if (rebuilt) {
        repair {
            for (wallet in accounts.getAllLatestBalancesOnce().cashWallets()) {
                repair { accounts.recomputeManualBalance(wallet.bankName, wallet.accountLast4) }
            }
        }
    }
    failure
}
