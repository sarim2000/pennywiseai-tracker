package com.pennywiseai.tracker.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

// Navigation 3 keys: @Serializable so rememberNavBackStack survives process death.
// Their properties are also handed to each entry's SavedStateHandle (see RouteArgs).
@Serializable
data object AppLock : NavKey

@Serializable
data object OnBoarding : NavKey

@Serializable
data object Permission : NavKey

@Serializable
data object Home : NavKey

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
    val tag: String? = null,
    // Set by an explicit open from Home ("See all", search): a new entry, so the
    // list's ViewModel starts over instead of keeping filters picked in the tab.
    val openId: Long = 0L
) : NavKey

@Serializable
data object Settings : NavKey

/** A page of the Settings hub. */
@Serializable
enum class SettingsSection { TRACKING, ACCOUNTS, PLANNING, CURRENCY, FORMATS, NOTIFICATIONS, SECURITY, DATA, AI, DEVELOPER }

@Serializable
data class SettingsPage(val section: SettingsSection) : NavKey

@Serializable
data object Categories : NavKey

@Serializable
data object Analytics : NavKey

@Serializable
data object Chat : NavKey

@Serializable
data class TransactionDetail(val transactionId: Long) : NavKey

@OptIn(ExperimentalSerializationApi::class)
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
    val unrecognizedSmsId: Long? = null,
    // Unique per open, so two Add screens on the stack never share one ViewModel/draft
    // (Navigation 3 keys an entry's ViewModels by its key). Always encoded, so the
    // same value comes back after process death.
    @EncodeDefault val openId: Long = System.nanoTime(),
) : NavKey

@Serializable
data class AccountDetail(val bankName: String, val accountLast4: String) : NavKey

@Serializable
data object UnrecognizedSms : NavKey

@Serializable
data object Faq : NavKey

@Serializable
data object Rules : NavKey

@Serializable
data class CreateRule(val ruleId: String? = null, val duplicateFromId: String? = null) : NavKey

@Serializable
data object ExchangeRates : NavKey

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
data object Loans : NavKey

@Serializable
data object RecurringTransactions : NavKey

@Serializable
data class LoanDetail(val loanId: Long) : NavKey

@Serializable
data object TransactionGroups : NavKey

@Serializable
data class TransactionGroupDetail(val groupId: Long) : NavKey

@Serializable
data object ImportStatement : NavKey

@Serializable
data object Webhooks : NavKey

@Serializable
data object AiAccess : NavKey

@Serializable
data class TransactionsWithFilter(
    val category: String,
    val period: String? = null,
    val currency: String? = null
) : NavKey

// Screens inside MainScreen's tab stacks (Settings, SettingsPage, Categories, Faq,
// UnrecognizedSms and ImportStatement live only there).
@Serializable
data object Subscriptions : NavKey

@Serializable
data object Appearance : NavKey

@Serializable
data object ManageAccounts : NavKey

@Serializable
data object AddAccount : NavKey

@Serializable
data class BalanceHistory(val bankName: String, val accountLast4: String) : NavKey
