package com.pennywiseai.tracker.data.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import kotlinx.serialization.Serializable

/**
 * A transaction the user took out of a trip (#1463), kept by hash so it stays out
 * after an SMS Rebuild deletes and re-creates the row.
 */
@Entity(
    tableName = "trip_exclusions",
    primaryKeys = ["group_id", "transaction_hash"],
    foreignKeys = [
        ForeignKey(
            entity = TransactionGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@Serializable
data class TripExclusionEntity(
    @ColumnInfo(name = "group_id")
    val groupId: Long = 0,

    @ColumnInfo(name = "transaction_hash")
    val transactionHash: String = ""
)
