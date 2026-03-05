package com.pennywiseai.shared.data.model

data class SharedCategory(
    val id: Long = 0L,
    val name: String,
    val colorHex: String,
    val isSystem: Boolean = false,
    val isIncome: Boolean = false,
    val displayOrder: Int = 0,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
