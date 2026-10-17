package com.pennywiseai.tracker.data.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

/**
 * Contributions that count toward Saved: typed ones, plus transaction-linked
 * ones whose transaction isn't soft-deleted (#914). Undoing the delete brings
 * the contribution back, so rows are never removed for a soft delete.
 */
private const val EFFECTIVE_CONTRIBUTIONS = """
    SELECT c.* FROM goal_contributions c
    LEFT JOIN transactions t ON t.id = c.transaction_id
    WHERE (c.transaction_id IS NULL OR t.is_deleted = 0)
"""

/** A transaction's id and merchant, for naming the goal contributions taken from it. */
data class LinkedMerchant(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "merchant_name") val merchantName: String,
)

@Dao
interface GoalDao {

    @Query("SELECT * FROM goals")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    fun observeGoal(id: Long): Flow<GoalEntity?>

    @Query("SELECT * FROM goals")
    suspend fun getAllGoals(): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getGoal(id: Long): GoalEntity?

    @Insert
    suspend fun insertGoal(goal: GoalEntity): Long

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Query("UPDATE goals SET is_archived = :archived, updated_at = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, updatedAt: java.time.LocalDateTime)

    /** Cascades to the goal's contributions; never touches transactions. */
    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Query("DELETE FROM goals")
    suspend fun deleteAllGoals()

    @Query("$EFFECTIVE_CONTRIBUTIONS ORDER BY c.date DESC, c.id DESC")
    fun observeEffectiveContributions(): Flow<List<GoalContributionEntity>>

    @Query("$EFFECTIVE_CONTRIBUTIONS AND c.goal_id = :goalId ORDER BY c.date DESC, c.id DESC")
    fun observeEffectiveContributionsForGoal(goalId: Long): Flow<List<GoalContributionEntity>>

    /** Every row linked to a transaction, whether or not that transaction is soft-deleted. */
    @Query("SELECT * FROM goal_contributions WHERE transaction_id = :transactionId")
    fun observeContributionsForTransaction(transactionId: Long): Flow<List<GoalContributionEntity>>

    /** Merchants of the transactions [goalId]'s contributions came from; re-emits when one is renamed. */
    @Query(
        "SELECT id, merchant_name FROM transactions " +
            "WHERE id IN (SELECT transaction_id FROM goal_contributions WHERE goal_id = :goalId)"
    )
    fun observeLinkedMerchants(goalId: Long): Flow<List<LinkedMerchant>>

    @Query("SELECT * FROM goal_contributions WHERE transaction_id = :transactionId")
    suspend fun getContributionsForTransaction(transactionId: Long): List<GoalContributionEntity>

    @Query("SELECT * FROM goal_contributions")
    suspend fun getAllContributions(): List<GoalContributionEntity>

    @Query("SELECT * FROM goal_contributions WHERE id = :id")
    suspend fun getContribution(id: Long): GoalContributionEntity?

    @Query("SELECT COUNT(*) FROM goal_contributions WHERE goal_id = :goalId")
    suspend fun countContributions(goalId: Long): Int

    @Insert
    suspend fun insertContribution(contribution: GoalContributionEntity): Long

    @Update
    suspend fun updateContribution(contribution: GoalContributionEntity)

    @Update
    suspend fun updateContributions(contributions: List<GoalContributionEntity>)

    @Query("DELETE FROM goal_contributions WHERE id = :id")
    suspend fun deleteContribution(id: Long)
}
