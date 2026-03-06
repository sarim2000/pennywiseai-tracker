package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.local.entity.SharedAccountBalanceEntity
import com.pennywiseai.shared.data.local.entity.SharedCardEntity
import kotlinx.coroutines.flow.Flow

interface SharedAccountRepository {
    fun observeBalances(): Flow<List<SharedAccountBalanceEntity>>
    fun observeCards(): Flow<List<SharedCardEntity>>
    suspend fun insertBalance(balance: SharedAccountBalanceEntity): Long
    suspend fun upsertCard(card: SharedCardEntity): Long
    suspend fun getLatestBalance(bankName: String, accountLast4: String): SharedAccountBalanceEntity?
    suspend fun getDistinctAccounts(): List<SharedAccountBalanceEntity>
    suspend fun deleteBalanceById(id: Long)
    suspend fun deleteByAccount(bankName: String, accountLast4: String)
    suspend fun deleteCardById(id: Long)
    suspend fun renameAccount(oldBankName: String, newBankName: String, accountLast4: String)
}
