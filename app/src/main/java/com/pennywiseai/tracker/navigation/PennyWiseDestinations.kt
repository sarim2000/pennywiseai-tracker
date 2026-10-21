package com.pennywiseai.tracker.navigation

import kotlinx.serialization.Serializable

// Define navigation destinations using Kotlin Serialization
@Serializable
object AppLock

@Serializable
object OnBoarding

@Serializable
object Permission

@Serializable
object Home

@Serializable
object Transactions

@Serializable
object Settings

@Serializable
object Categories

@Serializable
object Analytics

@Serializable
object Chat

@Serializable
data class TransactionDetail(val transactionId: Long)

@Serializable
data class AddTransaction(val sourceTransactionId: Long? = null, val sharedText: String? = null)

@Serializable
data class AccountDetail(val bankName: String, val accountLast4: String)

@Serializable
object UnrecognizedSms

@Serializable
object Faq

@Serializable
object Rules

@Serializable
data class CreateRule(val ruleId: String? = null, val duplicateFromId: String? = null)

@Serializable
object ExchangeRates

/** The Budgets screen; [createGoal] starts "New goal" on open (Pro gate included), as the goals widget's "+" does. */
@Serializable
data class BudgetGroups(val createGoal: Boolean = false)

@Serializable
data class BudgetGroupEdit(val groupId: Long = -1L)

/**
 * Per-budget history drill-down. The (year, month) selects the period;
 * the screen renders the per-window list (Weekly sub-list or Monthly /
 * One-time window) with each row labelled "Live" or "Frozen as of …".
 */
@Serializable
data class BudgetHistory(
    val groupId: Long,
    val year: Int,
    val month: Int
)

/** A savings Goal's own screen (#914). */
@Serializable
data class GoalDetail(val goalId: Long)

/** Create (goalId = -1) or edit a Goal. */
@Serializable
data class GoalEdit(val goalId: Long = -1L)

@Serializable
object Loans

@Serializable
object RecurringTransactions

@Serializable
data class LoanDetail(val loanId: Long)

@Serializable
object TransactionGroups

@Serializable
data class TransactionGroupDetail(val groupId: Long)

@Serializable
object ImportStatement

@Serializable
object Webhooks

@Serializable
object AiAccess

@Serializable
data class TransactionsWithFilter(
    val category: String,
    val period: String? = null,
    val currency: String? = null
)
