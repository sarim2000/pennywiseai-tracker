package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.RuleApplicationDao
import com.pennywiseai.tracker.data.database.dao.RuleDao
import com.pennywiseai.tracker.data.database.entity.RuleApplicationEntity
import com.pennywiseai.tracker.data.database.entity.RuleEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.model.rule.*
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleRepositoryImpl @Inject constructor(
    private val ruleDao: RuleDao,
    private val ruleApplicationDao: RuleApplicationDao,
    private val ruleEngine: RuleEngine
) : RuleRepository {

    override fun getAllRules(): Flow<List<TransactionRule>> {
        return ruleDao.getAllRules().map { entities ->
            entities.map { entity -> entityToRule(entity) }
        }
    }

    override suspend fun getActiveRules(): List<TransactionRule> {
        return ruleDao.getActiveRules().map { entity ->
            entityToRule(entity)
        }
    }

    override suspend fun getActiveRulesByType(type: TransactionType): List<TransactionRule> {
        // Get all active rules and filter in memory based on conditions
        val allActiveRules = ruleDao.getActiveRules()
        return allActiveRules
            .map { entity -> entityToRule(entity) }
            .filter { rule ->
                // Check if rule has no TYPE condition (applies to all) or matches the type
                val hasTypeCondition = rule.conditions.any { it.field == TransactionField.TYPE }
                if (!hasTypeCondition) {
                    true // Rule applies to all transaction types
                } else {
                    // Check if any TYPE condition matches
                    rule.conditions.any { condition ->
                        condition.field == TransactionField.TYPE &&
                        when (condition.operator) {
                            ConditionOperator.EQUALS -> condition.value.equals(type.name, ignoreCase = true)
                            ConditionOperator.IN -> condition.value.split(",")
                                .map { it.trim() }
                                .any { it.equals(type.name, ignoreCase = true) }
                            ConditionOperator.NOT_EQUALS -> !condition.value.equals(type.name, ignoreCase = true)
                            ConditionOperator.NOT_IN -> !condition.value.split(",")
                                .map { it.trim() }
                                .any { it.equals(type.name, ignoreCase = true) }
                            else -> false
                        }
                    }
                }
            }
    }

    override suspend fun getActiveRulesByTypes(types: List<TransactionType>): List<TransactionRule> {
        // Get all active rules and filter in memory based on conditions
        val allActiveRules = ruleDao.getActiveRules()
        val typeNames = types.map { it.name }

        return allActiveRules
            .map { entity -> entityToRule(entity) }
            .filter { rule ->
                // Check if rule has no TYPE condition (applies to all) or matches any of the types
                val hasTypeCondition = rule.conditions.any { it.field == TransactionField.TYPE }
                if (!hasTypeCondition) {
                    true // Rule applies to all transaction types
                } else {
                    // Check if any TYPE condition matches any of the requested types
                    rule.conditions.any { condition ->
                        condition.field == TransactionField.TYPE &&
                        when (condition.operator) {
                            ConditionOperator.EQUALS -> typeNames.any { it.equals(condition.value, ignoreCase = true) }
                            ConditionOperator.IN -> {
                                val conditionTypes = condition.value.split(",").map { it.trim() }
                                typeNames.any { type -> conditionTypes.any { it.equals(type, ignoreCase = true) } }
                            }
                            ConditionOperator.NOT_EQUALS -> typeNames.all { !it.equals(condition.value, ignoreCase = true) }
                            ConditionOperator.NOT_IN -> {
                                val conditionTypes = condition.value.split(",").map { it.trim() }
                                typeNames.any { type -> conditionTypes.none { it.equals(type, ignoreCase = true) } }
                            }
                            else -> false
                        }
                    }
                }
            }
    }

    override suspend fun getRuleById(ruleId: String): TransactionRule? {
        return ruleDao.getRuleById(ruleId)?.let { entityToRule(it) }
    }

    override suspend fun insertRule(rule: TransactionRule) {
        ruleDao.insertRule(ruleToEntity(rule))
    }

    override suspend fun updateRule(rule: TransactionRule) {
        ruleDao.updateRule(ruleToEntity(rule))
    }

    override suspend fun deleteRule(ruleId: String) {
        ruleDao.deleteRuleById(ruleId)
    }

    override suspend fun deleteAllRules() {
        ruleDao.deleteAllRules()
    }

    override suspend fun setRuleActive(ruleId: String, isActive: Boolean) {
        ruleDao.setRuleActive(ruleId, isActive)
    }

    override suspend fun updateRulePriority(ruleId: String, priority: Int) {
        ruleDao.updateRulePriority(ruleId, priority)
    }

    override suspend fun saveRuleApplication(application: RuleApplication) {
        ruleApplicationDao.insertApplication(applicationToEntity(application))
    }

    override suspend fun saveRuleApplications(applications: List<RuleApplication>) {
        ruleApplicationDao.insertApplications(
            applications.map { applicationToEntity(it) }
        )
    }

    override suspend fun getRuleApplicationsForTransaction(transactionId: String): List<RuleApplication> {
        return ruleApplicationDao.getApplicationsByTransaction(transactionId)
            .map { entityToApplication(it) }
    }

    override fun getRuleApplicationsForRule(ruleId: String): Flow<List<RuleApplication>> {
        return ruleApplicationDao.getApplicationsByRule(ruleId).map { entities ->
            entities.map { entityToApplication(it) }
        }
    }

    override suspend fun getRuleApplicationCount(ruleId: String): Int {
        return ruleApplicationDao.getApplicationCountForRule(ruleId)
    }

    private fun ruleToEntity(rule: TransactionRule): RuleEntity {
        return RuleEntity(
            id = rule.id,
            name = rule.name,
            description = rule.description,
            priority = rule.priority,
            conditions = ruleEngine.serializeConditions(rule.conditions),
            actions = ruleEngine.serializeActions(rule.actions),
            isActive = rule.isActive,
            isSystemTemplate = rule.isSystemTemplate,
            createdAt = LocalDateTime.ofEpochSecond(rule.createdAt / 1000, 0, ZoneOffset.UTC),
            updatedAt = LocalDateTime.ofEpochSecond(rule.updatedAt / 1000, 0, ZoneOffset.UTC)
        )
    }

    private fun entityToRule(entity: RuleEntity): TransactionRule {
        return TransactionRule(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            priority = entity.priority,
            conditions = ruleEngine.deserializeConditions(entity.conditions),
            actions = ruleEngine.deserializeActions(entity.actions),
            isActive = entity.isActive,
            isSystemTemplate = entity.isSystemTemplate,
            createdAt = entity.createdAt.toEpochSecond(ZoneOffset.UTC) * 1000,
            updatedAt = entity.updatedAt.toEpochSecond(ZoneOffset.UTC) * 1000
        )
    }

    private fun applicationToEntity(application: RuleApplication): RuleApplicationEntity {
        return RuleApplicationEntity(
            id = application.id,
            ruleId = application.ruleId,
            ruleName = application.ruleName,
            transactionId = application.transactionId,
            fieldsModified = ruleEngine.serializeFieldModifications(application.fieldsModified),
            appliedAt = LocalDateTime.ofEpochSecond(application.appliedAt / 1000, 0, ZoneOffset.UTC)
        )
    }

    private fun entityToApplication(entity: RuleApplicationEntity): RuleApplication {
        return RuleApplication(
            id = entity.id,
            ruleId = entity.ruleId,
            ruleName = entity.ruleName,
            transactionId = entity.transactionId,
            fieldsModified = ruleEngine.deserializeFieldModifications(entity.fieldsModified),
            appliedAt = entity.appliedAt.toEpochSecond(ZoneOffset.UTC) * 1000
        )
    }
}