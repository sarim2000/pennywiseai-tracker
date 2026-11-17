package com.pennywiseai.tracker.data.preferences

import android.content.Context
import com.pennywiseai.tracker.data.backup.backupJson
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import kotlinx.serialization.builtins.ListSerializer

/**
 * What the user set on each account (statement and due day, card bill, balance
 * anchor), kept on disk while a force resync rebuilds the rows that carry it. Those
 * rows are deleted before the inbox is read again, so a scan that stops in between
 * would otherwise lose it for good; the next scan to finish puts it back.
 *
 * Device-only scratch state: not part of any backup.
 */
class RebuildAccountFieldsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Fields still waiting for a scan to finish and put them back. */
    fun pending(): List<AccountBalanceEntity> = decode(prefs.getString(KEY, null))

    /** Written synchronously, so it's on disk before any row is deleted. Empty clears it. */
    fun save(rows: List<AccountBalanceEntity>) {
        val json = encode(rows)
        val editor = prefs.edit()
        if (json == null) editor.remove(KEY) else editor.putString(KEY, json)
        check(editor.commit()) { "Couldn't save account fields before a rebuild" }
    }

    companion object {
        private const val PREFS_NAME = "rebuild_account_fields"
        private const val KEY = "rows"
        private val serializer = ListSerializer(AccountBalanceEntity.serializer())

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
