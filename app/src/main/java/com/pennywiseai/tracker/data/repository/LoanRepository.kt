package com.pennywiseai.tracker.data.repository

import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.LoanDao
import com.pennywiseai.tracker.data.database.dao.SharedTransaction
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanShareEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.loan.SplitPerson
import com.pennywiseai.tracker.domain.loan.SplitRules
import com.pennywiseai.tracker.domain.loan.SplitShare
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoanRepository @Inject constructor(
    private val loanDao: LoanDao,
    private val transactionDao: TransactionDao,
    private val database: PennyWiseDatabase
) {
    // Repayments read what's owed, then write the capped share: serialized so two
    // quick taps can't both see the same balance and over-repay (#831). All loan
    // writers are in this process, so a lock is enough.
    private val repaymentLock = Mutex()

    fun getActiveLoans(): Flow<List<LoanEntity>> = loanDao.getActiveLoans()

    fun getAllLoans(): Flow<List<LoanEntity>> = loanDao.getAllLoans()

    fun getActiveLoanCount(): Flow<Int> = loanDao.getActiveLoanCount()

    fun getTotalLentRemaining(): Flow<BigDecimal> = loanDao.getTotalLentRemaining()

    fun getTotalBorrowedRemaining(): Flow<BigDecimal> = loanDao.getTotalBorrowedRemaining()

    /**
     * Expenses lent on ACTIVE loans in the window. A split bill (#346) appears
     * once per share, with [TransactionEntity.loanContribution] set to that
     * share, so callers can sum `loanContribution ?: amount` either way.
     */
    fun getActiveLentTransactionsInPeriod(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Flow<List<TransactionEntity>> = combine(
        loanDao.getActiveLentTransactionsInPeriod(startDate, endDate),
        loanDao.getActiveLentSharesInPeriod(startDate, endDate)
    ) { linked, shared -> linked + shared.map { it.asLoanRow() } }

    fun getLentLoansSettledInPeriod(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Flow<List<LoanEntity>> = loanDao.getLentLoansSettledInPeriod(startDate, endDate)

    /**
     * Net loss on a settled LENT loan: principal minus what came back as INCOME repayments.
     * Returns zero if the loan was repaid in full (or over).
     */
    suspend fun getSettlementLoss(loan: LoanEntity): BigDecimal {
        if (loan.direction != LoanDirection.LENT) return BigDecimal.ZERO
        val repaid = loanDao.getTotalRepaidByType(loan.id, "INCOME")
        return (loan.originalAmount - repaid).coerceAtLeast(BigDecimal.ZERO)
    }

    /** The loan's history; a split bill shows with this person's share as its loan part. */
    fun getTransactionsForLoan(loanId: Long): Flow<List<TransactionEntity>> = combine(
        loanDao.getTransactionsForLoan(loanId),
        loanDao.getSharedTransactionsForLoan(loanId)
    ) { linked, shared ->
        (linked + shared.map { it.asLoanRow() }).distinctBy { it.id }.sortedBy { it.dateTime }
    }

    // Display/summing copy only: never write it back.
    private fun SharedTransaction.asLoanRow() = transaction.copy(loanContribution = shareAmount)

    // Only same-currency transactions can repay a loan (see getActiveLoanByPersonAndDirection).
    fun getRecentUnlinkedRepayments(direction: LoanDirection, currency: String, limit: Int = 20): Flow<List<TransactionEntity>> {
        val repaymentType = if (direction == LoanDirection.LENT) "INCOME" else "EXPENSE"
        return loanDao.getRecentUnlinkedTransactionsByType(repaymentType, currency, limit)
    }

    fun getRecentPersonNames(): Flow<List<String>> = loanDao.getRecentPersonNames()

    suspend fun getLoanById(loanId: Long): LoanEntity? = loanDao.getLoanById(loanId)

    suspend fun getOriginalTransactionForLoan(loanId: Long): TransactionEntity? =
        loanDao.getOriginalTransactionForLoan(loanId) ?: loanDao.getFirstSharedTransactionForLoan(loanId)

    suspend fun findActiveLoanForPerson(personName: String, direction: LoanDirection, currency: String): LoanEntity? =
        loanDao.getActiveLoanByPersonAndDirection(personName, direction.name, currency)

    /**
     * Merge [transactionId] into an existing loan, bumping its principal by the
     * caller-supplied [contribution]. When [contribution] is less than the
     * transaction's own amount (a partial loan), it is persisted on the
     * transaction's `loan_contribution` column so the override survives future
     * recomputations.
     */
    suspend fun addToExistingLoan(loanId: Long, contribution: BigDecimal, transactionId: Long) {
        requireNotSplit(transactionId)
        val loan = loanDao.getLoanById(loanId) ?: return
        loanDao.updateLoan(
            loan.copy(
                originalAmount = loan.originalAmount + contribution,
                remainingAmount = loan.remainingAmount + contribution,
                updatedAt = LocalDateTime.now()
            )
        )
        loanDao.linkTransaction(transactionId, loanId)
        persistContributionOverride(transactionId, contribution)
    }

    /**
     * Create a new loan seeded from [sourceTransactionId]. [amount] is the
     * principal — typically the full transaction amount, but the caller may
     * supply a smaller value if only part of the payment is a loan.
     */
    suspend fun createLoan(
        personName: String,
        direction: LoanDirection,
        amount: BigDecimal,
        currency: String,
        note: String?,
        sourceTransactionId: Long
    ): Long {
        requireNotSplit(sourceTransactionId)
        val loan = LoanEntity(
            personName = personName,
            direction = direction,
            originalAmount = amount,
            remainingAmount = amount,
            currency = currency,
            note = note
        )
        val loanId = loanDao.insertLoan(loan)
        loanDao.linkTransaction(sourceTransactionId, loanId)
        persistContributionOverride(sourceTransactionId, amount)
        return loanId
    }

    /** A split bill's shares are already its loans (#346); it can't also be marked as one. */
    private suspend fun requireNotSplit(transactionId: Long) {
        check(loanDao.getSharesForTransaction(transactionId).isEmpty()) { "Transaction $transactionId is split; remove the split first" }
    }

    /**
     * Stores `loan_contribution` on the transaction only when the value differs
     * from the transaction's own amount. Equal contributions are treated as
     * "use the full amount" and left null so legacy data and the common case
     * stay unchanged.
     */
    private suspend fun persistContributionOverride(transactionId: Long, contribution: BigDecimal) {
        val txn = transactionDao.getTransactionById(transactionId) ?: return
        val override = if (contribution.compareTo(txn.amount) == 0) null else contribution
        if (txn.loanContribution == override) return
        transactionDao.updateTransaction(
            txn.copy(loanContribution = override, updatedAt = LocalDateTime.now())
        )
    }

    /**
     * Link [transactionId] to [loanId] as a repayment. When [contribution] is set
     * and differs from the transaction's own amount, only that portion counts
     * toward the loan's repaid total (via `loan_contribution`); a null
     * contribution preserves the legacy "full transaction amount" behaviour.
     *
     * The repayment is capped at what's still owed: ₹500 back on a ₹130 loan
     * repays ₹130, and the other ₹370 stays ordinary income in every total
     * (see `countedAmount`) instead of vanishing into a settled loan (#831).
     */
    suspend fun recordRepayment(
        loanId: Long,
        transactionId: Long,
        contribution: BigDecimal? = null
    ) = repaymentLock.withLock {
        requireNotSplit(transactionId)
        val loan = loanDao.getLoanById(loanId)
        val txn = transactionDao.getTransactionById(transactionId)
        val wanted = contribution ?: txn?.amount
        // A row already linked here is already counted as repaid, so add it back to what is owed.
        val alreadyRepaid = if (txn?.loanId == loanId) txn.loanContribution ?: txn.amount else BigDecimal.ZERO
        val owed = loan?.remainingAmount?.plus(alreadyRepaid)
        // Capped even at zero (a reopened, fully repaid loan): the payment then
        // counts in full as ordinary income/spend rather than vanishing.
        val capped = if (wanted != null && owed != null && wanted > owed) owed else contribution
        loanDao.linkTransaction(transactionId, loanId)
        if (capped != null) {
            persistContributionOverride(transactionId, capped)
        }
        recalculateRemaining(loanId)
    }

    suspend fun recordManualRepayment(
        loanId: Long,
        amount: BigDecimal,
        personName: String,
        currency: String
    ): Long = repaymentLock.withLock {
        val loan = loanDao.getLoanById(loanId) ?: return@withLock -1L
        val txType = if (loan.direction == LoanDirection.LENT)
            TransactionType.INCOME else TransactionType.EXPENSE
        val transaction = TransactionEntity(
            amount = amount,
            merchantName = personName,
            category = if (txType == TransactionType.INCOME) "Income" else "Others",
            transactionType = txType,
            dateTime = LocalDateTime.now(),
            description = "Loan repayment – $personName",
            transactionHash = "loan_repayment_${loanId}_${System.currentTimeMillis()}",
            currency = currency,
            loanId = loanId,
            // Same cap as [recordRepayment]: anything beyond what's owed counts
            // as ordinary income/spend instead of vanishing (#831).
            loanContribution = loan.remainingAmount.takeIf { amount > it }
        )
        val txId = transactionDao.insertTransaction(transaction)
        recalculateRemaining(loanId)
        txId
    }

    suspend fun unlinkTransaction(transactionId: Long, loanId: Long) {
        loanDao.unlinkTransaction(transactionId)
        val loan = loanDao.getLoanById(loanId) ?: return
        // Recompute the principal from the remaining CONTRIBUTION transactions
        // (the loan's own direction: LENT->EXPENSE, BORROWED->INCOME). Repayments
        // are the opposite type and don't count toward principal.
        val contributionType = if (loan.direction == LoanDirection.LENT) "EXPENSE" else "INCOME"
        // Split-bill shares (#346) are principal too.
        val remainingPrincipal = loanDao.getTotalRepaidByType(loanId, contributionType) + shareTotal(loanId)
        if (remainingPrincipal.compareTo(BigDecimal.ZERO) == 0) {
            // No principal left — the loan is meaningless. Delete it; deleteLoan's
            // unlinkAllTransactions also detaches any leftover repayment rows so
            // they aren't stranded on a zeroed-out, auto-settled loan
            // (issue #444 / Greptile #445). Covers the sole-transaction case too.
            deleteLoan(loanId)
        } else {
            // Multi-entry loan with contributions remaining: shrink the principal
            // to match, so unlinking a source transaction doesn't leave an
            // inflated originalAmount/remaining.
            if (loan.originalAmount.compareTo(remainingPrincipal) != 0) {
                loanDao.updateLoan(loan.copy(originalAmount = remainingPrincipal, updatedAt = LocalDateTime.now()))
            }
            recalculateRemaining(loanId)
        }
    }

    suspend fun updateOriginalAmount(loanId: Long, newAmount: BigDecimal) {
        val loan = loanDao.getLoanById(loanId) ?: return
        loanDao.updateLoan(
            loan.copy(
                originalAmount = newAmount,
                updatedAt = LocalDateTime.now()
            )
        )
        recalculateRemaining(loanId)
    }

    suspend fun settleLoan(loanId: Long) {
        val loan = loanDao.getLoanById(loanId) ?: return
        loanDao.updateLoan(
            loan.copy(
                status = LoanStatus.SETTLED,
                remainingAmount = BigDecimal.ZERO,
                settledAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )
    }

    /** Settles all [loanIds] atomically — same effect as [settleLoan] on each. */
    suspend fun settleLoans(loanIds: List<Long>) {
        loanDao.settleLoans(loanIds, LocalDateTime.now())
    }

    suspend fun reopenLoan(loanId: Long) {
        val loan = loanDao.getLoanById(loanId) ?: return
        val repaymentType = if (loan.direction == LoanDirection.LENT) "INCOME" else "EXPENSE"
        val totalRepaid = loanDao.getTotalRepaidByType(loanId, repaymentType)
        val remaining = (loan.originalAmount - totalRepaid).coerceAtLeast(BigDecimal.ZERO)
        loanDao.updateLoan(
            loan.copy(
                status = LoanStatus.ACTIVE,
                remainingAmount = remaining,
                settledAt = null,
                updatedAt = LocalDateTime.now()
            )
        )
    }

    suspend fun deleteLoan(loanId: Long): Unit = database.withTransaction {
        val loan = loanDao.getLoanById(loanId) ?: return@withTransaction
        loanDao.unlinkAllTransactions(loanId)
        // Its split shares go with it, so their bills count that part as your spend again.
        val sharedTransactionIds = loanDao.getSharesForLoan(loanId).map { it.transactionId }
        loanDao.deleteSharesForLoan(loanId)
        sharedTransactionIds.forEach { syncLentSharesTotal(it) }
        loanDao.deleteLoan(loan)
    }

    // ========== Split a bill (#346) ==========

    /** Each person's share of [transactionId], with their loan (null if it's gone). */
    fun observeSplit(transactionId: Long): Flow<List<SplitPerson>> = combine(
        loanDao.observeSharesForTransaction(transactionId),
        loanDao.getAllLoans()
    ) { shares, loans ->
        val byId = loans.associateBy { it.id }
        shares.map { SplitPerson(it, byId[it.loanId]) }
    }

    /**
     * Makes [shares] the people [transactionId] is split with. Each share is
     * money they owe you, on their active LENT loan in the transaction's
     * currency (a new loan if they have none), like "Mark as loan".
     *
     * Saving again edits in place: a changed share moves its loan's principal
     * by the difference and a dropped person's share is undone, so repayments
     * already recorded stay on their loan. An empty list removes the split.
     */
    suspend fun saveSplit(transactionId: Long, shares: List<SplitShare>) = database.withTransaction {
        val txn = requireNotNull(transactionDao.getTransactionById(transactionId)) { "Transaction $transactionId not found" }
        if (shares.isNotEmpty()) {
            require(txn.transactionType == TransactionType.EXPENSE) { "Only an expense can be split" }
            require(txn.loanId == null) { "Unmark the loan before splitting" }
            val error = SplitRules.validate(txn.amount, shares)
            require(error == null) { "Invalid split: $error" }
        }

        val wanted = shares.associateBy { it.personName }.toMutableMap()
        for (share in loanDao.getSharesForTransaction(transactionId)) {
            val loan = loanDao.getLoanById(share.loanId)
            val next = loan?.let { wanted.remove(it.personName) }
            if (loan == null || next == null) {
                removeShare(share, loan)
            } else if (next.amount.compareTo(share.amount) != 0) {
                loanDao.updateShare(share.copy(amount = next.amount))
                setPrincipal(loan, loan.originalAmount + next.amount - share.amount)
            }
        }
        for (share in wanted.values) {
            val existing = loanDao.getActiveLoanByPersonAndDirection(share.personName, LoanDirection.LENT.name, txn.currency)
            val loanId = if (existing != null) {
                loanDao.updateLoan(
                    existing.copy(
                        originalAmount = existing.originalAmount + share.amount,
                        remainingAmount = existing.remainingAmount + share.amount,
                        updatedAt = LocalDateTime.now()
                    )
                )
                existing.id
            } else {
                loanDao.insertLoan(
                    LoanEntity(
                        personName = share.personName,
                        direction = LoanDirection.LENT,
                        originalAmount = share.amount,
                        remainingAmount = share.amount,
                        currency = txn.currency
                    )
                )
            }
            loanDao.insertShare(LoanShareEntity(transactionId = transactionId, loanId = loanId, amount = share.amount))
        }
        syncLentSharesTotal(transactionId)
    }

    /**
     * Undoes one share: it comes off its loan's principal. The loan goes only
     * when that leaves nothing: principal kept without a bill (a deleted bill's
     * debt, a restored loan, an edited amount) or other linked money keeps it.
     */
    private suspend fun removeShare(share: LoanShareEntity, loan: LoanEntity?) {
        loanDao.deleteShare(share)
        if (loan == null) return
        val left = loan.originalAmount - share.amount
        val otherPrincipal = loanDao.getTotalRepaidByType(loan.id, "EXPENSE") + shareTotal(loan.id)
        val principal = when {
            left.signum() > 0 -> left
            otherPrincipal.signum() > 0 -> otherPrincipal
            else -> return deleteLoan(loan.id)
        }
        setPrincipal(loan, principal)
    }

    /**
     * Sets a loan's principal after a share changed. A settled loan stays
     * settled: Settle up records no repayment, so recomputing what's left would
     * reopen it for everything forgiven. Only its principal moves.
     */
    private suspend fun setPrincipal(loan: LoanEntity, principal: BigDecimal) {
        loanDao.updateLoan(loan.copy(originalAmount = principal, updatedAt = LocalDateTime.now()))
        if (loan.status != LoanStatus.SETTLED) recalculateRemaining(loan.id)
    }

    // Every share, a soft-deleted bill's too: deleting a bill leaves its debt
    // standing (like a loan-linked bill), so the debt still holds the loan up.
    private suspend fun shareTotal(loanId: Long): BigDecimal =
        loanDao.getSharesForLoan(loanId).fold(BigDecimal.ZERO) { sum, s -> sum + s.amount }

    /** Recomputes [TransactionEntity.lentSharesTotal] from the transaction's share rows. */
    suspend fun syncLentSharesTotal(transactionId: Long) = transactionDao.syncLentSharesTotal(transactionId)

    private suspend fun recalculateRemaining(loanId: Long) {
        val loan = loanDao.getLoanById(loanId) ?: return
        val repaymentType = if (loan.direction == LoanDirection.LENT) "INCOME" else "EXPENSE"
        val totalRepaid = loanDao.getTotalRepaidByType(loanId, repaymentType)
        val remaining = (loan.originalAmount - totalRepaid).coerceAtLeast(BigDecimal.ZERO)
        val newStatus = if (remaining <= BigDecimal.ZERO) LoanStatus.SETTLED else LoanStatus.ACTIVE
        loanDao.updateLoan(
            loan.copy(
                remainingAmount = remaining,
                status = newStatus,
                settledAt = if (newStatus == LoanStatus.SETTLED) LocalDateTime.now() else null,
                updatedAt = LocalDateTime.now()
            )
        )
    }
}
