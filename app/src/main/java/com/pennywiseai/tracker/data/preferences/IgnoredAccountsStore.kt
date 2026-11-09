package com.pennywiseai.tracker.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CardEntity
import com.pennywiseai.tracker.widget.WidgetRefresher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun keys(): Set<String> = prefs.getStringSet(KEY, emptySet()) ?: emptySet()

    /**
     * Emits on every change, so a list re-filters the moment an account is
     * ignored rather than at the next app start.
     */
    val keysFlow: Flow<Set<String>> = callbackFlow {
        // Register first, then read: a write landing between the two would
        // otherwise produce no event and leave an open list stale.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == KEY || changedKey == null) trySend(keys())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(keys())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun replaceAll(keys: Set<String>) {
        prefs.edit().putStringSet(KEY, keys).apply()
        // The spend widgets drop ignored accounts.
        WidgetRefresher.refreshTransactionWidgets(context)
    }

    /**
     * Whether a transaction on this account should be skipped. An account we
     * can't identify (no bank, or no account digits parsed out of the SMS) is
     * never ignored — dropping unidentifiable transactions would lose real ones.
     */
    fun isIgnored(bankName: String?, vararg accountLast4: String?): Boolean =
        isIgnored(keys(), bankName, *accountLast4)

    companion object {
        private const val PREFS_NAME = "account_prefs"
        private const val KEY = "hidden_accounts"

        fun keyFor(bankName: String, accountLast4: String) = "${bankName}_${accountLast4}"

        /**
         * [ignored] plus the key of each card linked to an ignored account. An
         * already-imported card purchase is stored under the *card's* digits, so
         * ignoring the account it draws on wouldn't hide it otherwise (#826).
         */
        fun withLinkedCards(
            ignored: Set<String>,
            cards: List<CardEntity>,
            balances: List<AccountBalanceEntity>,
        ): Set<String> {
            if (ignored.isEmpty()) return ignored
            val accountKeys = balances.map { keyFor(it.bankName, it.accountLast4) }.toSet()
            return ignored + cards.mapNotNull { card ->
                val account = card.accountLast4 ?: return@mapNotNull null
                if (keyFor(card.bankName, account) !in ignored) return@mapNotNull null
                val cardKey = keyFor(card.bankName, card.cardLast4)
                // A card's last four can collide with a real account's at
                // the same bank. The stored digits are all a list has to
                // match on, so adding the key would hide that account too —
                // leave it, and lose only the card rows we can't separate.
                if (cardKey in accountKeys && cardKey !in ignored) null else cardKey
            }
        }

        /**
         * Pure form, so the gate is testable without a Context.
         *
         * [accountLast4] may be several candidates for the same message: a card
         * purchase carries the card's digits while the money leaves the linked
         * bank account, and ignoring either should stop it.
         *
         * A message with no digits at all is only ignored when it belongs to a
         * wallet the user ignored ([AccountBalanceEntity.WALLET_ACCOUNT_MARKER] —
         * mobile-money services are one account keyed on the bank name alone).
         * Otherwise it counts, because dropping what we cannot attribute would
         * lose real transactions.
         */
        fun isIgnored(ignored: Set<String>, bankName: String?, vararg accountLast4: String?): Boolean {
            if (bankName.isNullOrBlank() || ignored.isEmpty()) return false
            val identified = accountLast4.filterNot { it.isNullOrBlank() }
            if (identified.isEmpty()) {
                return keyFor(bankName, AccountBalanceEntity.WALLET_ACCOUNT_MARKER) in ignored
            }
            return identified.any { keyFor(bankName, it!!) in ignored }
        }
    }
}
