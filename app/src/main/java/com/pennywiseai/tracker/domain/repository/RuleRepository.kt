package com.pennywiseai.tracker.domain.repository

import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.model.rule.RuleApplication
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import kotlinx.coroutines.flow.Flow

interface RuleRepository {
    fun getAllRules(): Flow<List<TransactionRule>>
    suspend fun getActiveRules(): List<TransactionRule>
    suspend fun getActiveRulesByType(type: TransactionType): List<TransactionRule>
    suspend fun getActiveRulesByTypes(types: List<TransactionType>): List<TransactionRule>
    suspend fun getRuleById(ruleId: String): TransactionRule?
    suspend fun insertRule(rule: TransactionRule)
    suspend fun updateRule(rule: TransactionRule)
    suspend fun deleteRule(ruleId: String)
    suspend fun deleteAllRules()
    suspend fun setRuleActive(ruleId: String, isActive: Boolean)
    suspend fun updateRulePriority(ruleId: String, priority: Int)

    suspend fun saveRuleApplication(application: RuleApplication)
    suspend fun saveRuleApplications(applications: List<RuleApplication>)
    suspend fun getRuleApplicationsForTransaction(transactionId: String): List<RuleApplication>
    fun getRuleApplicationsForRule(ruleId: String): Flow<List<RuleApplication>>
    suspend fun getRuleApplicationCount(ruleId: String): Int
}