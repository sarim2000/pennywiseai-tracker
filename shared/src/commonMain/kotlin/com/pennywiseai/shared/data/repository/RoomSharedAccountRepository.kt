package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.local.dao.SharedAccountBalanceDao
import com.pennywiseai.shared.data.local.dao.SharedCardDao
import com.pennywiseai.shared.data.local.dao.SharedTransactionDao
import com.pennywiseai.shared.data.local.entity.SharedAccountBalanceEntity
import com.pennywiseai.shared.data.local.entity.SharedCardEntity
import kotlinx.coroutines.flow.Flow

class RoomSharedAccountRepository(
    private val balanceDao: SharedAccountBalanceDao,
    private val cardDao: SharedCardDao,
    private val transactionDao: SharedTransactionDao? = null
) : SharedAccountRepository {
    override fun observeBalances(): Flow<List<SharedAccountBalanceEntity>> = balanceDao.observeAll()
    override fun observeCards(): Flow<List<SharedCardEntity>> = cardDao.observeAll()
    override suspend fun insertBalance(balance: SharedAccountBalanceEntity): Long = balanceDao.insert(balance)
    override suspend fun upsertCard(card: SharedCardEntity): Long = cardDao.upsert(card)
    override suspend fun getLatestBalance(bankName: String, accountLast4: String): SharedAccountBalanceEntity? =
        balanceDao.getLatest(bankName, accountLast4)
    override suspend fun getDistinctAccounts(): List<SharedAccountBalanceEntity> =
        balanceDao.getDistinctAccounts()
    override suspend fun deleteBalanceById(id: Long) = balanceDao.deleteById(id)
    override suspend fun deleteByAccount(bankName: String, accountLast4: String) =
        balanceDao.deleteByAccount(bankName, accountLast4)
    override suspend fun deleteCardById(id: Long) = cardDao.deleteById(id)
    override suspend fun renameAccount(oldBankName: String, newBankName: String, accountLast4: String) {
        balanceDao.renameAccount(oldBankName, newBankName, accountLast4)
        transactionDao?.renameAccountInTransactions(oldBankName, newBankName, accountLast4)
    }
}
