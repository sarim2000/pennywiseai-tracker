package com.pennywiseai.tracker.data.database.dao

import androidx.room3.*
import com.pennywiseai.tracker.data.database.entity.RuleApplicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleApplicationDao {


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApplication(application: RuleApplicationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApplications(applications: List<RuleApplicationEntity>)

    @Query("DELETE FROM rule_applications WHERE transaction_id = :transactionId")
    suspend fun deleteApplicationsByTransaction(transactionId: String)

    @Query("SELECT COUNT(*) FROM rule_applications WHERE rule_id = :ruleId")
    suspend fun getApplicationCountForRule(ruleId: String): Int

    @Query("DELETE FROM rule_applications")
    suspend fun deleteAllApplications()

    @Query("SELECT * FROM rule_applications ORDER BY applied_at DESC")
    fun getAllApplications(): Flow<List<RuleApplicationEntity>>
}
