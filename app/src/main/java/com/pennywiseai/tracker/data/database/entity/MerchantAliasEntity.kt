package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Entity(tableName = "merchant_aliases")
@Serializable
data class MerchantAliasEntity(
    @PrimaryKey
    @ColumnInfo(name = "merchant_name")
    val merchantName: String,

    @ColumnInfo(name = "alias")
    val alias: String,

    @ColumnInfo(name = "created_at")
    @Contextual
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    @Contextual
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
