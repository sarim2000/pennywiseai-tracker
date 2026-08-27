package com.pennywiseai.tracker.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.RecurringTransactionDao
import com.pennywiseai.tracker.data.database.entity.RecurringTransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CRUD + scheduled materialisation for recurring / scheduled manual (cash)
 * transaction templates (#706).
 *
 * A template has no SMS behind it — it exists so a user can schedule cash /
 * manual spend (rent, allowance, a weekly cleaner) to be created automatically.
 * [materializeDue] is the scheduler entry point: the daily
 * [com.pennywiseai.tracker.worker.RecurringTransactionWorker] calls it, and it
 * inserts a real [TransactionEntity] for every due active template — reusing
 * [AccountBalanceRepository] so a linked funding account's balance moves the
 * same way a hand-entered transaction's would — then advances each template's
 * `nextDueDate` past today.
 */
@Singleton
class RecurringTransactionRepository @Inject constructor(
    private val dao: RecurringTransactionDao,
    private val database: PennyWiseDatabase,
    private val accountBalanceRepository: AccountBalanceRepository
) {

    companion object {
        private const val TAG = "RecurringTxnRepository"
    }

    fun getAll(): Flow<List<RecurringTransactionEntity>> = dao.getAll()

    fun getActive(): Flow<List<RecurringTransactionEntity>> = dao.getActive()

    suspend fun getById(id: Long): RecurringTransactionEntity? = dao.getById(id)

    suspend fun insert(recurring: RecurringTransactionEntity): Long = dao.insert(recurring)

    suspend fun update(recurring: RecurringTransactionEntity) =
        dao.update(recurring.copy(updatedAt = LocalDateTime.now()))

    suspend fun delete(recurring: RecurringTransactionEntity) = dao.delete(recurring)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    /**
     * Materialise every due active template into a real transaction and advance
     * its schedule. Returns the number of transactions actually created.
     *
     * Double-insertion is guarded two ways, both belt-and-suspenders:
     *  1. Each template's insert + date-advance run inside ONE Room transaction,
     *     so a template whose `nextDueDate` we advance past today can't be
     *     returned by `getDue` again on a later run the same day.
     *  2. The created transaction gets a deterministic hash derived from the
     *     template id + the due date, so even if the worker runs twice before
     *     the advance commits, the second insert collides on the unique
     *     `transaction_hash` index and no-ops (insert returns -1).
     *
     * A template that missed several cycles (device off) materialises ONCE and
     * its schedule is fast-forwarded past today, rather than back-filling a
     * burst of historical transactions.
     */
    suspend fun materializeDue(today: LocalDate = LocalDate.now()): Int {
        val due = dao.getDue(today)
        var created = 0
        for (template in due) {
            try {
                database.withTransaction {
                    val transaction = template.toTransaction()
                    val rowId = accountBalanceRepository.insertTransactionWithBalance(
                        transaction = transaction,
                        bankName = template.bankName,
                        accountLast4 = template.accountLast4
                    )
                    // Advance the schedule regardless of whether the insert was a
                    // fresh row or a dedup no-op (-1): the intent is "this due
                    // date has been handled, move on".
                    dao.updateNextDueDate(template.id, nextDueDateAfter(template, today))
                    if (rowId != -1L) created++
                }
            } catch (e: Exception) {
                // One bad template must not abort the rest of the batch.
                Log.w(TAG, "Failed to materialize recurring template ${template.id}: ${e.message}", e)
            }
        }
        return created
    }

    /**
     * The first occurrence strictly after [today], stepping by the template's
     * cadence from its current [RecurringTransactionEntity.nextDueDate]. Loops
     * so a template that missed several cycles lands on the next real future
     * date instead of re-firing immediately.
     */
    private fun nextDueDateAfter(
        template: RecurringTransactionEntity,
        today: LocalDate
    ): LocalDate {
        var next = template.nextDueAfter(template.nextDueDate)
        while (!next.isAfter(today)) {
            next = template.nextDueAfter(next)
        }
        return next
    }

    /**
     * Build the manual transaction for this template, mirroring how
     * `AddTransactionUseCase` constructs a hand-entered one: null SMS fields
     * mark it manual, and a null funding account falls back to "Manual Entry".
     * The date is pinned to the scheduled due date (start of day) so the
     * deterministic hash stays stable across worker re-runs.
     */
    private fun RecurringTransactionEntity.toTransaction(): TransactionEntity {
        val now = LocalDateTime.now()
        return TransactionEntity(
            amount = amount,
            merchantName = merchantName,
            category = category,
            transactionType = transactionType,
            dateTime = nextDueDate.atStartOfDay(),
            description = note,
            smsBody = null,
            bankName = bankName ?: "Manual Entry",
            smsSender = null,
            accountNumber = accountLast4,
            balanceAfter = null,
            transactionHash = recurringTransactionHash(id, amount, merchantName, nextDueDate),
            currency = currency,
            createdAt = now,
            updatedAt = now,
            profileId = profileId
        )
    }

    private fun recurringTransactionHash(
        templateId: Long,
        amount: BigDecimal,
        merchant: String,
        dueDate: LocalDate
    ): String {
        // Template id + due date make this deterministic per (template, cycle),
        // so a repeated run before the date advances collides on the unique hash.
        val data = "RECURRING_${templateId}_${amount}_${merchant}_${dueDate}"
        return MessageDigest.getInstance("MD5")
            .digest(data.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
