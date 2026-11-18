package com.pennywiseai.tracker.data.preferences

import android.content.Context
import com.pennywiseai.tracker.data.backup.backupJson
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * What the user set on each account (statement and due day, card bill), kept on
 * disk while a force resync rebuilds the rows that carry it. Those
 * rows are deleted before the inbox is read again, so a scan that stops in between
 * would otherwise lose it for good; the next scan to finish puts it back.
 *
 * Once the user edits one of those fields, what they typed wins: the saved copy
 * takes the edit, and drops it once the edit is on the account's rows, or the next
 * scan would put a cleared bill back. Taking the snapshot with the deletes after it,
 * putting it back, and an edit each run whole under one lock, so an edit can't land
 * between the snapshot and the deletes and go with the rows, and a scan can't read
 * the copy, let the user clear the field, and then write its stale copy back.
 *
 * Device-only scratch state: not part of any backup.
 */
class RebuildAccountFieldsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Fields still waiting for a scan to finish and put them back. */
    fun pending(): List<AccountBalanceEntity> = decode(prefs.getString(KEY, null))

    /**
     * Before a rebuild: saves the accounts [read] returns ahead of any older interrupted
     * rebuild's, so they win when put back, then runs [deleteRows]. Returns what [read] gave.
     */
    suspend fun saveBeforeRebuild(
        read: suspend () -> List<AccountBalanceEntity>,
        deleteRows: suspend () -> Unit,
    ): List<AccountBalanceEntity> =
        lock.withLock { read().also { save(it + pending()); deleteRows() } }

    /**
     * Once a scan has finished: [put] puts each account's saved fields back on its rows
     * and says how many it touched. One with no rows yet stays saved for a later scan.
     */
    suspend fun restore(put: suspend (AccountBalanceEntity) -> Int) {
        lock.withLock {
            val rows = pending()
            if (rows.isNotEmpty()) save(rows.filter { put(it) == 0 })
        }
    }

    /**
     * The user sets a card's due day and bill (nulls clear them) with [write], which
     * says how many rows it changed.
     */
    suspend fun editCardBill(
        bankName: String,
        accountLast4: String,
        dueDay: Int?,
        amount: BigDecimal?,
        asOf: LocalDateTime?,
        write: suspend () -> Int,
    ) = edit(
        bankName, accountLast4, write,
        edited = { it.copy(cardDueDay = dueDay, cardBillAmount = amount, cardBillAsOf = asOf) },
        cleared = { it.copy(cardDueDay = null, cardBillAmount = null, cardBillAsOf = null) },
    )

    /** The user sets an account's statement day (null clears it) with [write], which says how many rows it changed. */
    suspend fun editStatementDay(bankName: String, accountLast4: String, day: Int?, write: suspend () -> Int) =
        edit(bankName, accountLast4, write, edited = { it.copy(statementDay = day) }, cleared = { it.copy(statementDay = null) })

    /**
     * [edited] goes into the saved copy first, so a scan still to come puts back the
     * edit rather than what was there before. Once [write] has put it on some row, the
     * copy drops those fields ([cleared]); while the account has no rows (a rebuild
     * deleted them), the edit stays there for the scan that recreates them.
     */
    private suspend fun edit(
        bankName: String,
        accountLast4: String,
        write: suspend () -> Int,
        edited: (AccountBalanceEntity) -> AccountBalanceEntity,
        cleared: (AccountBalanceEntity) -> AccountBalanceEntity,
    ) {
        lock.withLock {
            fun savedWith(change: (AccountBalanceEntity) -> AccountBalanceEntity) =
                save(pending().map { if (it.isAccount(bankName, accountLast4)) change(it) else it })
            val saved = pending().any { it.isAccount(bankName, accountLast4) }
            if (saved) savedWith(edited)
            if (write() > 0 && saved) savedWith(cleared)
        }
    }

    /** Written synchronously, so it's on disk before any row is deleted. Empty clears it. */
    private fun save(rows: List<AccountBalanceEntity>) {
        val json = encode(rows)
        val editor = prefs.edit()
        if (json == null) editor.remove(KEY) else editor.putString(KEY, json)
        check(editor.commit()) { "Couldn't save account fields before a rebuild" }
    }

    companion object {
        private const val PREFS_NAME = "rebuild_account_fields"
        private const val KEY = "rows"
        private val serializer = ListSerializer(AccountBalanceEntity.serializer())

        /** One per process: the worker and the screens each hold their own store. */
        private val lock = Mutex()

        private fun AccountBalanceEntity.isAccount(bankName: String, accountLast4: String) =
            this.bankName == bankName && this.accountLast4 == accountLast4

        /** Only rows that carry something the user set; null when there are none. */
        internal fun encode(rows: List<AccountBalanceEntity>): String? = rows
            .filter { it.hasAccountFields() }
            // The SMS text isn't needed to put the fields back, so it isn't copied here.
            .map { it.copy(smsSource = null) }
            .takeIf { it.isNotEmpty() }
            ?.let { backupJson.encodeToString(serializer, it) }

        /** What [encode] wrote; nothing when it's missing or unreadable. */
        internal fun decode(json: String?): List<AccountBalanceEntity> =
            json?.let { runCatching { backupJson.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

        private fun AccountBalanceEntity.hasAccountFields(): Boolean =
            listOfNotNull(statementDay, cardDueDay, cardBillAmount, cardBillAsOf).isNotEmpty()
    }
}
