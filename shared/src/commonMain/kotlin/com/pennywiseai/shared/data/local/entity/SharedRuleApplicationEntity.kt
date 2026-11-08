package com.pennywiseai.shared.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "shared_rule_applications",
    foreignKeys = [
        ForeignKey(
            entity = SharedRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["rule_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["rule_id"]), Index(value = ["transaction_id"])]
)
data class SharedRuleApplicationEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "rule_id")
    val ruleId: String,
    @ColumnInfo(name = "rule_name")
    val ruleName: String,
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,
    @ColumnInfo(name = "fields_modified_json")
    val fieldsModifiedJson: String,
    @ColumnInfo(name = "applied_at_epoch_millis")
    val appliedAtEpochMillis: Long
)
