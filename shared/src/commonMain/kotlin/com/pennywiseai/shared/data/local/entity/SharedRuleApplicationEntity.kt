package com.pennywiseai.shared.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
