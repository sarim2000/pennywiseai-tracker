package com.pennywiseai.tracker.data.statement

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.domain.model.rule.ActionType
import com.pennywiseai.tracker.domain.model.rule.ConditionOperator
import com.pennywiseai.tracker.domain.model.rule.RuleAction
import com.pennywiseai.tracker.domain.model.rule.RuleCondition
import com.pennywiseai.tracker.domain.model.rule.TransactionField
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleEngine
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** What the rules did to an imported row is stored against the id the insert returned. */
class ImportRulesSourceTest {
    private val ruleRepository = mockk<RuleRepository>(relaxed = true)
    private val tagRepository = mockk<TagRepository>(relaxed = true)
    private val source = ImportRulesSource(mockk(), mockk(), ruleRepository, RuleEngine(), tagRepository)

    private val rule = TransactionRule(
        name = "Coffee",
        conditions = listOf(RuleCondition(TransactionField.MERCHANT, ConditionOperator.CONTAINS, "Coffee")),
        actions = listOf(
            RuleAction(TransactionField.CATEGORY, ActionType.SET, "Cafes"),
            RuleAction(TransactionField.TAGS, ActionType.ADD_TAG, "Weekend")
        )
    )

    /** The applications a new (unsaved, id 0) row gets from [rule]. */
    private fun applications() = (
        ImportRules(emptyMap(), emptyMap(), listOf(rule), RuleEngine()).apply(
            TransactionEntity(
                amount = BigDecimal("120.00"),
                merchantName = "Coffee Hut",
                category = "Others",
                transactionType = TransactionType.EXPENSE,
                dateTime = LocalDateTime.of(2026, 1, 15, 12, 0),
                transactionHash = "synthetic"
            )
        ) as ImportRules.Outcome.Kept
        ).applications

    @Test
    fun `history and tags land on the inserted row's id`() = runBlocking {
        source.record(42L, applications())

        coVerify {
            ruleRepository.saveRuleApplications(
                match { apps -> apps.isNotEmpty() && apps.all { it.transactionId == "42" && it.ruleId == rule.id } }
            )
        }
        coVerify { tagRepository.applyTagChanges(42L, listOf("Weekend"), emptyList()) }
    }

    @Test
    fun `a row the insert ignored records nothing`() = runBlocking {
        val applications = applications()
        assertTrue(applications.isNotEmpty())

        source.record(-1L, applications)

        coVerify(exactly = 0) { ruleRepository.saveRuleApplications(any()) }
        coVerify(exactly = 0) { tagRepository.applyTagChanges(any(), any(), any()) }
    }
}
