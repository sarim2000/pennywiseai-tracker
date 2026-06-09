package com.pennywiseai.shared.data.bootstrap

object DefaultCategoryData {
    data class CategorySeed(val name: String, val colorHex: String, val isIncome: Boolean)

    val ALL: List<CategorySeed> = listOf(
        CategorySeed("Food & Dining",      "#FC8019", false),
        CategorySeed("Groceries",          "#5AC85A", false),
        CategorySeed("Transportation",     "#000000", false),
        CategorySeed("Shopping",           "#FF9900", false),
        CategorySeed("Bills & Utilities",  "#4CAF50", false),
        CategorySeed("Entertainment",      "#E50914", false),
        CategorySeed("Healthcare",         "#10847E", false),
        CategorySeed("Investments",        "#00D09C", false),
        CategorySeed("Banking",            "#004C8F", false),
        CategorySeed("Personal Care",      "#6A4C93", false),
        CategorySeed("Education",          "#673AB7", false),
        CategorySeed("Mobile",             "#2A3890", false),
        CategorySeed("Fitness",            "#FF3278", false),
        CategorySeed("Insurance",          "#0066CC", false),
        CategorySeed("Travel",             "#00BCD4", false),
        CategorySeed("Salary",             "#4CAF50", true),
        CategorySeed("Income",             "#4CAF50", true),
        CategorySeed("Others",             "#757575", false)
    )
}
