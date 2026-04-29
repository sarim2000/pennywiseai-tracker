package com.pennywiseai.tracker.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryMonthSnapshotEntity
import com.pennywiseai.tracker.data.database.entity.BudgetMonthSnapshotEntity

@Dao
interface BudgetSnapshotDao {

    @Insert
    suspend fun insertGroupSnapshot(snapshot: BudgetMonthSnapshotEntity): Long

    @Insert
    suspend fun insertCategorySnapshot(snapshot: BudgetCategoryMonthSnapshotEntity): Long

    @Query("DELETE FROM budget_month_snapshots WHERE year = :year AND month = :month")
    suspend fun deleteGroupSnapshotsForMonth(year: Int, month: Int)

    @Query("DELETE FROM budget_category_month_snapshots WHERE year = :year AND month = :month")
    suspend fun deleteCategorySnapshotsForMonth(year: Int, month: Int)

    @Query("SELECT * FROM budget_month_snapshots WHERE year = :year AND month = :month ORDER BY display_order ASC")
    suspend fun getGroupSnapshots(year: Int, month: Int): List<BudgetMonthSnapshotEntity>

    @Query("SELECT * FROM budget_category_month_snapshots WHERE year = :year AND month = :month")
    suspend fun getCategorySnapshots(year: Int, month: Int): List<BudgetCategoryMonthSnapshotEntity>
}
