package com.pennywiseai.shared.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shared_merchant_mappings")
data class SharedMerchantMappingEntity(
    @PrimaryKey
    @ColumnInfo(name = "merchant_name")
    val merchantName: String,
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long
)
