package com.pennywiseai.tracker.data.repository

import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.utils.BalanceCalculator
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class AccountBalanceRepository @Inject constructor(
    private val accountBalanceDao: AccountBalanceDao,
    private val transactionDao: TransactionDao,
    private val database: PennyWiseDatabase
) {
    companion object {
        const val SOURCE_OPENING = "OPENING"
        const val SOURCE_MANUAL = "MANUAL"
    }
    
    suspend fun insertBalance(balance: AccountBalanceEntity): Long {
        return accountBalanceDao.insertBalance(balance)
    }
    
    suspend fun getLatestBalance(bankName: String, accountLast4: String): AccountBalanceEntity? {
        return accountBalanceDao.getLatestBalance(bankName, accountLast4)
    }

    /**
     * Resolves an account when only the last-4 digits are known — used by the
     * TRANSFER balance-update path where `from_account` / `to_account` only
     * persist the last4. See AccountBalanceDao.getLatestBalanceByLast4.
     */
    suspend fun getLatestBalanceByLast4(accountLast4: String): AccountBalanceEntity? {
        return accountBalanceDao.getLatestBalanceByLast4(accountLast4)
    }
    
    fun getLatestBalanceFlow(bankName: String, accountLast4: String): Flow<AccountBalanceEntity?> {
        return accountBalanceDao.getLatestBalanceFlow(bankName, accountLast4)
    }
    
    fun getAllLatestBalances(): Flow<List<AccountBalanceEntity>> {
        return accountBalanceDao.getAllLatestBalances()
    }
    
    fun getTotalBalance(): Flow<BigDecimal?> {
        return accountBalanceDao.getTotalBalance()
    }
    
    fun getBalanceHistory(
        bankName: String,
        accountLast4: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Flow<List<AccountBalanceEntity>> {
        return accountBalanceDao.getBalanceHistory(bankName, accountLast4, startDate, endDate)
    }
    
    fun getAccountCount(): Flow<Int> {
        return accountBalanceDao.getAccountCount()
    }
    
    suspend fun deleteOldBalances(beforeDate: LocalDateTime): Int {
        return accountBalanceDao.deleteOldBalances(beforeDate)
    }
    
    suspend fun updateBalance(balance: AccountBalanceEntity) {
        accountBalanceDao.updateBalance(balance)
    }
    
    suspend fun deleteBalance(balance: AccountBalanceEntity) {
        accountBalanceDao.deleteBalance(balance)
    }
    
    /**
     * Inserts a balance record from a transaction if it has balance information.
     * Preserves the existing account's profileId if one exists.
     */
    suspend fun insertBalanceFromTransaction(
        bankName: String?,
        accountLast4: String?,
        balance: BigDecimal?,
        creditLimit: BigDecimal? = null,
        timestamp: LocalDateTime,
        transactionId: Long?,
        isCreditCard: Boolean = false
    ) {
        if (bankName != null && accountLast4 != null && (balance != null || creditLimit != null)) {
            val existing = getLatestBalance(bankName, accountLast4)
            val balanceEntity = AccountBalanceEntity(
                bankName = bankName,
                accountLast4 = accountLast4,
                balance = balance ?: BigDecimal.ZERO,
                timestamp = timestamp,
                transactionId = transactionId,
                creditLimit = creditLimit,
                isCreditCard = isCreditCard,
                profileId = existing?.profileId ?: ProfileEntity.PERSONAL_ID,
                alias = existing?.alias,
                lowBalanceThreshold = existing?.lowBalanceThreshold
            )
            insertBalance(balanceEntity)
        }
    }

    /**
     * Inserts a balance update from a balance notification SMS.
     * Preserves the existing account's profileId if one exists.
     */
    suspend fun insertBalanceUpdate(
        bankName: String,
        accountLast4: String,
        balance: BigDecimal,
        timestamp: LocalDateTime,
        smsSource: String? = null,
        sourceType: String? = null,
        currency: String = "INR"
    ): Long {
        val existing = getLatestBalance(bankName, accountLast4)
        val balanceEntity = AccountBalanceEntity(
            bankName = bankName,
            accountLast4 = accountLast4,
            balance = balance,
            timestamp = timestamp,
            transactionId = null,
            smsSource = smsSource?.take(500),  // Limit to 500 chars
            sourceType = sourceType,
            currency = currency,
            profileId = existing?.profileId ?: ProfileEntity.PERSONAL_ID,
            alias = existing?.alias,
            lowBalanceThreshold = existing?.lowBalanceThreshold
        )
        return insertBalance(balanceEntity)
    }
    
    suspend fun getBalanceHistoryForAccount(bankName: String, accountLast4: String): List<AccountBalanceEntity> {
        return accountBalanceDao.getBalanceHistoryForAccount(bankName, accountLast4)
    }
    
    suspend fun deleteBalanceById(id: Long) {
        accountBalanceDao.deleteBalanceById(id)
    }

    suspend fun deleteBalancesForTransaction(transactionId: Long): Int {
        return accountBalanceDao.deleteBalancesForTransaction(transactionId)
    }
    
    suspend fun updateBalanceById(id: Long, newBalance: BigDecimal) {
        accountBalanceDao.updateBalanceById(id, newBalance)
    }
    
    suspend fun getBalanceCountForAccount(bankName: String, accountLast4: String): Int {
        return accountBalanceDao.getBalanceCountForAccount(bankName, accountLast4)
    }

    fun getBalancesFromDate(startDate: LocalDateTime): Flow<List<AccountBalanceEntity>> {
        return accountBalanceDao.getBalancesFromDate(startDate)
    }

    suspend fun deleteAccount(bankName: String, accountLast4: String): Int {
        return accountBalanceDao.deleteAccount(bankName, accountLast4)
    }

    suspend fun updateAccountBankName(oldBankName: String, accountLast4: String, newBankName: String): Int {
        return accountBalanceDao.updateAccountBankName(oldBankName, accountLast4, newBankName)
    }

    suspend fun updateStatementDay(bankName: String, accountLast4: String, statementDay: Int?): Int {
        return accountBalanceDao.updateStatementDay(bankName, accountLast4, statementDay)
    }

    suspend fun deleteAllBalances() {
        accountBalanceDao.deleteAllBalances()
    }

    /** See [AccountBalanceDao.deleteRebuildableBalances]. */
    suspend fun deleteRebuildableBalances() {
        accountBalanceDao.deleteRebuildableBalances()
    }

    suspend fun setAccountProfile(bankName: String, accountLast4: String, profileId: Long): Int {
        return accountBalanceDao.setAccountProfile(bankName, accountLast4, profileId)
    }

    suspend fun setAccountAlias(bankName: String, accountLast4: String, alias: String?): Int {
        return accountBalanceDao.setAccountAlias(bankName, accountLast4, alias)
    }

    /**
     * Sets (or clears, with null) the per-account low-balance alert threshold
     * across all of the account's balance rows. (#509)
     */
    suspend fun setLowBalanceThreshold(bankName: String, accountLast4: String, threshold: BigDecimal?): Int {
        return accountBalanceDao.setLowBalanceThreshold(bankName, accountLast4, threshold)
    }

    suspend fun updateAccountCurrency(bankName: String, accountLast4: String, currency: String): Int {
        return accountBalanceDao.updateAccountCurrency(bankName, accountLast4, currency)
    }

    /**
     * Atomically shift account balances to reflect replacing [original] with
     * [updated] — the single owner of "what does this edit do to balances".
     * Pass `updated = null` for a deletion. Wrapped in a Room transaction so a
     * crash mid-sequence can't leave accounts half-reverted.
     *
     * Covers every shape of change: type or amount edits on the same account
     * (netted into one corrective row), account moves (revert the old account,
     * apply to the new), and TRANSFER conversions in either direction (legs
     * reverted/applied per account, credit-card aware). Manual/cash accounts are
     * skipped for the assigned account — their balance is *derived*, so callers
     * recompute them — while manual transfer *legs* are recomputed here, since
     * no caller tracks leg accounts.
     *
     * Corrective rows are stamped with the current time, NOT the edited
     * transaction's dateTime: "latest balance" is resolved by `ORDER BY
     * timestamp DESC`, so a correction stamped into the past (any edit of a
     * transaction older than the account's newest balance row — the common
     * case) would never become the latest row and would be invisible. A
     * correction is a now-event — we learned about it now, and it adjusts the
     * current figure. Effects only ever layer onto an account's *latest* row;
     * the next SMS that reports an explicit balance re-anchors the account to
     * the bank's own figure regardless (#636).
     */
    suspend fun applyTransactionBalanceShift(
        original: TransactionEntity?,
        updated: TransactionEntity?
    ) {
        database.withTransaction {
            val origKey = singleAccountKey(original)
            val updKey = singleAccountKey(updated)

            // Same-account non-transfer edit: one net corrective row instead of a
            // revert/apply pair, so the history doesn't grow two entries per edit.
            if (origKey != null && origKey == updKey) {
                val (bank, acct) = origKey
                if (!isManualAccount(bank, acct)) {
                    val latest = getLatestBalance(bank, acct)
                    if (latest != null) {
                        val net = BalanceCalculator.signedBalanceEffect(
                            latest.isCreditCard, updated!!.transactionType, updated.amount
                        ) - BalanceCalculator.signedBalanceEffect(
                            latest.isCreditCard, original!!.transactionType, original.amount
                        )
                        if (net.signum() != 0) {
                            insertBalanceDelta(bank, acct, net, LocalDateTime.now(), updated.id)
                        }
                    }
                }
                return@withTransaction
            }

            if (original != null) {
                if (original.transactionType == TransactionType.TRANSFER) {
                    shiftTransferLeg(original.fromAccount, incoming = false, revert = true,
                        original.amount, updated?.id)
                    shiftTransferLeg(original.toAccount, incoming = true, revert = true,
                        original.amount, updated?.id)
                } else if (origKey != null) {
                    shiftSingleAccount(origKey, original.transactionType, original.amount,
                        revert = true, updated?.id)
                }
            }
            if (updated != null) {
                if (updated.transactionType == TransactionType.TRANSFER) {
                    shiftTransferLeg(updated.fromAccount, incoming = false, revert = false,
                        updated.amount, updated.id)
                    shiftTransferLeg(updated.toAccount, incoming = true, revert = false,
                        updated.amount, updated.id)
                } else if (updKey != null) {
                    shiftSingleAccount(updKey, updated.transactionType, updated.amount,
                        revert = false, updated.id)
                }
            }
        }
    }

    /**
     * Reflects a newly-inserted transaction ([transactionId]) on
     * its account's running balance. Manual/cash accounts are recomputed from
     * their transactions (so back-dated or later-edited rows stay correct); an
     * SMS-tracked account gets a signed "TRANSACTION" delta snapshot off its
     * latest row. No-op when the account has no balance row yet (an SMS account
     * we've never seen a balance for) — there's no anchor to move.
     *
     * The transaction MUST already be persisted before calling this. Callers
     * that support manual accounts must also call [ensureManualOpening] BEFORE
     * inserting the transaction, so the opening anchor is derived from the
     * pre-insert balance. Shared by manual add (AddTransactionUseCase),
     * subscription mark-as-paid and income-autopay (#570) so all three obey one
     * balance rule.
     */
    suspend fun applyTransactionToBalance(
        bankName: String,
        accountLast4: String,
        amount: BigDecimal,
        type: TransactionType,
        transactionId: Long
    ) {
        if (isManualAccount(bankName, accountLast4)) {
            recomputeManualBalance(bankName, accountLast4)
            return
        }
        val currentAccount = getLatestBalance(bankName, accountLast4) ?: return
        // Credit-card aware (#636): a purchase on a card must *raise* its
        // outstanding, which the old debit-only formula got backwards.
        val effect = BalanceCalculator.signedBalanceEffect(currentAccount.isCreditCard, type, amount)
        if (effect.signum() == 0) return
        insertBalance(
            currentAccount.copy(
                id = 0,
                balance = currentAccount.balance + effect,
                // Stamped now, not [date]: a back-dated add stamped into the past
                // would lose the ORDER BY timestamp DESC race to the row it was
                // computed from and never become the visible balance.
                timestamp = LocalDateTime.now(),
                transactionId = transactionId,
                sourceType = "TRANSACTION",
                smsSource = null
            )
        )
    }

    /**
     * Atomically insert [transaction] and reflect it on its account's balance,
     * so a crash can't leave the transaction persisted (its dedup hash then
     * blocking any retry) while the balance was never moved — a gap that would
     * permanently short a hash-guarded autopay/mark-paid cycle. Wraps the
     * opening-anchor pin, the insert, and [applyTransactionToBalance] in one
     * Room transaction. Returns the new row id (-1 on a dedup-conflict insert).
     * Pass a null account to insert with no balance side effect.
     */
    suspend fun insertTransactionWithBalance(
        transaction: TransactionEntity,
        bankName: String?,
        accountLast4: String?
    ): Long = database.withTransaction {
        if (bankName != null && accountLast4 != null) {
            ensureManualOpening(bankName, accountLast4)
        }
        val rowId = transactionDao.insertTransaction(transaction)
        if (rowId != -1L && bankName != null && accountLast4 != null) {
            applyTransactionToBalance(
                bankName = bankName,
                accountLast4 = accountLast4,
                amount = transaction.amount,
                type = transaction.transactionType,
                transactionId = rowId
            )
        }
        rowId
    }

    /**
     * Atomically insert a manual TRANSFER [transaction] and move BOTH legs'
     * balances as one Room transaction, so a crash can never leave the money
     * debited from one account but never credited to the other (or vice-versa).
     * The [transaction] must already carry `transactionType = TRANSFER`,
     * `fromAccount = fromLast4` and `toAccount = toLast4`.
     *
     * The two legs use different balance mechanics, mirroring the rest of this
     * class:
     *  - Manual/cash accounts have a *derived* balance (opening + Σtxns, with
     *    TRANSFER handled by `from_account`/`to_account` in [signedTransactionSum]).
     *    Once the row is inserted, [recomputeManualBalance] re-derives the figure —
     *    no explicit delta needed. Opening anchors for both legs are pinned from
     *    the PRE-insert snapshot via [ensureManualOpening] before the insert, so
     *    the recompute includes this new transfer exactly once.
     *  - SMS-tracked accounts have a bank-reported balance, so we snapshot a signed
     *    "TRANSACTION" delta off the latest row via [insertBalanceDelta]
     *    (money out on the FROM leg, in on the TO leg, sign-flipped for credit
     *    cards). No-op if that account has no balance row yet.
     *
     * Returns the new row id (-1 on a dedup-conflict insert, in which case no
     * balance is moved).
     */
    suspend fun insertTransferWithBalance(
        transaction: TransactionEntity,
        fromBankName: String,
        fromLast4: String,
        toBankName: String,
        toLast4: String
    ): Long = database.withTransaction {
        // Pin opening anchors from the PRE-insert snapshot for manual accounts (both legs).
        ensureManualOpening(fromBankName, fromLast4)
        ensureManualOpening(toBankName, toLast4)
        val rowId = transactionDao.insertTransaction(transaction)
        if (rowId != -1L) {
            // FROM leg: money out. Bank-aware so a shared last4 (Kotak ••9999 vs
            // HDFC ••9999) debits the account the user actually picked. Legs are
            // credit-card aware: money arriving at a card pays down outstanding.
            if (isManualAccount(fromBankName, fromLast4)) {
                recomputeManualBalance(fromBankName, fromLast4)
            } else {
                getLatestBalance(fromBankName, fromLast4)?.let { latest ->
                    val effect = BalanceCalculator.transferLegEffect(latest.isCreditCard, incoming = false, transaction.amount)
                    // now() not dateTime: a back-dated leg must still postdate
                    // the latest row to become the visible balance.
                    insertBalanceDelta(fromBankName, fromLast4, effect, LocalDateTime.now(), rowId)
                }
            }
            // TO leg: money in.
            if (isManualAccount(toBankName, toLast4)) {
                recomputeManualBalance(toBankName, toLast4)
            } else {
                getLatestBalance(toBankName, toLast4)?.let { latest ->
                    val effect = BalanceCalculator.transferLegEffect(latest.isCreditCard, incoming = true, transaction.amount)
                    insertBalanceDelta(toBankName, toLast4, effect, LocalDateTime.now(), rowId)
                }
            }
        }
        rowId
    }

    /**
     * Adjusts running account balances when [transaction] is deleted
     * across manual, credit-card, SMS-tracked, and transfer accounts.
     */
    open suspend fun applyDeleteBalanceShift(transaction: TransactionEntity) {
        val bank = transaction.bankName
        val acct = transaction.accountNumber
        if (bank != null && acct != null) {
            ensureManualOpening(bank, acct)
        }
        applyTransactionBalanceShift(
            original = transaction,
            updated = null
        )
        if (bank != null && acct != null) {
            recomputeManualBalance(bank, acct)
        }
    }

    /**
     * Adjusts running account balances when [transaction] is restored / undeleted.
     */
    open suspend fun applyRestoreBalanceShift(transaction: TransactionEntity) {
        val bank = transaction.bankName
        val acct = transaction.accountNumber
        if (bank != null && acct != null) {
            ensureManualOpening(bank, acct)
        }
        applyTransactionBalanceShift(
            original = null,
            updated = transaction
        )
        if (bank != null && acct != null) {
            recomputeManualBalance(bank, acct)
        }
    }

    /**
     * The (bankName, accountLast4) pair a non-TRANSFER transaction's balance
     * effect lands on, or null when there is nothing to shift (no account, or a
     * TRANSFER — whose effects travel through its legs instead).
     */
    private fun singleAccountKey(txn: TransactionEntity?): Pair<String, String>? {
        if (txn == null || txn.transactionType == TransactionType.TRANSFER) return null
        val bank = txn.bankName ?: return null
        val acct = txn.accountNumber ?: return null
        return bank to acct
    }

    /**
     * Applies (or, with [revert], undoes) a non-TRANSFER transaction's effect on
     * its account as a delta off the latest row. Skips manual accounts (derived
     * balance — callers recompute) and accounts with no balance row (no anchor).
     */
    private suspend fun shiftSingleAccount(
        key: Pair<String, String>,
        type: TransactionType,
        amount: BigDecimal,
        revert: Boolean,
        transactionId: Long?
    ) {
        val (bank, acct) = key
        if (isManualAccount(bank, acct)) return
        val latest = getLatestBalance(bank, acct) ?: return
        var effect = BalanceCalculator.signedBalanceEffect(latest.isCreditCard, type, amount)
        if (revert) effect = effect.negate()
        if (effect.signum() == 0) return
        insertBalanceDelta(bank, acct, effect, LocalDateTime.now(), transactionId)
    }

    /**
     * Applies (or, with [revert], undoes) one TRANSFER leg's effect. Resolved by
     * last4 alone — `from_account`/`to_account` only persist the last4. Manual
     * leg accounts are re-derived instead of getting a delta row (their MANUAL
     * row would fight it); an account with no anchor yet stays untouched, in
     * line with the rest of this class. Credit-card legs flip sign — money
     * arriving at a card pays down outstanding.
     */
    private suspend fun shiftTransferLeg(
        accountLast4: String?,
        incoming: Boolean,
        revert: Boolean,
        amount: BigDecimal,
        transactionId: Long?
    ) {
        if (accountLast4 == null) return
        val latest = accountBalanceDao.getLatestBalanceByLast4(accountLast4) ?: return
        if (isManualAccount(latest.bankName, latest.accountLast4)) {
            ensureManualOpening(latest.bankName, latest.accountLast4)
            recomputeManualBalance(latest.bankName, latest.accountLast4)
            return
        }
        var effect = BalanceCalculator.transferLegEffect(latest.isCreditCard, incoming, amount)
        if (revert) effect = effect.negate()
        if (effect.signum() == 0) return
        accountBalanceDao.insertBalance(
            latest.copy(
                id = 0,
                balance = latest.balance + effect,
                timestamp = LocalDateTime.now(),
                transactionId = transactionId,
                sourceType = "TRANSACTION",
                smsSource = null
            )
        )
    }

    /**
     * Inserts a signed delta snapshot off an account's latest balance row,
     * resolved by (bankName, last4) so an effect can't be misattributed to a
     * *different* account that merely shares the same last4 (e.g. Kotak ••9999
     * vs HDFC ••9999). No-op if that account has no balance row yet.
     */
    private suspend fun insertBalanceDelta(
        bankName: String,
        accountLast4: String,
        delta: BigDecimal,
        timestamp: LocalDateTime,
        transactionId: Long?
    ) {
        val latest = accountBalanceDao.getLatestBalance(bankName, accountLast4) ?: return
        accountBalanceDao.insertBalance(
            latest.copy(
                id = 0,
                balance = latest.balance + delta,
                timestamp = timestamp,
                transactionId = transactionId,
                sourceType = "TRANSACTION",
                smsSource = null
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Manual / cash account balance = opening + Σ(its transactions).
    //
    // SMS-tracked accounts get their balance from the bank (each SMS reports the
    // real figure), so these helpers only ever touch accounts the app recognises
    // as manual. A manual account stores a stable OPENING row (at an early
    // timestamp, so it never wins "latest by timestamp") plus a single MANUAL row
    // that [recomputeManualBalance] keeps equal to opening + Σ(transactions).
    // Because the balance is *derived*, retroactive changes — back-dated adds,
    // deletes, edits — are handled by simply recomputing. (#469 / #470)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * An account is "manual" if it has an OPENING anchor, or (for accounts created
     * before this model existed) its latest balance row is user-entered (MANUAL).
     * SMS-tracked accounts never match.
     */
    suspend fun isManualAccount(bankName: String, accountLast4: String): Boolean {
        // Credit cards are excluded — their balance is outstanding owed (spending
        // increases it), which the income-positive recompute would get backwards.
        accountBalanceDao.getOpeningRow(bankName, accountLast4)?.let { return !it.isCreditCard }
        val latest = accountBalanceDao.getLatestBalance(bankName, accountLast4) ?: return false
        if (latest.sourceType != SOURCE_MANUAL || latest.isCreditCard) return false
        // Legacy bridge for accounts created before the OPENING model: only treat them
        // as manual if they have no SMS-sourced history. Without this, an SMS-tracked
        // account that merely had a one-off "Update balance" override (latest row MANUAL)
        // would be permanently reclassified as manual on its first write. (Greptile #487)
        return accountBalanceDao.countSmsSourcedBalances(bankName, accountLast4) == 0
    }

    /**
     * Σ of an account's transactions, signed the way balances move. Direct transactions
     * (assigned via `account_number`): INCOME +, everything else −. TRANSFERs are handled
     * separately by their `from_account`/`to_account` (money in when this account is the
     * destination, out when it's the source) and excluded from the direct sum so they're
     * never double-counted — a transfer *into* a manual account would otherwise be dropped.
     */
    private suspend fun signedTransactionSum(bankName: String, accountLast4: String): BigDecimal {
        var sum = transactionDao.getTransactionsForAccountStrict(bankName, accountLast4)
            .filter { it.transactionType != TransactionType.TRANSFER }
            .fold(BigDecimal.ZERO) { acc, tx ->
                if (tx.transactionType == TransactionType.INCOME) acc + tx.amount else acc - tx.amount
            }
        for (tx in transactionDao.getTransfersForAccount(accountLast4)) {
            if (tx.toAccount == accountLast4) sum += tx.amount
            if (tx.fromAccount == accountLast4) sum -= tx.amount
        }
        return sum
    }

    /**
     * Ensures a manual account has an OPENING anchor, deriving it once so the
     * currently-displayed balance is preserved: opening = currentBalance − Σtxns.
     * No-op if an anchor already exists or the account has no balance row yet.
     */
    suspend fun ensureManualOpening(bankName: String, accountLast4: String) {
        if (accountBalanceDao.getOpeningRow(bankName, accountLast4) != null) return
        if (!isManualAccount(bankName, accountLast4)) return
        database.withTransaction {
            // Re-check inside the transaction in case a concurrent call just created it.
            if (accountBalanceDao.getOpeningRow(bankName, accountLast4) != null) return@withTransaction
            val latest = accountBalanceDao.getLatestBalance(bankName, accountLast4) ?: return@withTransaction
            val sum = signedTransactionSum(bankName, accountLast4)
            val earliest = accountBalanceDao.getEarliestBalanceTimestamp(bankName, accountLast4)
                ?: latest.timestamp
            accountBalanceDao.insertBalance(
                latest.copy(
                    id = 0,
                    balance = latest.balance - sum,
                    timestamp = earliest.minusNanos(1),  // strictly before all history → never "latest"
                    transactionId = null,
                    sourceType = SOURCE_OPENING,
                    smsSource = null
                )
            )
        }
    }

    /**
     * Recomputes a manual account's displayed balance as opening + Σ(transactions)
     * and writes it to the single MANUAL row (refreshed to "now" so it wins as the
     * latest). Safe to call after any add / edit / delete of the account's
     * transactions. No-op for non-manual accounts.
     */
    suspend fun recomputeManualBalance(bankName: String, accountLast4: String) {
        if (!isManualAccount(bankName, accountLast4)) return
        database.withTransaction {
            ensureManualOpening(bankName, accountLast4)
            val opening = accountBalanceDao.getOpeningRow(bankName, accountLast4) ?: return@withTransaction
            val current = opening.balance + signedTransactionSum(bankName, accountLast4)
            writeManualCurrentRow(bankName, accountLast4, opening, current)
        }
    }

    /**
     * "Update balance" for a manual account (option-b semantics): the user types
     * their *current* balance and we back-solve the opening (opening = target −
     * Σtxns) so future transactions still adjust from there.
     */
    suspend fun setManualCurrentBalance(bankName: String, accountLast4: String, target: BigDecimal) {
        if (!isManualAccount(bankName, accountLast4)) return
        database.withTransaction {
            ensureManualOpening(bankName, accountLast4)
            val opening = accountBalanceDao.getOpeningRow(bankName, accountLast4) ?: return@withTransaction
            val sum = signedTransactionSum(bankName, accountLast4)
            accountBalanceDao.updateBalanceById(opening.id, target - sum)
            // current = (target − sum) + sum = target — write it directly instead of
            // re-summing via recomputeManualBalance.
            writeManualCurrentRow(bankName, accountLast4, opening, target)
        }
    }

    /**
     * Atomically applies the account-edit dialog's currency + balance changes for a
     * manual account, so a failure can't leave the currency updated but the opening
     * un-back-solved (a stale balance until the next mutation). (Greptile #487)
     */
    suspend fun updateManualBalanceAndCurrency(
        bankName: String,
        accountLast4: String,
        currency: String,
        targetBalance: BigDecimal
    ) {
        database.withTransaction {
            accountBalanceDao.updateAccountCurrency(bankName, accountLast4, currency)
            setManualCurrentBalance(bankName, accountLast4, targetBalance)
        }
    }

    /** Upserts the single MANUAL "current" row at `now` so it wins as the latest. */
    private suspend fun writeManualCurrentRow(
        bankName: String,
        accountLast4: String,
        opening: AccountBalanceEntity,
        current: BigDecimal
    ) {
        val now = LocalDateTime.now()
        val existing = accountBalanceDao.getManualCurrentRow(bankName, accountLast4)
        if (existing != null) {
            accountBalanceDao.updateBalanceAndTimestampById(existing.id, current, now)
        } else {
            accountBalanceDao.insertBalance(
                opening.copy(id = 0, balance = current, timestamp = now, sourceType = SOURCE_MANUAL)
            )
        }
    }

    /**
     * Seeds a brand-new manual account: an OPENING anchor (at an early timestamp)
     * plus its current MANUAL row, both equal to [openingBalance] since there are
     * no transactions yet. [template] carries currency / accountType / alias / etc.
     */
    suspend fun seedManualAccount(template: AccountBalanceEntity, openingBalance: BigDecimal) {
        val now = LocalDateTime.now()
        database.withTransaction {
            accountBalanceDao.insertBalance(
                template.copy(
                    id = 0,
                    balance = openingBalance,
                    timestamp = now.minusSeconds(1),
                    transactionId = null,
                    sourceType = SOURCE_OPENING,
                    smsSource = null
                )
            )
            accountBalanceDao.insertBalance(
                template.copy(
                    id = 0,
                    balance = openingBalance,
                    timestamp = now,
                    transactionId = null,
                    sourceType = SOURCE_MANUAL,
                    smsSource = null
                )
            )
        }
    }
}
