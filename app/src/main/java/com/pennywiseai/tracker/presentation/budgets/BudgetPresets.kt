package com.pennywiseai.tracker.presentation.budgets

data class BudgetPreset(
    val name: String,
    val icon: String,
    val description: String,
    val categories: List<String>,
    val includeAllCategories: Boolean = false,
    val color: String
)

object BudgetPresets {
    val TOTAL_SPENDING = BudgetPreset(
        name = "Total Spending",
        icon = "💰",
        description = "Track all your expenses",
        categories = emptyList(),
        includeAllCategories = true,
        color = "#1565C0"
    )

    val FOOD = BudgetPreset(
        name = "Food & Dining",
        icon = "🍔",
        description = "Food, Groceries, Dining Out",
        categories = listOf("Food & Dining", "Groceries"),
        color = "#FF7043"
    )

    val ENTERTAINMENT = BudgetPreset(
        name = "Entertainment",
        icon = "🎬",
        description = "Entertainment, Subscriptions",
        categories = listOf("Entertainment", "Subscriptions"),
        color = "#E50914"
    )

    val SHOPPING = BudgetPreset(
        name = "Shopping",
        icon = "🛍️",
        description = "Shopping, Personal Care",
        categories = listOf("Shopping", "Personal Care"),
        color = "#AB47BC"
    )

    val TRANSPORT = BudgetPreset(
        name = "Transport",
        icon = "🚗",
        description = "Transportation expenses",
        categories = listOf("Transportation"),
        color = "#5C6BC0"
    )

    val CUSTOM = BudgetPreset(
        name = "Custom Budget",
        icon = "✨",
        description = "Choose your own categories",
        categories = emptyList(),
        color = "#455A64"
    )

    val ALL_PRESETS = listOf(
        TOTAL_SPENDING,
        FOOD,
        ENTERTAINMENT,
        SHOPPING,
        TRANSPORT,
        CUSTOM
    )
}
