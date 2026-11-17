package com.pennywiseai.tracker.data.preferences

import android.content.Context
import com.pennywiseai.tracker.data.backup.backupJson
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject

/**
 * What the user set on each account (statement and due day, card bill, balance
 * anchor), kept on disk while a force resync rebuilds the rows that carry it. Those
 * rows are deleted before the inbox is read again, so a scan that stops in between
 * would otherwise lose it for good; the next scan to finish puts it back.
 *
 * Once the user edits one of those fields, what they typed wins: the edit drops the
 * saved copy, or the next scan would put a cleared bill back. Taking the snapshot,
 * putting it back, and an edit each run whole under one lock, so a scan can't read
 * the copy, let the user clear the field, and then write its stale copy back.
 *
 * Device-only scratch state: not part of any backup.
 */
class RebuildAccountFieldsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Fields still waiting for a scan to finish and put them back. */
    fun pending(): List<AccountBalanceEntity> = decode(prefs.getString(KEY, null))

    /**
     * Before a rebuild deletes any row: saves the accounts [read] returns ahead of any
     * older interrupted rebuild's, so they win when put back. Returns what [read] gave.
     */
    suspend fun saveBeforeRebuild(read: suspend () -> List<AccountBalanceEntity>): List<AccountBalanceEntity> =
        lock.withLock { read().also { save(it + pending()) } }

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

    /** The user sets a card's due day and bill with [write]; the saved copy of those goes first. */
    suspend fun editCardBill(bankName: String, accountLast4: String, write: suspend () -> Unit) =
        edit(bankName, accountLast4, write) { it.copy(cardDueDay = null, cardBillAmount = null, cardBillAsOf = null) }

    /** The user sets an account's statement day with [write]; the saved copy goes first. */
    suspend fun editStatementDay(bankName: String, accountLast4: String, write: suspend () -> Unit) =
        edit(bankName, accountLast4, write) { it.copy(statementDay = null) }

    private suspend fun edit(
        bankName: String,
        accountLast4: String,
        write: suspend () -> Unit,
        clear: (AccountBalanceEntity) -> AccountBalanceEntity,
    ) {
        lock.withLock {
            val rows = pending()
            if (rows.any { it.isAccount(bankName, accountLast4) }) {
                save(rows.map { if (it.isAccount(bankName, accountLast4)) clear(it) else it })
            }
            write()
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
            listOfNotNull(statementDay, cardDueDay, cardBillAmount, cardBillAsOf, anchorAmount, anchorAt, anchorSource).isNotEmpty()
    }
}
