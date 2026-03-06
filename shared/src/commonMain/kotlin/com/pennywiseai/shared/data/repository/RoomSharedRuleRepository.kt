package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.local.dao.SharedRuleApplicationDao
import com.pennywiseai.shared.data.local.dao.SharedRuleDao
import com.pennywiseai.shared.data.local.entity.SharedRuleApplicationEntity
import com.pennywiseai.shared.data.local.entity.SharedRuleEntity
import kotlinx.coroutines.flow.Flow

class RoomSharedRuleRepository(
    private val ruleDao: SharedRuleDao,
    private val ruleApplicationDao: SharedRuleApplicationDao
) : SharedRuleRepository {
    override fun observeRules(): Flow<List<SharedRuleEntity>> = ruleDao.observeAll()
    override fun observeRuleApplications(): Flow<List<SharedRuleApplicationEntity>> = ruleApplicationDao.observeRecent()
    override suspend fun upsertRule(rule: SharedRuleEntity) = ruleDao.upsert(rule)
    override suspend fun deleteRuleById(ruleId: String) = ruleDao.deleteById(ruleId)
    override suspend fun addApplication(application: SharedRuleApplicationEntity) = ruleApplicationDao.insert(application)
}
