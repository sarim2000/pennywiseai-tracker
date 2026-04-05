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
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoanRepository @Inject constructor(
    private val loanDao: LoanDao,
    private val transactionDao: TransactionDao
) {
    fun getActiveLoans(): Flow<List<LoanEntity>> = loanDao.getActiveLoans()

    fun getAllLoans(): Flow<List<LoanEntity>> = loanDao.getAllLoans()

    fun getActiveLoanCount(): Flow<Int> = loanDao.getActiveLoanCount()

    fun getTotalLentRemaining(): Flow<BigDecimal> = loanDao.getTotalLentRemaining()

    fun getTotalBorrowedRemaining(): Flow<BigDecimal> = loanDao.getTotalBorrowedRemaining()

    fun getTransactionsForLoan(loanId: Long): Flow<List<TransactionEntity>> =
        loanDao.getTransactionsForLoan(loanId)

    fun getRecentUnlinkedRepayments(direction: LoanDirection, limit: Int = 20): Flow<List<TransactionEntity>> {
        val repaymentType = if (direction == LoanDirection.LENT) "INCOME" else "EXPENSE"
        return loanDao.getRecentUnlinkedTransactionsByType(repaymentType, limit)
    }

    fun getRecentPersonNames(): Flow<List<String>> = loanDao.getRecentPersonNames()

    suspend fun getLoanById(loanId: Long): LoanEntity? = loanDao.getLoanById(loanId)

    suspend fun findActiveLoanForPerson(personName: String, direction: LoanDirection): LoanEntity? =
        loanDao.getActiveLoanByPersonAndDirection(personName, direction.name)

    suspend fun addToExistingLoan(loanId: Long, amount: BigDecimal, transactionId: Long) {
        val loan = loanDao.getLoanById(loanId) ?: return
        loanDao.updateLoan(
            loan.copy(
                originalAmount = loan.originalAmount + amount,
                remainingAmount = loan.remainingAmount + amount,
                updatedAt = LocalDateTime.now()
            )
        )
        loanDao.linkTransaction(transactionId, loanId)
    }

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
        return loanId
    }

    suspend fun recordRepayment(loanId: Long, transactionId: Long) {
        loanDao.linkTransaction(transactionId, loanId)
        recalculateRemaining(loanId)
    }

    suspend fun recordManualRepayment(
        loanId: Long,
        amount: BigDecimal,
        personName: String,
        currency: String
    ): Long {
        val loan = loanDao.getLoanById(loanId) ?: return -1
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
            loanId = loanId
        )
        val txId = transactionDao.insertTransaction(transaction)
        recalculateRemaining(loanId)
        return txId
    }

    suspend fun unlinkTransaction(transactionId: Long, loanId: Long) {
        loanDao.unlinkTransaction(transactionId)
        recalculateRemaining(loanId)
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
