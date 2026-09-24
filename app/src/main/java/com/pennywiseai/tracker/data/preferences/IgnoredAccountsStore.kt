package com.pennywiseai.tracker.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The set of accounts the user has chosen to ignore (#826).
 *
 * A phone number registered on someone else's account — a parent's, a joint
 * account — pulls their spending into the tracker and makes every total wrong.
 * Ignoring an account skips its messages at ingestion, so nothing is stored and
 * no notification fires, and hides whatever was imported before.
 *
 * Stored as `"${bankName}_${accountLast4}"` keys in the same `account_prefs`
 * file the Manage Accounts screen has always used, so existing hidden accounts
 * carry over untouched.
 */
@Singleton
class IgnoredAccountsStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun keys(): Set<String> = prefs.getStringSet(KEY, emptySet()) ?: emptySet()

    fun replaceAll(keys: Set<String>) {
        prefs.edit().putStringSet(KEY, keys).apply()
    }

    /**
     * Whether a transaction on this account should be skipped. An account we
     * can't identify (no bank, or no account digits parsed out of the SMS) is
     * never ignored — dropping unidentifiable transactions would lose real ones.
     */
    fun isIgnored(bankName: String?, accountLast4: String?): Boolean =
        isIgnored(keys(), bankName, accountLast4)

    companion object {
        private const val PREFS_NAME = "account_prefs"
        private const val KEY = "hidden_accounts"

        fun keyFor(bankName: String, accountLast4: String) = "${bankName}_${accountLast4}"

        /** Pure form, so the gate is testable without a Context. */
        fun isIgnored(ignored: Set<String>, bankName: String?, accountLast4: String?): Boolean {
            if (bankName.isNullOrBlank() || accountLast4.isNullOrBlank()) return false
            return keyFor(bankName, accountLast4) in ignored
        }
    }
}
