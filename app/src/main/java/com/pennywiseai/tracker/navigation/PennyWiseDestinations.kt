package com.pennywiseai.tracker.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Navigation 3 keys: @Serializable so rememberNavBackStack survives process death.
// Their properties are also handed to each entry's SavedStateHandle (see RouteArgs).
@Serializable
object AppLock : NavKey

@Serializable
object OnBoarding : NavKey

@Serializable
object Permission : NavKey

@Serializable
object Home : NavKey

/** The Transactions tab; any filter set means a drill-down (shows a back arrow). */
@Serializable
data class Transactions(
    val category: String? = null,
    val merchant: String? = null,
    val period: String? = null,
    val currency: String? = null,
    val focusSearch: Boolean = false,
    val type: String? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val account: String? = null,
    val tag: String? = null
) : NavKey

@Serializable
object Settings : NavKey

@Serializable
object Categories : NavKey

@Serializable
object Analytics : NavKey

@Serializable
object Chat : NavKey

@Serializable
data class TransactionDetail(val transactionId: Long) : NavKey

@Serializable
data class AddTransaction(
    val sourceTransactionId: Long? = null,
    val sharedText: String? = null,
    // The quick add sheet's draft ("More details"): what was typed there.
    val amount: String? = null,
    val merchant: String? = null,
    val category: String? = null,
    val bankName: String? = null,
    val accountLast4: String? = null,
    // A suggestion from Unrecognized SMS (#994): its currency, type and SMS time,
    // and the SMS row to hide once the transaction is saved.
    val currency: String? = null,
    val type: String? = null,
    val dateTime: String? = null,
    val unrecognizedSmsId: Long? = null
) : NavKey

@Serializable
data class AccountDetail(val bankName: String, val accountLast4: String) : NavKey

@Serializable
object UnrecognizedSms : NavKey

@Serializable
object Faq : NavKey

@Serializable
object Rules : NavKey

@Serializable
data class CreateRule(val ruleId: String? = null, val duplicateFromId: String? = null) : NavKey

@Serializable
object ExchangeRates : NavKey

/** The Budgets screen; [createGoal] starts "New goal" on open (Pro gate included), as the goals widget's "+" does. */
@Serializable
data class BudgetGroups(
    val createGoal: Boolean = false,
    // Distinct per widget tap: entries are keyed by value, so a repeated "+" needs
    // a new key to get a fresh screen (and New goal again).
    val tapId: Long = 0L
) : NavKey

@Serializable
data class BudgetGroupEdit(val groupId: Long = -1L) : NavKey

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
) : NavKey

/** A savings Goal's own screen (#914). */
@Serializable
data class GoalDetail(val goalId: Long) : NavKey

/** Create (goalId = -1) or edit a Goal. */
@Serializable
data class GoalEdit(val goalId: Long = -1L) : NavKey

@Serializable
object Loans : NavKey

@Serializable
object RecurringTransactions : NavKey

@Serializable
data class LoanDetail(val loanId: Long) : NavKey

@Serializable
object TransactionGroups : NavKey

@Serializable
data class TransactionGroupDetail(val groupId: Long) : NavKey

@Serializable
object ImportStatement : NavKey

@Serializable
object Webhooks : NavKey

@Serializable
object AiAccess : NavKey

@Serializable
data class TransactionsWithFilter(
    val category: String,
    val period: String? = null,
    val currency: String? = null
) : NavKey

// Screens inside MainScreen's tab stacks (Home, Settings, Categories, Faq,
// UnrecognizedSms and ImportStatement are reused there too).
@Serializable
object Subscriptions : NavKey

@Serializable
object Appearance : NavKey

@Serializable
object ManageAccounts : NavKey

@Serializable
object AddAccount : NavKey

@Serializable
data class BalanceHistory(val bankName: String, val accountLast4: String) : NavKey
