package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.local.dao.SharedAccountBalanceDao
import com.pennywiseai.shared.data.local.dao.SharedCardDao
import com.pennywiseai.shared.data.local.entity.SharedAccountBalanceEntity
import com.pennywiseai.shared.data.local.entity.SharedCardEntity
import kotlinx.coroutines.flow.Flow

class RoomSharedAccountRepository(
    private val balanceDao: SharedAccountBalanceDao,
    private val cardDao: SharedCardDao
) : SharedAccountRepository {
    override fun observeBalances(): Flow<List<SharedAccountBalanceEntity>> = balanceDao.observeAll()
    override fun observeCards(): Flow<List<SharedCardEntity>> = cardDao.observeAll()
    override suspend fun insertBalance(balance: SharedAccountBalanceEntity): Long = balanceDao.insert(balance)
    override suspend fun upsertCard(card: SharedCardEntity): Long = cardDao.upsert(card)
}
