package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.CustomParserRuleDao
import com.pennywiseai.tracker.data.database.entity.CustomParserRuleEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomParserRuleRepository @Inject constructor(
    private val dao: CustomParserRuleDao
) {
    fun getAllRules(): Flow<List<CustomParserRuleEntity>> = dao.getAllRules()

    suspend fun getActiveRules(): List<CustomParserRuleEntity> = dao.getActiveRules()

    suspend fun getRuleById(id: Long): CustomParserRuleEntity? = dao.getRuleById(id)

    suspend fun insertRule(rule: CustomParserRuleEntity): Long = dao.insertRule(rule)

    suspend fun updateRule(rule: CustomParserRuleEntity) {
        dao.updateRule(rule.copy(updatedAt = LocalDateTime.now()))
    }

    suspend fun setActive(id: Long, isActive: Boolean) {
        dao.setActive(id, isActive)
    }

    suspend fun deleteRuleById(id: Long) = dao.deleteRuleById(id)
}
