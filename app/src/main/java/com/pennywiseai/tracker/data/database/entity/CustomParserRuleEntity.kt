package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Room Entity representing a user-defined custom parsing rule.
 * 
 * This table stores custom Regular Expressions (Regex) and capture group indexes
 * that allow PennyWise to parse transactions from Google Pay or bank notifications (like SBI)
 * locally and dynamically without hardcoded code changes.
 * 
 * @Entity registers this class as a table in SQLite.
 * @Serializable allows exporting/importing this data (e.g. for backups).
 */
@Entity(tableName = "custom_parser_rules")
@Serializable
data class CustomParserRuleEntity(
    // Primary key that automatically increments starting from 1
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    // App package name (e.g. "com.google.android.apps.nbu.paisa.user") or SMS sender alias (e.g. "SBI")
    @ColumnInfo(name = "package_or_sender")
    val packageOrSender: String = "",

    // Friendly name designated by the user (e.g. "GPay Custom Pattern")
    @ColumnInfo(name = "rule_name")
    val ruleName: String = "",

    // The compiled Regular Expression string (e.g. "^You paid ₹([0-9,.]+) to (.*?)$")
    @ColumnInfo(name = "regex_pattern")
    val regexPattern: String = "",

    // The index of the regex capture group containing the Amount (e.g., 1)
    @ColumnInfo(name = "amount_group_index")
    val amountGroupIndex: Int = 1,

    // The index of the regex capture group containing the Merchant/Payee name (e.g., 2)
    @ColumnInfo(name = "merchant_group_index")
    val merchantGroupIndex: Int = 2,

    // The transaction type (either "EXPENSE" or "INCOME")
    @ColumnInfo(name = "type")
    val type: String = "EXPENSE",

    // The index of the regex capture group containing the account/card last 4 digits (-1 if none)
    @ColumnInfo(name = "account_group_index")
    val accountGroupIndex: Int = -1,

    // Boolean flag to enable/disable this rule
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    // Time in milliseconds when the rule was created
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
