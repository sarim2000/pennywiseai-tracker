package com.pennywiseai.tracker.data.repository

import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountBalanceRepository @Inject constructor(
    private val accountBalanceDao: AccountBalanceDao,
    private val database: PennyWiseDatabase
) {
    
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
                profileId = existing?.profileId ?: ProfileEntity.PERSONAL_ID
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
            profileId = existing?.profileId ?: ProfileEntity.PERSONAL_ID
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

    suspend fun setAccountProfile(bankName: String, accountLast4: String, profileId: Long): Int {
        return accountBalanceDao.setAccountProfile(bankName, accountLast4, profileId)
    }

    /**
     * Atomically revert the balance impact of the [original] TRANSFER (if it was
     * one) and apply the [updated] TRANSFER's impact (if it is one) — wrapped in
     * a Room transaction so a crash mid-sequence can't leave accounts in a
     * half-reverted state. Revert entries are timestamped with the original
     * transaction's dateTime, apply entries with the updated transaction's, so
     * the balance history timeline stays correct.
     *
     * Looks up accounts by `accountLast4` alone — `from_account` / `to_account`
     * only persist the last4. A missing balance row for a referenced last4 is
     * silently skipped (account not tracked yet); the other side still runs.
     */
    suspend fun applyTransferBalanceShift(
        original: TransactionEntity?,
        updated: TransactionEntity
    ) {
        database.withTransaction {
            if (original != null && original.transactionType == TransactionType.TRANSFER) {
                original.fromAccount?.let {
                    insertBalanceDeltaByLast4(it, original.amount, original.dateTime, updated.id)
                }
                original.toAccount?.let {
                    insertBalanceDeltaByLast4(it, original.amount.negate(), original.dateTime, updated.id)
                }
            }
            if (updated.transactionType == TransactionType.TRANSFER) {
                updated.fromAccount?.let {
                    insertBalanceDeltaByLast4(it, updated.amount.negate(), updated.dateTime, updated.id)
                }
                updated.toAccount?.let {
                    insertBalanceDeltaByLast4(it, updated.amount, updated.dateTime, updated.id)
                }
            }
        }
    }

    private suspend fun insertBalanceDeltaByLast4(
        accountLast4: String,
        delta: BigDecimal,
        timestamp: LocalDateTime,
        transactionId: Long
    ) {
        val latest = accountBalanceDao.getLatestBalanceByLast4(accountLast4) ?: return
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
}
