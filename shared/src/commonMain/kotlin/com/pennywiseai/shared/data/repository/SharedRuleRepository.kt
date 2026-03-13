package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.local.entity.SharedRuleApplicationEntity
import com.pennywiseai.shared.data.local.entity.SharedRuleEntity
import kotlinx.coroutines.flow.Flow

interface SharedRuleRepository {
    fun observeRules(): Flow<List<SharedRuleEntity>>
    fun observeRuleApplications(): Flow<List<SharedRuleApplicationEntity>>
    suspend fun upsertRule(rule: SharedRuleEntity)
    suspend fun deleteRuleById(ruleId: String)
    suspend fun addApplication(application: SharedRuleApplicationEntity)
}
