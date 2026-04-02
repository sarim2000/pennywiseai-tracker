package com.pennywiseai.tracker.data.database.dao

import androidx.room.*
import com.pennywiseai.tracker.data.database.entity.RuleApplicationEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface RuleApplicationDao {

    @Query("SELECT * FROM rule_applications WHERE transaction_id = :transactionId ORDER BY applied_at DESC")
    suspend fun getApplicationsByTransaction(transactionId: String): List<RuleApplicationEntity>

    @Query("SELECT * FROM rule_applications WHERE rule_id = :ruleId ORDER BY applied_at DESC")
    fun getApplicationsByRule(ruleId: String): Flow<List<RuleApplicationEntity>>

    @Query("SELECT * FROM rule_applications ORDER BY applied_at DESC LIMIT :limit")
    fun getRecentApplications(limit: Int): Flow<List<RuleApplicationEntity>>

    @Query("SELECT * FROM rule_applications WHERE applied_at >= :startDate AND applied_at <= :endDate ORDER BY applied_at DESC")
    suspend fun getApplicationsInDateRange(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<RuleApplicationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApplication(application: RuleApplicationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApplications(applications: List<RuleApplicationEntity>)

    @Delete
    suspend fun deleteApplication(application: RuleApplicationEntity)

    @Query("DELETE FROM rule_applications WHERE transaction_id = :transactionId")
    suspend fun deleteApplicationsByTransaction(transactionId: String)

    @Query("DELETE FROM rule_applications WHERE rule_id = :ruleId")
    suspend fun deleteApplicationsByRule(ruleId: String)

    @Query("SELECT COUNT(*) FROM rule_applications WHERE rule_id = :ruleId")
    suspend fun getApplicationCountForRule(ruleId: String): Int

    @Query("SELECT COUNT(DISTINCT transaction_id) FROM rule_applications WHERE rule_id = :ruleId")
    suspend fun getUniqueTransactionCountForRule(ruleId: String): Int

    @Query("DELETE FROM rule_applications WHERE applied_at < :beforeDate")
    suspend fun deleteOldApplications(beforeDate: LocalDateTime)

    @Query("DELETE FROM rule_applications")
    suspend fun deleteAllApplications()
    
    @Query("SELECT * FROM rule_applications ORDER BY applied_at DESC")
    fun getAllApplications(): Flow<List<RuleApplicationEntity>>
}