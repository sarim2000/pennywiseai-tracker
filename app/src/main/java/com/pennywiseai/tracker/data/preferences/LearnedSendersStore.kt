package com.pennywiseai.tracker.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Senders the user has booked an SMS suggestion from, and the account they
 * booked it to (#994 step 3). Later SMS from them are suggested whatever the
 * generic extractor's confidence, pre-filled with that account, and announced
 * as they arrive. Nothing reaches totals until the user taps Add.
 */
@Singleton
class LearnedSendersStore @Inject constructor(
    @ApplicationContext context: Context
) {
    data class Account(val bankName: String, val accountLast4: String, val currency: String)

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Learned senders (normalized) to the account last booked from them, if any. */
    fun all(): Map<String, Account?> = prefs.all.mapValues { (_, v) -> decode(v as? String) }

    val allFlow: Flow<Map<String, Account?>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(all()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(all())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun isLearned(sender: String): Boolean = prefs.contains(normalize(sender))

    fun accountFor(sender: String): Account? = decode(prefs.getString(normalize(sender), null))

    /** The latest booking wins, so moving a sender to another account just takes one more Add. */
    fun learn(sender: String, account: Account?) {
        val value = account?.run { "$bankName$SEP$accountLast4$SEP$currency" } ?: ""
        prefs.edit().putString(normalize(sender), value).apply()
    }

    companion object {
        private const val PREFS_NAME = "learned_sms_senders"
        private const val SEP = '\n'

        private val DLT_PREFIX = Regex("^[A-Z0-9]{2}-")
        private val DLT_SUFFIX = Regex("-[A-Z]$")

        /** "JD-KBLBNK-S" and "VM-KBLBNK" are one sender: drop the operator prefix and the DLT suffix. */
        fun normalize(sender: String): String =
            sender.trim().uppercase().replace(DLT_PREFIX, "").replace(DLT_SUFFIX, "")

        /**
         * The learned account, unless the SMS names other digits (a second card
         * at the same bank) or another currency (USD must not land in an INR account).
         */
        fun accountToUse(learned: Account?, smsLast4: String?, smsCurrency: String): Account? =
            learned?.takeIf { (smsLast4 == null || smsLast4 == it.accountLast4) && smsCurrency == it.currency }

        private fun decode(value: String?): Account? =
            value?.split(SEP)?.takeIf { it.size == 3 }?.let { (bank, last4, currency) -> Account(bank, last4, currency) }
    }
}
