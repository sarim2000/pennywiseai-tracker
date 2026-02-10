package com.pennywiseai.tracker.navigation

import kotlinx.serialization.Serializable

// Define navigation destinations using Kotlin Serialization
@Serializable
object AppLock

@Serializable
object Permission

@Serializable
object Home

@Serializable
data class HomeWithCategoryFilter(
    val category: String,
    val period: String,
    val currency: String
)

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
object AddTransaction

@Serializable
data class AccountDetail(val bankName: String, val accountLast4: String)

@Serializable
object UnrecognizedSms

@Serializable
object Faq

@Serializable
object Rules

@Serializable
data class CreateRule(val ruleId: String? = null)

@Serializable
object MonthlyBudget

@Serializable
object MonthlyBudgetSettings

@Serializable
object ExchangeRates

@Serializable
object BudgetGroups

@Serializable
data class BudgetGroupEdit(val groupId: Long = -1L)