package com.pennywiseai.shared.data.model

enum class SharedTransactionType {
    INCOME,
    EXPENSE,
    CREDIT,
    TRANSFER,
    INVESTMENT;

    companion object {
        fun fromStorage(value: String): SharedTransactionType =
            entries.firstOrNull { it.name == value } ?: EXPENSE
    }
}
