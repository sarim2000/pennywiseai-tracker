package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.TransactionGroupDao
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionGroupRepository @Inject constructor(
    private val groupDao: TransactionGroupDao
) {
    fun getAllGroups(): Flow<List<TransactionGroupEntity>> = groupDao.getAllGroups()

    fun getTransactionsForGroup(groupId: Long): Flow<List<TransactionEntity>> =
        groupDao.getTransactionsForGroup(groupId)

    fun getTransactionCount(groupId: Long): Flow<Int> = groupDao.getTransactionCount(groupId)

    fun getRecentUngroupedTransactions(): Flow<List<TransactionEntity>> =
        groupDao.getRecentUngroupedTransactions()

    fun searchUngroupedTransactions(query: String): Flow<List<TransactionEntity>> =
        groupDao.searchUngroupedTransactions(query)

    suspend fun getGroupById(id: Long): TransactionGroupEntity? = groupDao.getGroupById(id)

    suspend fun createGroup(name: String, note: String?): Long {
        val group = TransactionGroupEntity(
            name = name.trim(),
            note = note?.trim()?.takeIf { it.isNotEmpty() }
        )
        return groupDao.insertGroup(group)
    }

    suspend fun updateGroup(group: TransactionGroupEntity) {
        groupDao.updateGroup(group.copy(updatedAt = LocalDateTime.now()))
    }

    suspend fun deleteGroup(groupId: Long) {
        val group = groupDao.getGroupById(groupId) ?: return
        groupDao.unlinkAndDeleteGroup(group)
    }

    suspend fun addTransactionToGroup(transactionId: Long, groupId: Long) {
        groupDao.linkTransaction(transactionId, groupId)
        val group = groupDao.getGroupById(groupId) ?: return
        groupDao.updateGroup(group.copy(updatedAt = LocalDateTime.now()))
    }

    suspend fun createGroupWithTransaction(name: String, note: String?, transactionId: Long): Long {
        val group = TransactionGroupEntity(
            name = name.trim(),
            note = note?.trim()?.takeIf { it.isNotEmpty() }
        )
        return groupDao.createGroupAndLink(group, transactionId)
    }

    suspend fun removeTransactionFromGroup(transactionId: Long) {
        groupDao.unlinkTransaction(transactionId)
    }
}
