package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.model.SharedTransaction
import kotlinx.coroutines.flow.Flow

interface SharedTransactionRepository {
    fun observeTransactions(): Flow<List<SharedTransaction>>
    suspend fun getById(id: Long): SharedTransaction?
    suspend fun getByHash(hash: String): SharedTransaction?
    suspend fun getByReference(reference: String): SharedTransaction?
    /** Same amount in the same [currency] (minor units only compare within one), on [startEpochMillis]..[endEpochMillis]. */
    suspend fun getByAmountAndDate(amountMinor: Long, currency: String, startEpochMillis: Long, endEpochMillis: Long): List<SharedTransaction>
    suspend fun insert(transaction: SharedTransaction): Long
    suspend fun insertAll(transactions: List<SharedTransaction>)
    suspend fun update(transaction: SharedTransaction)
    suspend fun softDelete(id: Long, updatedAtEpochMillis: Long)
}
