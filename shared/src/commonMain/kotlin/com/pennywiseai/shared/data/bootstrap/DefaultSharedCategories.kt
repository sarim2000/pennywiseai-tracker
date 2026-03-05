package com.pennywiseai.shared.data.bootstrap

import com.pennywiseai.shared.data.model.SharedCategory

internal object DefaultSharedCategories {
    fun create(nowEpochMillis: Long): List<SharedCategory> {
        val rows = listOf(
            Triple("Food & Dining", "#FC8019", false),
            Triple("Groceries", "#5AC85A", false),
            Triple("Transportation", "#000000", false),
            Triple("Shopping", "#FF9900", false),
            Triple("Bills & Utilities", "#4CAF50", false),
            Triple("Entertainment", "#E50914", false),
            Triple("Healthcare", "#10847E", false),
            Triple("Investments", "#00D09C", false),
            Triple("Banking", "#004C8F", false),
            Triple("Personal Care", "#6A4C93", false),
            Triple("Education", "#673AB7", false),
            Triple("Mobile", "#2A3890", false),
            Triple("Fitness", "#FF3278", false),
            Triple("Insurance", "#0066CC", false),
            Triple("Travel", "#00BCD4", false),
            Triple("Salary", "#4CAF50", true),
            Triple("Income", "#4CAF50", true),
            Triple("Others", "#757575", false)
        )

        return rows.mapIndexed { index, (name, color, isIncome) ->
            SharedCategory(
                name = name,
                colorHex = color,
                isSystem = true,
                isIncome = isIncome,
                displayOrder = index + 1,
                createdAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis
            )
        }
    }
}
