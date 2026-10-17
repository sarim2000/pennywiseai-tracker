package com.pennywiseai.tracker.data.repository

import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.GoalDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A goal's progress plus its effective contributions, newest first. */
data class GoalDetail(val progress: GoalProgress, val contributions: List<GoalContributionEntity>)

/**
 * Goals and their Contributions / Withdrawals (#914). Math lives in [GoalRules];
 * this class enforces the invariants on write. Invalid input throws
 * [IllegalArgumentException] / [IllegalStateException]. Goals never change
 * transactions.
 */
@Singleton
class GoalRepository @Inject constructor(
    private val goalDao: GoalDao,
    private val transactionDao: TransactionDao,
    private val database: PennyWiseDatabase,
) {

    // ponytail: `today` is fixed per subscription; re-collect after midnight if a screen stays open for days.
    /** Every goal with progress, archived included, in no particular order. */
    fun observeGoals(today: LocalDate = LocalDate.now()): Flow<List<GoalProgress>> =
        combine(goalDao.observeGoals(), goalDao.observeEffectiveContributions()) { goals, contributions ->
            val byGoal = contributions.groupBy { it.goalId }
            goals.map { GoalRules.progress(it, byGoal[it.id].orEmpty(), today) }
        }

    /** Non-archived goals, soonest due first, Reached last. */
    fun observeActiveGoals(today: LocalDate = LocalDate.now()): Flow<List<GoalProgress>> =
        observeGoals(today).map(GoalRules::orderForDisplay)

    fun observeArchivedGoals(today: LocalDate = LocalDate.now()): Flow<List<GoalProgress>> =
        observeGoals(today).map { all -> all.filter { it.status == GoalStatus.ARCHIVED }.sortedBy { it.goal.dueDate } }

    fun observeGoalDetail(goalId: Long, today: LocalDate = LocalDate.now()): Flow<GoalDetail?> =
        combine(goalDao.observeGoal(goalId), goalDao.observeEffectiveContributionsForGoal(goalId)) { goal, contributions ->
            goal?.let { GoalDetail(GoalRules.progress(it, contributions, today), contributions) }
        }

    /** Merchant of each transaction [goalId]'s contributions came from, by transaction id; follows renames. */
    fun observeLinkedMerchants(goalId: Long): Flow<Map<Long, String>> =
        goalDao.observeLinkedMerchants(goalId).map { rows -> rows.associate { it.id to it.merchantName } }

    /** All contributions linked to [transactionId], across goals (soft-deleted transaction or not). */
    fun observeContributionsForTransaction(transactionId: Long): Flow<List<GoalContributionEntity>> =
        goalDao.observeContributionsForTransaction(transactionId)

    /** Whether a new goal may be created: Pro, or fewer active goals than the free limit. */
    suspend fun canCreateGoal(isPro: Boolean, today: LocalDate = LocalDate.now()): Boolean =
        GoalRules.canCreateGoal(isPro, observeGoals(today).first().count { it.isActive })

    suspend fun createGoal(
        name: String,
        targetAmount: BigDecimal,
        currency: String,
        dueDate: LocalDate,
        color: String,
        alreadySaved: BigDecimal = BigDecimal.ZERO,
    ): Long {
        require(name.isNotBlank()) { "Goal name is blank" }
        require(targetAmount.signum() > 0) { "Target amount must be positive" }
        require(alreadySaved.signum() >= 0) { "Already saved can't be negative" }
        val now = LocalDateTime.now()
        return goalDao.insertGoal(
            GoalEntity(
                name = name.trim(), targetAmount = targetAmount, currency = currency, dueDate = dueDate,
                color = color, alreadySaved = alreadySaved, createdAt = now, updatedAt = now
            )
        )
    }

    /**
     * Saves edits to name/target/due date/colour/already saved/currency. The
     * currency can't change once the goal has any contribution; uid and
     * createdAt (the start of the even pace) are kept from the stored row.
     */
    suspend fun updateGoal(goal: GoalEntity) = database.withTransaction {
        val existing = checkNotNull(goalDao.getGoal(goal.id)) { "Goal ${goal.id} not found" }
        require(goal.name.isNotBlank()) { "Goal name is blank" }
        require(goal.targetAmount.signum() > 0) { "Target amount must be positive" }
        require(goal.alreadySaved.signum() >= 0) { "Already saved can't be negative" }
        check(
            goal.currency == existing.currency ||
                (goalDao.countContributions(goal.id) == 0 && existing.alreadySaved.signum() == 0)
        ) {
            "A goal's currency can't change once it holds money (contributions or already saved)"
        }
        goalDao.updateGoal(
            goal.copy(name = goal.name.trim(), uid = existing.uid, createdAt = existing.createdAt, updatedAt = LocalDateTime.now())
        )
    }

    suspend fun setArchived(goalId: Long, archived: Boolean) =
        goalDao.setArchived(goalId, archived, LocalDateTime.now())

    /** Removes the goal and its contributions only; transactions are untouched. */
    suspend fun deleteGoal(goalId: Long) = goalDao.deleteGoal(goalId)

    /**
     * Adds a Contribution of [amount] (> 0, goal currency). For money typed in
     * another currency pass [originalAmount]/[originalCurrency]. For one taken
     * from a transaction pass [transactionId]; [originalAmount] is then the
     * share of the transaction in its currency (defaults to [amount] when the
     * currencies match) and must fit in what the transaction has left.
     */
    suspend fun addContribution(
        goalId: Long,
        amount: BigDecimal,
        date: LocalDate = LocalDate.now(),
        note: String? = null,
        originalAmount: BigDecimal? = null,
        originalCurrency: String? = null,
        transactionId: Long? = null,
    ): Long = insertMovement(goalId, amount, date, note, originalAmount, originalCurrency, transactionId, sign = 1)

    /** Records a Withdrawal of [amount] (> 0); stored negative. Same options as [addContribution]. */
    suspend fun addWithdrawal(
        goalId: Long,
        amount: BigDecimal,
        date: LocalDate = LocalDate.now(),
        note: String? = null,
        originalAmount: BigDecimal? = null,
        originalCurrency: String? = null,
        transactionId: Long? = null,
    ): Long = insertMovement(goalId, amount, date, note, originalAmount, originalCurrency, transactionId, sign = -1)

    private suspend fun insertMovement(
        goalId: Long,
        amount: BigDecimal,
        date: LocalDate,
        note: String?,
        originalAmount: BigDecimal?,
        originalCurrency: String?,
        transactionId: Long?,
        sign: Int,
    ): Long = database.withTransaction {
        require(amount.signum() > 0) { "Amount must be positive" }
        require(originalAmount == null || originalAmount.signum() > 0) { "Original amount must be positive" }
        val goal = checkNotNull(goalDao.getGoal(goalId)) { "Goal $goalId not found" }
        val now = LocalDateTime.now()
        val contribution = GoalContributionEntity(
            goalId = goalId,
            amount = if (sign < 0) amount.negate() else amount,
            date = date,
            note = note?.trim()?.ifEmpty { null },
            originalAmount = originalAmount?.let { if (sign < 0) it.negate() else it },
            originalCurrency = originalCurrency,
            transactionId = transactionId,
            createdAt = now,
            updatedAt = now,
        )
        goalDao.insertContribution(validated(contribution, goal))
    }

    /**
     * Saves an edited contribution (amount stays signed). goalId, uid,
     * transactionId and createdAt are kept from the stored row; a linked share
     * must still fit (for a cross-currency link, update [GoalContributionEntity.originalAmount] too).
     */
    suspend fun updateContribution(contribution: GoalContributionEntity) = database.withTransaction {
        val existing = checkNotNull(goalDao.getContribution(contribution.id)) { "Contribution ${contribution.id} not found" }
        require(contribution.amount.signum() != 0) { "Amount can't be zero" }
        val goal = checkNotNull(goalDao.getGoal(existing.goalId))
        val updated = contribution.copy(
            goalId = existing.goalId, uid = existing.uid, transactionId = existing.transactionId,
            createdAt = existing.createdAt, note = contribution.note?.trim()?.ifEmpty { null },
            updatedAt = LocalDateTime.now()
        )
        goalDao.updateContribution(validated(updated, goal))
    }

    suspend fun deleteContribution(contributionId: Long) = goalDao.deleteContribution(contributionId)

    /** What's left of a transaction to put toward goals, in its currency. */
    suspend fun unallocatedAmount(transactionId: Long, excludingContributionId: Long? = null): BigDecimal {
        val transaction = checkNotNull(transactionDao.getTransactionById(transactionId)) { "Transaction $transactionId not found" }
        return GoalRules.unallocated(transaction.amount, goalDao.getContributionsForTransaction(transactionId), excludingContributionId)
    }

    /**
     * Runs [write], which may change transaction [transactionId]'s amount, and
     * then caps its contributions to the amount stored at that moment, in one
     * DB transaction, so a cap can never act on an amount a later write replaced.
     * Shares that still fit (an increase) are left alone; a transaction with no
     * contributions costs one indexed read. [TransactionRepository] wraps every
     * amount write in this.
     */
    suspend fun <T> writeAndCap(transactionId: Long, write: suspend () -> T): T = database.withTransaction {
        val result = write()
        val linked = goalDao.getContributionsForTransaction(transactionId)
        val stored = if (linked.isEmpty()) null else transactionDao.getTransactionById(transactionId)
        if (stored != null) {
            val changed = GoalRules.capContributions(linked, stored.amount, LocalDateTime.now())
            if (changed.isNotEmpty()) goalDao.updateContributions(changed)
        }
        result
    }

    /**
     * Before a duplicate transaction is removed, moves its contributions to the
     * row that survives (capped to its amount) so the money stays in the goal.
     * See [GoalRules.repoint].
     */
    suspend fun moveContributions(fromTransactionId: Long, toTransactionId: Long) {
        if (fromTransactionId == toTransactionId) return
        if (goalDao.getContributionsForTransaction(fromTransactionId).isEmpty()) return
        database.withTransaction {
            val survivor = transactionDao.getTransactionById(toTransactionId) ?: return@withTransaction
            val changed = GoalRules.repoint(
                moving = goalDao.getContributionsForTransaction(fromTransactionId),
                survivorLinked = goalDao.getContributionsForTransaction(toTransactionId),
                survivorId = survivor.id,
                survivorAmount = survivor.amount,
                survivorCurrency = survivor.currency,
                now = LocalDateTime.now(),
            )
            if (changed.isNotEmpty()) goalDao.updateContributions(changed)
        }
    }

    /** A fresh copy due a year later, starting today at 0 with no contributions. */
    suspend fun repeatForNextYear(goalId: Long): Long {
        val goal = checkNotNull(goalDao.getGoal(goalId)) { "Goal $goalId not found" }
        val now = LocalDateTime.now()
        return goalDao.insertGoal(
            goal.copy(
                id = 0, uid = UUID.randomUUID().toString(), dueDate = goal.dueDate.plusYears(1),
                alreadySaved = BigDecimal.ZERO, isArchived = false, createdAt = now, updatedAt = now
            )
        )
    }

    /**
     * Checks the original-amount pair and, for a linked row, fills in the
     * transaction share and checks it fits. Caller is inside a DB transaction.
     */
    private suspend fun validated(c: GoalContributionEntity, goal: GoalEntity): GoalContributionEntity {
        require(c.originalAmount == null || c.originalAmount.signum() == c.amount.signum()) {
            "The original amount has the amount's sign"
        }
        val transactionId = c.transactionId ?: run {
            require((c.originalAmount == null) == (c.originalCurrency == null)) { "Original amount and currency go together" }
            return c
        }
        val transaction = checkNotNull(transactionDao.getTransactionById(transactionId)) { "Transaction $transactionId not found" }
        require(!transaction.isDeleted) { "Transaction $transactionId is deleted" }
        require(c.originalCurrency == null || c.originalCurrency == transaction.currency) {
            "A transaction share is in the transaction's currency"
        }
        // Same currency: the share is the amount itself. Otherwise the caller passes it.
        val share = if (transaction.currency == goal.currency) c.amount else requireNotNull(c.originalAmount) {
            "Pass the transaction share when its currency differs from the goal's"
        }
        require(share.signum() == c.amount.signum()) { "The share has the amount's sign" }
        val left = GoalRules.unallocated(transaction.amount, goalDao.getContributionsForTransaction(transactionId), c.id)
        require(share.abs() <= left) { "Only $left ${transaction.currency} of this transaction is left to put toward goals" }
        return c.copy(originalAmount = share, originalCurrency = transaction.currency)
    }
}
