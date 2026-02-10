package com.pennywiseai.tracker.domain.service

import com.pennywiseai.tracker.domain.model.rule.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleTemplateService @Inject constructor() {

    fun getDefaultRuleTemplates(): List<TransactionRule> {
        return listOf(
            // Just one simple example rule to get users started
            createSmallPaymentsToFoodRule()
        )
    }

    private fun createSmallPaymentsToFoodRule(): TransactionRule {
        return TransactionRule(
            id = UUID.randomUUID().toString(),
            name = "Small Payments to Food",
            description = "Categorize small expense payments (under 200) as Food & Dining",
            priority = 100,
            conditions = listOf(
                RuleCondition(
                    field = TransactionField.AMOUNT,
                    operator = ConditionOperator.LESS_THAN,
                    value = "200",
                    logicalOperator = LogicalOperator.AND
                ),
                RuleCondition(
                    field = TransactionField.TYPE,
                    operator = ConditionOperator.EQUALS,
                    value = "EXPENSE",
                    logicalOperator = LogicalOperator.AND
                )
            ),
            actions = listOf(
                RuleAction(
                    field = TransactionField.CATEGORY,
                    actionType = ActionType.SET,
                    value = "Food & Dining"
                )
            ),
            isActive = false, // Users can enable this
            isSystemTemplate = true
        )
    }

    private fun createUpiCashbackRule(): TransactionRule {
        return TransactionRule(
            id = UUID.randomUUID().toString(),
            name = "UPI Cashback",
            description = "Identify small UPI receipts (under 10) from NPCI as cashback",
            priority = 50,
            conditions = listOf(
                RuleCondition(
                    field = TransactionField.AMOUNT,
                    operator = ConditionOperator.LESS_THAN,
                    value = "10",
                    logicalOperator = LogicalOperator.AND
                ),
                RuleCondition(
                    field = TransactionField.TYPE,
                    operator = ConditionOperator.EQUALS,
                    value = "INCOME",
                    logicalOperator = LogicalOperator.AND
                ),
                RuleCondition(
                    field = TransactionField.SMS_TEXT,
                    operator = ConditionOperator.CONTAINS,
                    value = "NPCI",
                    logicalOperator = LogicalOperator.AND
                )
            ),
            actions = listOf(
                RuleAction(
                    field = TransactionField.CATEGORY,
                    actionType = ActionType.SET,
                    value = "Cashback"
                )
            ),
            isActive = false
        )
    }

    private fun createSalaryDetectionRule(): TransactionRule {
        return TransactionRule(
            id = UUID.randomUUID().toString(),
            name = "Salary Detection",
            description = "Detect salary credits based on keywords",
            priority = 75,
            conditions = listOf(
                RuleCondition(
                    field = TransactionField.TYPE,
                    operator = ConditionOperator.EQUALS,
                    value = "INCOME",
                    logicalOperator = LogicalOperator.AND
                ),
                RuleCondition(
                    field = TransactionField.SMS_TEXT,
                    operator = ConditionOperator.REGEX_MATCHES,
                    value = "(?i)(salary|sal|stipend|wages|payroll)",
                    logicalOperator = LogicalOperator.OR
                ),
                RuleCondition(
                    field = TransactionField.NARRATION,
                    operator = ConditionOperator.REGEX_MATCHES,
                    value = "(?i)(salary|sal|stipend|wages|payroll)",
                    logicalOperator = LogicalOperator.AND
                )
            ),
            actions = listOf(
                RuleAction(
                    field = TransactionField.CATEGORY,
                    actionType = ActionType.SET,
                    value = "Salary"
                )
            ),
            isActive = false
        )
    }

    private fun createRentPaymentRule(): TransactionRule {
        return TransactionRule(
            id = UUID.randomUUID().toString(),
            name = "Rent Payment Detection",
            description = "Identify rent payments based on keywords and amount patterns",
            priority = 80,
            conditions = listOf(
                RuleCondition(
                    field = TransactionField.TYPE,
                    operator = ConditionOperator.EQUALS,
                    value = "EXPENSE",
                    logicalOperator = LogicalOperator.AND
                ),
                RuleCondition(
                    field = TransactionField.SMS_TEXT,
                    operator = ConditionOperator.REGEX_MATCHES,
                    value = "(?i)(rent|landlord|house owner|flat|apartment)",
                    logicalOperator = LogicalOperator.OR
                ),
                RuleCondition(
                    field = TransactionField.NARRATION,
                    operator = ConditionOperator.REGEX_MATCHES,
                    value = "(?i)(rent|landlord|house owner)",
                    logicalOperator = LogicalOperator.AND
                )
            ),
            actions = listOf(
                RuleAction(
                    field = TransactionField.CATEGORY,
                    actionType = ActionType.SET,
                    value = "Housing"
                )
            ),
            isActive = false
        )
    }

    private fun createInvestmentDetectionRule(): TransactionRule {
        return TransactionRule(
            id = UUID.randomUUID().toString(),
            name = "Investment Detection",
            description = "Categorize mutual funds, stocks, and other investments",
            priority = 85,
            conditions = listOf(
                RuleCondition(
                    field = TransactionField.SMS_TEXT,
                    operator = ConditionOperator.REGEX_MATCHES,
                    value = "(?i)(mutual fund|mf|sip|zerodha|groww|upstox|paytm money|kuvera|et money|stocks|shares|demat)",
                    logicalOperator = LogicalOperator.OR
                ),
                RuleCondition(
                    field = TransactionField.MERCHANT,
                    operator = ConditionOperator.REGEX_MATCHES,
                    value = "(?i)(zerodha|groww|upstox|paytm money|kuvera|et money|hdfc securities|icici direct)",
                    logicalOperator = LogicalOperator.AND
                )
            ),
            actions = listOf(
                RuleAction(
                    field = TransactionField.CATEGORY,
                    actionType = ActionType.SET,
                    value = "Investments"
                )
            ),
            isActive = false
        )
    }

    private fun createEmiDetectionRule(): TransactionRule {
        return TransactionRule(
            id = UUID.randomUUID().toString(),
            name = "EMI Detection",
            description = "Identify EMI payments",
            priority = 90,
            conditions = listOf(
                RuleCondition(
                    field = TransactionField.TYPE,
                    operator = ConditionOperator.EQUALS,
                    value = "EXPENSE",
                    logicalOperator = LogicalOperator.AND
                ),
                RuleCondition(
                    field = TransactionField.SMS_TEXT,
                    operator = ConditionOperator.REGEX_MATCHES,
                    value = "(?i)(emi|equated monthly|installment|loan)",
                    logicalOperator = LogicalOperator.OR
                ),
                RuleCondition(
                    field = TransactionField.NARRATION,
                    operator = ConditionOperator.CONTAINS,
                    value = "EMI",
                    logicalOperator = LogicalOperator.AND
                )
            ),
            actions = listOf(
                RuleAction(
                    field = TransactionField.CATEGORY,
                    actionType = ActionType.SET,
                    value = "EMI"
                )
            ),
            isActive = false
        )
    }

    private fun createTransferCategorizationRule(): TransactionRule {
        return TransactionRule(
            id = UUID.randomUUID().toString(),
            name = "Transfer Detection",
            description = "Mark contra and transfer transactions",
            priority = 95,
            conditions = listOf(
                RuleCondition(
                    field = TransactionField.NARRATION,
                    operator = ConditionOperator.REGEX_MATCHES,
                    value = "(?i)(contra|transfer|trf|self)",
                    logicalOperator = LogicalOperator.OR
                ),
                RuleCondition(
                    field = TransactionField.SMS_TEXT,
                    operator = ConditionOperator.REGEX_MATCHES,
                    value = "(?i)(transfer to self|own account|linked account)",
                    logicalOperator = LogicalOperator.AND
                )
            ),
            actions = listOf(
                RuleAction(
                    field = TransactionField.TYPE,
                    actionType = ActionType.SET,
                    value = "transfer"
                ),
                RuleAction(
                    field = TransactionField.CATEGORY,
                    actionType = ActionType.SET,
                    value = "Transfer"
                )
            ),
            isActive = false
        )
    }

    private fun createSubscriptionDetectionRule(): TransactionRule {
        return TransactionRule(
            id = UUID.randomUUID().toString(),
            name = "Subscription Detection",
            description = "Identify recurring subscriptions like Netflix, Spotify, etc.",
            priority = 105,
            conditions = listOf(
                RuleCondition(
                    field = TransactionField.MERCHANT,
                    operator = ConditionOperator.REGEX_MATCHES,
                    value = "(?i)(netflix|spotify|amazon prime|hotstar|youtube|apple|google|microsoft|adobe)",
                    logicalOperator = LogicalOperator.OR
                ),
                RuleCondition(
                    field = TransactionField.SMS_TEXT,
                    operator = ConditionOperator.REGEX_MATCHES,
                    value = "(?i)(subscription|recurring|auto-debit|mandate)",
                    logicalOperator = LogicalOperator.AND
                )
            ),
            actions = listOf(
                RuleAction(
                    field = TransactionField.CATEGORY,
                    actionType = ActionType.SET,
                    value = "Entertainment"
                )
            ),
            isActive = false
        )
    }

    private fun createFuelDetectionRule(): TransactionRule {
        return TransactionRule(
            id = UUID.randomUUID().toString(),
            name = "Fuel/Petrol Detection",
            description = "Categorize fuel and petrol pump transactions",
            priority = 110,
            conditions = listOf(
                RuleCondition(
                    field = TransactionField.MERCHANT,
                    operator = ConditionOperator.REGEX_MATCHES,
                    value = "(?i)(indian oil|bharat petroleum|hp|hindustan petroleum|bpcl|iocl|shell|essar|reliance|petrol|diesel|fuel|pump)",
                    logicalOperator = LogicalOperator.OR
                ),
                RuleCondition(
                    field = TransactionField.SMS_TEXT,
                    operator = ConditionOperator.REGEX_MATCHES,
                    value = "(?i)(petrol|diesel|fuel|pump|filling station)",
                    logicalOperator = LogicalOperator.AND
                )
            ),
            actions = listOf(
                RuleAction(
                    field = TransactionField.CATEGORY,
                    actionType = ActionType.SET,
                    value = "Transportation"
                )
            ),
            isActive = false
        )
    }

    private fun createHealthcareDetectionRule(): TransactionRule {
        return TransactionRule(
            id = UUID.randomUUID().toString(),
            name = "Healthcare Detection",
            description = "Identify medical and healthcare expenses",
            priority = 115,
            conditions = listOf(
                RuleCondition(
                    field = TransactionField.MERCHANT,
                    operator = ConditionOperator.REGEX_MATCHES,
                    value = "(?i)(apollo|fortis|max|medanta|aiims|hospital|clinic|pharmacy|medical|pharma|netmeds|1mg|pharmeasy)",
                    logicalOperator = LogicalOperator.OR
                ),
                RuleCondition(
                    field = TransactionField.SMS_TEXT,
                    operator = ConditionOperator.REGEX_MATCHES,
                    value = "(?i)(hospital|doctor|medical|medicine|pharmacy|health|diagnostic|lab|test)",
                    logicalOperator = LogicalOperator.AND
                )
            ),
            actions = listOf(
                RuleAction(
                    field = TransactionField.CATEGORY,
                    actionType = ActionType.SET,
                    value = "Healthcare"
                )
            ),
            isActive = false
        )
    }
}