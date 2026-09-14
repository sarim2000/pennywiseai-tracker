package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
@Serializable
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "color")
    val color: String,

    // Optional emoji shown in place of the built-in icon (#760). Null = default
    // icon. Nullable + defaulted so old backups restore (#414).
    @ColumnInfo(name = "icon")
    val icon: String? = null,

    // Sub-categories (#374): id of the top-level parent, or null for a
    // top-level category. One level deep by construction (the editor only
    // offers top-level parents). Nullable + defaulted so old backups restore.
    @ColumnInfo(name = "parent_id")
    val parentId: Long? = null,
    
    @ColumnInfo(name = "is_system")
    val isSystem: Boolean = false,
    
    @ColumnInfo(name = "is_income")
    val isIncome: Boolean = false,

    // Hidden categories are kept in the DB (so existing transactions keep their
    // category and still show in analytics) but are filtered out of the category
    // PICKERS. Lets users tuck away unused default categories (#736). Backup-safe:
    // a default keeps old backups restorable (#414).
    @ColumnInfo(name = "is_hidden", defaultValue = "0")
    val isHidden: Boolean = false,

    @ColumnInfo(name = "display_order")
    val displayOrder: Int = 999,
    
    @ColumnInfo(name = "created_at")
    @Contextual
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    @Contextual
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

/** Top-level categories in their order, each followed by its children in theirs. */
fun List<CategoryEntity>.hierarchical(): List<CategoryEntity> {
    val ids = mapTo(HashSet()) { it.id }
    val children = filter { it.parentId != null && it.parentId in ids }.groupBy { it.parentId!! }
    return flatMap { c -> if (c.parentId != null && c.parentId in ids) emptyList() else listOf(c) + children[c.id].orEmpty() }
}

/** child name → parent name, for rolling sub-categories up into their parent. */
fun List<CategoryEntity>.parentNameOf(): Map<String, String> {
    val byId = associateBy { it.id }
    return mapNotNull { c -> c.parentId?.let { byId[it] }?.let { c.name to it.name } }.toMap()
}

/** [names] plus every child of a named parent — a filter on "Food" includes "Coffee". */
fun List<CategoryEntity>.expandWithChildren(names: Collection<String>): Set<String> {
    val byName = associateBy { it.name }
    val parentIds = names.mapNotNull { byName[it]?.takeIf { c -> c.parentId == null }?.id }.toSet()
    return names.toSet() + filter { it.parentId in parentIds }.map { it.name }
}

