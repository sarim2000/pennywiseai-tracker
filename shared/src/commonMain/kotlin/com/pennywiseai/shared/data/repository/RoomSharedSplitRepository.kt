package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.local.dao.SharedTransactionSplitDao
import com.pennywiseai.shared.data.local.entity.SharedTransactionSplitEntity
import kotlinx.coroutines.flow.Flow

class RoomSharedSplitRepository(
    private val dao: SharedTransactionSplitDao
) : SharedSplitRepository {
    override fun observeSplits(transactionId: Long): Flow<List<SharedTransactionSplitEntity>> =
        dao.observeForTransaction(transactionId)

    override suspend fun replaceSplits(transactionId: Long, splits: List<SharedTransactionSplitEntity>) {
        dao.deleteForTransaction(transactionId)
        if (splits.isNotEmpty()) {
            dao.insertAll(splits.map { it.copy(transactionId = transactionId) })
        }
    }
}
