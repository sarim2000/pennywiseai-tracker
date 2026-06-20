package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * A user-defined, free-text tag that can be attached to any number of
 * transactions (and a transaction can have any number of tags). Tags are
 * created on demand from the transaction add/edit screens — typing a new name
 * creates a row here, while an existing name is reused.
 *
 * Backup contract: every field has a Kotlin default so an older backup that
 * omits a column still restores (see docs/backup-format.md).
 */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
@Serializable
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String = "",

    @ColumnInfo(name = "created_at")
    @Contextual
    val createdAt: LocalDateTime = LocalDateTime.now()
)

/**
 * Junction table implementing the many-to-many relationship between
 * transactions and tags. Rows are removed automatically (CASCADE) when either
 * the transaction or the tag is deleted.
 */
@Entity(
    tableName = "transaction_tag_cross_ref",
    primaryKeys = ["transaction_id", "tag_id"],
    indices = [
        Index(value = ["transaction_id"]),
        Index(value = ["tag_id"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@Serializable
data class TransactionTagCrossRef(
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long = 0,

    @ColumnInfo(name = "tag_id")
    val tagId: Long = 0
)

/**
 * Lightweight projection used to load all (transaction, tagName) pairs in a
 * single query, for in-memory tag search and tag analytics aggregation.
 */
data class TransactionTagName(
    @ColumnInfo(name = "transactionId")
    val transactionId: Long,

    @ColumnInfo(name = "name")
    val name: String
)
