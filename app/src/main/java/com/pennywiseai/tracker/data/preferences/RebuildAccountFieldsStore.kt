package com.pennywiseai.tracker.data.preferences

import android.content.Context
import com.pennywiseai.tracker.data.backup.backupJson
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject

/**
 * What the user set on each account (statement and due day, card bill, balance
 * anchor), kept on disk while a force resync rebuilds the rows that carry it. Those
 * rows are deleted before the inbox is read again, so a scan that stops in between
 * would otherwise lose it for good; the next scan to finish puts it back.
 *
 * Once the user edits one of those fields, what they typed wins: [forget] drops the
 * saved copy, or the next scan would put a cleared bill back.
 *
 * Device-only scratch state: not part of any backup.
 */
class RebuildAccountFieldsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Fields still waiting for a scan to finish and put them back. */
    fun pending(): List<AccountBalanceEntity> = decode(prefs.getString(KEY, null))

    /** Written synchronously, so it's on disk before any row is deleted. Empty clears it. */
    fun save(rows: List<AccountBalanceEntity>) {
        synchronized(LOCK) {
            val json = encode(rows)
            val editor = prefs.edit()
            if (json == null) editor.remove(KEY) else editor.putString(KEY, json)
            check(editor.commit()) { "Couldn't save account fields before a rebuild" }
        }
    }

    /**
     * The user just set [bankName]/[accountLast4]'s fields that [clear] blanks: the
     * saved copy of those no longer applies. Written synchronously, before the edit.
     */
    private fun forget(bankName: String, accountLast4: String, clear: (AccountBalanceEntity) -> AccountBalanceEntity) {
        synchronized(LOCK) {
            val rows = pending()
            if (rows.any { it.isAccount(bankName, accountLast4) }) {
                save(rows.map { if (it.isAccount(bankName, accountLast4)) clear(it) else it })
            }
        }
    }

    /** Clears a card's due day and bill. */
    fun forgetCardBill(bankName: String, accountLast4: String) =
        forget(bankName, accountLast4) { it.copy(cardDueDay = null, cardBillAmount = null, cardBillAsOf = null) }

    fun forgetStatementDay(bankName: String, accountLast4: String) =
        forget(bankName, accountLast4) { it.copy(statementDay = null) }

    companion object {
        private const val PREFS_NAME = "rebuild_account_fields"
        private const val KEY = "rows"
        private val serializer = ListSerializer(AccountBalanceEntity.serializer())

        /** One per process: the worker and the screens each hold their own store. */
        private val LOCK = Any()

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
