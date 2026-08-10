package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.TransactionGroupDao
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.sumByCurrency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A group with its aggregate figures. Totals are kept per-currency because a
 * group can mix currencies and summing across them is meaningless (hard
 * constraint 2). Keyed by currency code. Shared by the groups screen and the
 * Home "Groups" section so the two can never disagree.
 */
data class GroupSummary(
    val group: TransactionGroupEntity,
    val transactionCount: Int,
    val expenseByCurrency: Map<String, Money>,
    val incomeByCurrency: Map<String, Money>
) {
    val hasExpense: Boolean get() = expenseByCurrency.values.any { it.isPositive }
    val hasIncome: Boolean get() = incomeByCurrency.values.any { it.isPositive }
}

@Singleton
class TransactionGroupRepository @Inject constructor(
    private val groupDao: TransactionGroupDao
) {
    fun getAllGroups(): Flow<List<TransactionGroupEntity>> = groupDao.getAllGroups()

    /**
     * Live [GroupSummary] per group; empty list when there are no groups.
     * All groups' transactions come from a single query (rather than one
     * reactive query per group), so DB work stays constant as groups grow.
     */
    fun observeGroupSummaries(): Flow<List<GroupSummary>> =
        combine(getAllGroups(), groupDao.getAllGroupedTransactions()) { groups, transactions ->
            val byGroup = transactions.groupBy { it.groupId }
            groups.map { group -> buildSummary(group, byGroup[group.id].orEmpty()) }
        }

    private fun buildSummary(
        group: TransactionGroupEntity,
        transactions: List<TransactionEntity>
    ): GroupSummary {
        val expense = transactions
            .filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT }
            .sumByCurrency({ it.currency }, { it.amount })
        val income = transactions
            .filter { it.transactionType == TransactionType.INCOME }
            .sumByCurrency({ it.currency }, { it.amount })
        return GroupSummary(group, transactions.size, expense, income)
    }

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
