package com.pennywiseai.tracker.data.database.dao

import androidx.room.*
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDateTime

@Dao
interface LoanDao {

    @Insert
    suspend fun insertLoan(loan: LoanEntity): Long

    @Update
    suspend fun updateLoan(loan: LoanEntity)

    @Delete
    suspend fun deleteLoan(loan: LoanEntity)

    @Query("SELECT * FROM loans WHERE id = :loanId")
    suspend fun getLoanById(loanId: Long): LoanEntity?

    @Query("SELECT * FROM loans WHERE status = 'ACTIVE' ORDER BY created_at DESC")
    fun getActiveLoans(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans ORDER BY created_at DESC")
    fun getAllLoans(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE status = :status ORDER BY created_at DESC")
    fun getLoansByStatus(status: LoanStatus): Flow<List<LoanEntity>>

    @Query("SELECT COUNT(*) FROM loans WHERE status = 'ACTIVE'")
    fun getActiveLoanCount(): Flow<Int>

    @Query("SELECT DISTINCT person_name FROM loans ORDER BY updated_at DESC")
    fun getRecentPersonNames(): Flow<List<String>>

    @Query("SELECT * FROM loans WHERE person_name = :personName AND direction = :direction AND status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveLoanByPersonAndDirection(personName: String, direction: String): LoanEntity?

    @Query("SELECT * FROM transactions WHERE loan_id = :loanId AND is_deleted = 0 ORDER BY date_time ASC")
    fun getTransactionsForLoan(loanId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE loan_id = :loanId AND is_deleted = 0 ORDER BY date_time ASC LIMIT 1")
    suspend fun getOriginalTransactionForLoan(loanId: Long): TransactionEntity?

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM transactions t
        WHERE t.loan_id = :loanId AND t.is_deleted = 0
        AND t.transaction_type = :repaymentType
    """)
    suspend fun getTotalRepaidByType(loanId: Long, repaymentType: String): BigDecimal

    @Query("SELECT COALESCE(SUM(remaining_amount), 0) FROM loans WHERE direction = 'LENT' AND status = 'ACTIVE'")
    fun getTotalLentRemaining(): Flow<BigDecimal>

    @Query("SELECT COALESCE(SUM(remaining_amount), 0) FROM loans WHERE direction = 'BORROWED' AND status = 'ACTIVE'")
    fun getTotalBorrowedRemaining(): Flow<BigDecimal>

    /**
     * Source EXPENSE transactions of currently-ACTIVE LENT loans whose date_time falls in the
     * given window. Drives "Lent this month" on the home screen — settled loans are excluded
     * because their principal is reflected in "Spent this month" (as a settlement loss) or has
     * been recovered as INCOME repayments.
     */
    @Query("""
        SELECT t.* FROM transactions t
        INNER JOIN loans l ON t.loan_id = l.id
        WHERE l.direction = 'LENT'
          AND l.status = 'ACTIVE'
          AND t.transaction_type = 'EXPENSE'
          AND t.is_deleted = 0
          AND t.date_time BETWEEN :startDate AND :endDate
        ORDER BY t.date_time DESC
    """)
    fun getActiveLentTransactionsInPeriod(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Flow<List<TransactionEntity>>

    /**
     * LENT loans settled within the period. Used to compute settlement losses
     * (originalAmount - totalRepaid) which are folded into "Spent this month".
     */
    @Query("""
        SELECT * FROM loans
        WHERE direction = 'LENT'
          AND status = 'SETTLED'
          AND settled_at BETWEEN :startDate AND :endDate
    """)
    fun getLentLoansSettledInPeriod(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Flow<List<LoanEntity>>

    @Query("UPDATE transactions SET loan_id = NULL WHERE id = :transactionId")
    suspend fun unlinkTransaction(transactionId: Long)

    @Query("UPDATE transactions SET loan_id = :loanId WHERE id = :transactionId")
    suspend fun linkTransaction(transactionId: Long, loanId: Long)

    @Query("UPDATE transactions SET loan_id = NULL WHERE loan_id = :loanId")
    suspend fun unlinkAllTransactions(loanId: Long)

    @Query("""
        SELECT * FROM transactions
        WHERE loan_id IS NULL AND is_deleted = 0
        AND transaction_type = :type
        ORDER BY date_time DESC
        LIMIT :limit
    """)
    fun getRecentUnlinkedTransactionsByType(type: String, limit: Int = 20): Flow<List<TransactionEntity>>
}
