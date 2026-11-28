package com.pennywiseai.tracker.data.statement

import com.pennywiseai.tracker.data.database.dao.CategoryDao
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.repository.MerchantMappingRepository
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.data.repository.mappingFits
import com.pennywiseai.tracker.domain.model.rule.RuleApplication
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import com.pennywiseai.tracker.domain.model.rule.tagChanges
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleEngine
import javax.inject.Inject

/**
 * The learned merchant categories and active rules, read once per file import
 * and applied to the rows it adds the way an SMS scan applies them: the
 * mapping first, so a rule has the last word. A file has no SMS text, so an
 * "SMS text" condition never matches.
 */
internal class ImportRules(
    private val merchantCategories: Map<String, String>,
    private val categoryIsIncome: Map<String, Boolean>,
    private val rules: List<TransactionRule>,
    private val ruleEngine: RuleEngine
) {
    sealed interface Outcome {
        /** A block rule matched: the row isn't added. */
        data object Blocked : Outcome
        data class Kept(val entity: TransactionEntity, val applications: List<RuleApplication>) : Outcome
    }

    /** [categorize] false keeps a category the file chose itself; block rules still apply. */
    fun apply(entity: TransactionEntity, categorize: Boolean = true): Outcome {
        val mapped = merchantCategories[entity.merchantName]
            ?.takeIf { categorize && mappingFits(categoryIsIncome[it], entity.transactionType) }
            ?.let { entity.copy(category = it) }
            ?: entity
        if (ruleEngine.shouldBlockTransaction(mapped, null, rules) != null) return Outcome.Blocked
        if (!categorize) return Outcome.Kept(mapped, emptyList())
        val (ruled, applications) = ruleEngine.evaluateRules(mapped, null, rules)
        return Outcome.Kept(ruled, applications)
    }

    companion object {
        val NONE = ImportRules(emptyMap(), emptyMap(), emptyList(), RuleEngine())
    }
}

/** Reads [ImportRules] for an import and records what they did to the rows it added. */
class ImportRulesSource @Inject constructor(
    private val merchantMappingRepository: MerchantMappingRepository,
    private val categoryDao: CategoryDao,
    private val ruleRepository: RuleRepository,
    private val ruleEngine: RuleEngine,
    private val tagRepository: TagRepository
) {
    internal suspend fun load(): ImportRules = ImportRules(
        merchantCategories = merchantMappingRepository.getAllMappingsAsMap(),
        categoryIsIncome = categoryDao.getAllCategoriesList().associate { it.name to it.isIncome },
        rules = ruleRepository.getActiveRules(),
        ruleEngine = ruleEngine
    )

    /** Saves [applications] against the added row [transactionId], tag actions included. */
    suspend fun record(transactionId: Long, applications: List<RuleApplication>) {
        if (transactionId == -1L || applications.isEmpty()) return
        ruleRepository.saveRuleApplications(applications.map { it.copy(transactionId = transactionId.toString()) })
        // ADD_TAG / REMOVE_TAG live in the tag table, not on the row (#748).
        val (add, remove) = applications.tagChanges()
        if (add.isNotEmpty() || remove.isNotEmpty()) tagRepository.applyTagChanges(transactionId, add, remove)
    }
}
