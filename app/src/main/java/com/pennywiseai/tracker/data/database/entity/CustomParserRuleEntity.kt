package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "custom_parser_rules",
    indices = [
        Index(value = ["priority", "is_active"]),
        Index(value = ["name"])
    ]
)
data class CustomParserRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "sender_pattern")
    val senderPattern: String,

    @ColumnInfo(name = "sample_sms")
    val sampleSms: String,

    @ColumnInfo(name = "amount_regex")
    val amountRegex: String,

    @ColumnInfo(name = "merchant_regex")
    val merchantRegex: String?,

    @ColumnInfo(name = "account_regex")
    val accountRegex: String?,

    @ColumnInfo(name = "expense_keywords")
    val expenseKeywords: String,

    @ColumnInfo(name = "income_keywords")
    val incomeKeywords: String,

    @ColumnInfo(name = "currency")
    val currency: String,

    @ColumnInfo(name = "bank_name_display")
    val bankNameDisplay: String,

    @ColumnInfo(name = "priority")
    val priority: Int = 100,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
