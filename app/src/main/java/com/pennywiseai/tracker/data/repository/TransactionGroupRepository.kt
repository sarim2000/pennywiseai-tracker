package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.TransactionGroupDao
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
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
    val incomeByCurrency: Map<String, Money>,
    val investedByCurrency: Map<String, Money> = emptyMap()
) {
    val hasExpense: Boolean get() = expenseByCurrency.values.any { it.isPositive }
    val hasIncome: Boolean get() = incomeByCurrency.values.any { it.isPositive }
    val hasInvested: Boolean get() = investedByCurrency.values.any { it.isPositive }
}

/** A group's per-currency totals, split by what the money did. */
data class GroupTotals(
    val expense: Map<String, Money>,
    val income: Map<String, Money>,
    val invested: Map<String, Money>
)

/**
 * The one definition of a group's totals, used by every screen that shows
 * them. Investments get their own figure (#837): they aren't spending, but a
 * group that holds nothing else used to show "No transactions yet" under a
 * non-zero item count. Transfers stay out of every figure — money moving
 * between your own accounts isn't a total of anything.
 */
fun groupTotalsOf(transactions: List<TransactionEntity>): GroupTotals = GroupTotals(
    expense = transactions
        .filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT }
        .sumByCurrency({ it.currency }, { it.amount }),
    income = transactions
        .filter { it.transactionType == TransactionType.INCOME }
        .sumByCurrency({ it.currency }, { it.amount }),
    invested = transactions
        .filter { it.transactionType == TransactionType.INVESTMENT }
        .sumByCurrency({ it.currency }, { it.amount })
)

/** One account's share of a group. [label] is "Bank ••1234", or null for rows with no account. */
data class AccountShare(val label: String?, val totals: GroupTotals)

/**
 * A group's totals split per account, by the same [groupTotalsOf] rules as the
 * group total, largest first. Accounts whose rows count toward no figure (only
 * transfers) are left out.
 */
fun accountSharesOf(transactions: List<TransactionEntity>): List<AccountShare> =
    transactions
        .groupBy { it.bankName?.takeIf(String::isNotBlank)?.let { bank -> bank to it.accountNumber } }
        .map { (key, txns) ->
            AccountShare(key?.let { (bank, last4) -> AccountBalanceEntity.accountLabel(bank, last4) }, groupTotalsOf(txns))
        }
        .filter { share -> share.totals.all().isNotEmpty() }
        // ponytail: orders by the biggest single figure; currencies aren't converted, which is fine for ordering only.
        .sortedByDescending { share -> share.totals.all().maxOf { it.amount } }

private fun GroupTotals.all(): List<Money> = expense.values + invested.values + income.values

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
        val totals = groupTotalsOf(transactions)
        return GroupSummary(group, transactions.size, totals.expense, totals.income, totals.invested)
    }

    fun getTransactionsForGroup(groupId: Long): Flow<List<TransactionEntity>> =
        groupDao.getTransactionsForGroup(groupId)

    /**
     * Every group with its live transactions (newest first), from one query for all
     * groups, like [observeGroupSummaries]: a flow per group re-ran a query per group
     * on every transactions write.
     */
    fun observeGroupsWithTransactions(): Flow<List<Pair<TransactionGroupEntity, List<TransactionEntity>>>> =
        combine(getAllGroups(), groupDao.getAllGroupedTransactions()) { groups, transactions ->
            val byGroup = transactions.groupBy { it.groupId }
            groups.map { group -> group to byGroup[group.id].orEmpty().sortedByDescending { it.dateTime } }
        }

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
