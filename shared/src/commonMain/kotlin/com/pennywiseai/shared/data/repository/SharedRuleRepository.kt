package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.local.entity.SharedRuleEntity
import kotlinx.coroutines.flow.Flow

interface SharedRuleRepository {
    fun observeRules(): Flow<List<SharedRuleEntity>>
    suspend fun upsertRule(rule: SharedRuleEntity)
    suspend fun deleteRuleById(ruleId: String)
}
