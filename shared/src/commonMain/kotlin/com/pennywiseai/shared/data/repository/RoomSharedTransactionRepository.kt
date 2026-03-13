package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.local.dao.SharedTransactionDao
import com.pennywiseai.shared.data.model.SharedTransaction
import com.pennywiseai.shared.data.repository.mapper.toDomain
import com.pennywiseai.shared.data.repository.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSharedTransactionRepository(
    private val dao: SharedTransactionDao
) : SharedTransactionRepository {
    override fun observeTransactions(): Flow<List<SharedTransaction>> =
        dao.observeTransactions().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): SharedTransaction? =
        dao.getById(id)?.toDomain()

    override suspend fun getByHash(hash: String): SharedTransaction? =
        dao.getByHash(hash)?.toDomain()

    override suspend fun getByReference(reference: String): SharedTransaction? =
        dao.getByReference(reference)?.toDomain()

    override suspend fun getByAmountAndDate(
        amountMinor: Long,
        startEpochMillis: Long,
        endEpochMillis: Long
    ): List<SharedTransaction> = dao.getByAmountAndDate(amountMinor, startEpochMillis, endEpochMillis).map { it.toDomain() }

    override suspend fun insert(transaction: SharedTransaction): Long =
        dao.insert(transaction.toEntity())

    override suspend fun insertAll(transactions: List<SharedTransaction>) {
        transactions.forEach { dao.insert(it.toEntity()) }
    }

    override suspend fun update(transaction: SharedTransaction) {
        dao.update(transaction.toEntity())
    }

    override suspend fun softDelete(id: Long, updatedAtEpochMillis: Long) {
        dao.softDelete(id, updatedAtEpochMillis)
    }
}
