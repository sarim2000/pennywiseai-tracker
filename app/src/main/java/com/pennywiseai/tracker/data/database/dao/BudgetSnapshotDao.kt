package com.pennywiseai.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryMonthSnapshotEntity
import com.pennywiseai.tracker.data.database.entity.BudgetMonthSnapshotEntity

@Dao
interface BudgetSnapshotDao {

    @Insert
    suspend fun insertGroupSnapshots(snapshots: List<BudgetMonthSnapshotEntity>)

    @Insert
    suspend fun insertCategorySnapshots(snapshots: List<BudgetCategoryMonthSnapshotEntity>)

    @Query("DELETE FROM budget_month_snapshots WHERE year = :year AND month = :month")
    suspend fun deleteGroupSnapshotsForMonth(year: Int, month: Int)

    @Query("DELETE FROM budget_category_month_snapshots WHERE year = :year AND month = :month")
    suspend fun deleteCategorySnapshotsForMonth(year: Int, month: Int)

    @Transaction
    suspend fun replaceMonthSnapshots(
        year: Int,
        month: Int,
        groupSnapshots: List<BudgetMonthSnapshotEntity>,
        categorySnapshots: List<BudgetCategoryMonthSnapshotEntity>
    ) {
        deleteGroupSnapshotsForMonth(year, month)
        deleteCategorySnapshotsForMonth(year, month)
        insertGroupSnapshots(groupSnapshots)
        insertCategorySnapshots(categorySnapshots)
    }

    @Query("SELECT * FROM budget_month_snapshots WHERE year = :year AND month = :month ORDER BY display_order ASC")
    suspend fun getGroupSnapshots(year: Int, month: Int): List<BudgetMonthSnapshotEntity>

    @Query("SELECT * FROM budget_category_month_snapshots WHERE year = :year AND month = :month")
    suspend fun getCategorySnapshots(year: Int, month: Int): List<BudgetCategoryMonthSnapshotEntity>
}
