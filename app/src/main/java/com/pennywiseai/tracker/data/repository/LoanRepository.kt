package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.LoanDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.flow.Flow
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
    private val transactionDao: TransactionDao
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

    fun getActiveLentTransactionsInPeriod(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Flow<List<TransactionEntity>> = loanDao.getActiveLentTransactionsInPeriod(startDate, endDate)

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

    fun getTransactionsForLoan(loanId: Long): Flow<List<TransactionEntity>> =
        loanDao.getTransactionsForLoan(loanId)

    // Only same-currency transactions can repay a loan (see getActiveLoanByPersonAndDirection).
    fun getRecentUnlinkedRepayments(direction: LoanDirection, currency: String, limit: Int = 20): Flow<List<TransactionEntity>> {
        val repaymentType = if (direction == LoanDirection.LENT) "INCOME" else "EXPENSE"
        return loanDao.getRecentUnlinkedTransactionsByType(repaymentType, currency, limit)
    }

    fun getRecentPersonNames(): Flow<List<String>> = loanDao.getRecentPersonNames()

    suspend fun getLoanById(loanId: Long): LoanEntity? = loanDao.getLoanById(loanId)

    suspend fun getOriginalTransactionForLoan(loanId: Long): TransactionEntity? =
        loanDao.getOriginalTransactionForLoan(loanId)

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
        val remainingPrincipal = loanDao.getTotalRepaidByType(loanId, contributionType)
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

    suspend fun deleteLoan(loanId: Long) {
        val loan = loanDao.getLoanById(loanId) ?: return
        loanDao.unlinkAllTransactions(loanId)
        loanDao.deleteLoan(loan)
    }

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
