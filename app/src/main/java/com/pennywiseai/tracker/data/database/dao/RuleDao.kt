package com.pennywiseai.tracker.data.database.dao

import androidx.room3.*
import com.pennywiseai.tracker.data.database.entity.RuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Query("SELECT * FROM transaction_rules ORDER BY priority ASC, name ASC")
    fun getAllRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM transaction_rules WHERE is_active = 1 ORDER BY priority ASC")
    suspend fun getActiveRules(): List<RuleEntity>

    @Query("SELECT * FROM transaction_rules WHERE id = :ruleId")
    suspend fun getRuleById(ruleId: String): RuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<RuleEntity>)

    @Update
    suspend fun updateRule(rule: RuleEntity)

    @Query("UPDATE transaction_rules SET is_active = :isActive WHERE id = :ruleId")
    suspend fun setRuleActive(ruleId: String, isActive: Boolean)

    @Query("UPDATE transaction_rules SET priority = :priority WHERE id = :ruleId")
    suspend fun updateRulePriority(ruleId: String, priority: Int)

    @Delete
    suspend fun deleteRule(rule: RuleEntity)

    @Query("DELETE FROM transaction_rules WHERE id = :ruleId")
    suspend fun deleteRuleById(ruleId: String)

    @Query("SELECT COUNT(*) FROM transaction_rules")
    suspend fun getRuleCount(): Int

    @Query("SELECT COUNT(*) FROM transaction_rules WHERE is_active = 1")
    suspend fun getActiveRuleCount(): Int

    @Query("DELETE FROM transaction_rules")
    suspend fun deleteAllRules()
}