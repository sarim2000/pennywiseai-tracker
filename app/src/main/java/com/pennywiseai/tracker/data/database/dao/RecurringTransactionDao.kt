package com.pennywiseai.tracker.data.database.dao

import androidx.room.*
import com.pennywiseai.tracker.data.database.entity.RecurringTransactionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface RecurringTransactionDao {

    @Query("SELECT * FROM recurring_transactions ORDER BY next_due_date ASC")
    fun getAll(): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions WHERE is_active = 1 ORDER BY next_due_date ASC")
    fun getActive(): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions WHERE id = :id")
    suspend fun getById(id: Long): RecurringTransactionEntity?

    /**
     * Active templates whose next due date is today or earlier — the daily
     * worker materialises one transaction per row returned here, then advances
     * each row's next_due_date past today.
     */
    @Query("SELECT * FROM recurring_transactions WHERE is_active = 1 AND next_due_date <= :today ORDER BY next_due_date ASC")
    suspend fun getDue(today: LocalDate): List<RecurringTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recurring: RecurringTransactionEntity): Long

    @Update
    suspend fun update(recurring: RecurringTransactionEntity)

    @Delete
    suspend fun delete(recurring: RecurringTransactionEntity)

    @Query("DELETE FROM recurring_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE recurring_transactions SET next_due_date = :nextDueDate, updated_at = datetime('now') WHERE id = :id")
    suspend fun updateNextDueDate(id: Long, nextDueDate: LocalDate)

    @Query("DELETE FROM recurring_transactions")
    suspend fun deleteAll()
}
