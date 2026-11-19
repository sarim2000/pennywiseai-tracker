package com.pennywiseai.tracker.presentation.add

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.core.QuickAddIntent
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.model.VoiceEntry
import com.pennywiseai.tracker.data.repository.AppLockRepository
import com.pennywiseai.tracker.ui.security.BiometricAuthManager
import com.pennywiseai.tracker.ui.viewmodel.PennyWiseUserTheme
import com.pennywiseai.tracker.utils.withLatinDigits
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * The quick add ([QuickAddSheet]) for the widgets' + and the evening recap's "+ Cash": a
 * translucent sheet over whatever the user was doing. "More details" and Transfer hand over
 * to the full form in the app.
 *
 * Opened by a mic (the cash widget: [QuickAddIntent.voice]) it listens first; a question
 * opens Chat with it in the box, not sent.
 *
 * Behind the app lock, like opening the app.
 */
@AndroidEntryPoint
class QuickAddActivity : FragmentActivity() {

    @Inject lateinit var appLockRepository: AppLockRepository
    @Inject lateinit var biometricAuthManager: BiometricAuthManager

    private val unlocked = mutableStateOf(false)

    /** Opened by a mic: listen first. State, since a later tap can bring this same sheet back (onNewIntent). */
    private var voiceMode by mutableStateOf(false)

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(newBase.withLatinDigits())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.getBoolean(KEY_UNLOCKED) == true) unlocked.value = true else checkLock()
        voiceMode = savedInstanceState?.getBoolean(KEY_VOICE) ?: intent.getBooleanExtra(QuickAddIntent.EXTRA_VOICE, false)
        setContent {
            PennyWiseUserTheme {
                // A new mode starts the sheet over: listening, or plain typing.
                if (unlocked.value) {
                    key(voiceMode) {
                        QuickAddSheet(
                            listenFirst = voiceMode,
                            onDismiss = ::finish,
                            onSaved = ::finish,
                            onMoreDetails = ::openFullForm,
                            onAsk = ::openChat
                        )
                    }
                }
            }
        }
    }

    /**
     * singleTask: a sheet left behind the lock prompt (only an unlocked one closes on
     * onStop) comes back for the next tap, which may be the other button: the mic's
     * sheet listens, the +'s doesn't.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        voiceMode = intent.getBooleanExtra(QuickAddIntent.EXTRA_VOICE, false)
    }

    // Kept across a rotation only: an unlocked sheet doesn't outlive leaving it (onStop).
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, unlocked.value)
        outState.putBoolean(KEY_VOICE, voiceMode)
    }

    /**
     * Left after unlocking (Home, another app, the screen off): close. The
     * sheet is singleTask, so one kept in the background came back on the
     * next + without onCreate, past the lock's timeout; a fresh one checks.
     * Not while locked: the PIN fallback covers the sheet mid-check.
     */
    override fun onStop() {
        super.onStop()
        if (unlocked.value && !isChangingConfigurations) finish()
    }

    private fun checkLock() {
        lifecycleScope.launch {
            if (!appLockRepository.shouldLockApp()) {
                unlocked.value = true
                return@launch
            }
            biometricAuthManager.authenticate(
                activity = this@QuickAddActivity,
                onSuccess = {
                    lifecycleScope.launch { appLockRepository.updateAuthTimestamp() }
                    unlocked.value = true
                },
                onError = { finish() },
                onCancel = { finish() }
            )
        }
    }

    /** The full form, with what was typed (or said) here so nothing is entered twice. */
    private fun openFullForm(draft: QuickAddDraft) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_ADD_TRANSACTION, true)
                .putExtra(MainActivity.EXTRA_ADD_AMOUNT, draft.amount.ifEmpty { null })
                .putExtra(MainActivity.EXTRA_ADD_MERCHANT, draft.merchant.trim().ifEmpty { null })
                .putExtra(MainActivity.EXTRA_ADD_CATEGORY, draft.category)
                .putExtra(MainActivity.EXTRA_ADD_BANK, draft.account?.bankName)
                .putExtra(MainActivity.EXTRA_ADD_LAST4, draft.account?.accountLast4)
                .putExtra(MainActivity.EXTRA_ADD_TYPE, draft.type.name)
                .putExtra(MainActivity.EXTRA_ADD_CURRENCY, draft.currency)
                .putExtra(MainActivity.EXTRA_ADD_DATE_TIME, draft.dateTime?.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    /** A question: Chat with it in the box, not sent. Single top keeps the app as it was under the sheet. */
    private fun openChat(text: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_CHAT_TEXT, text)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    /** Where what was said goes: the sheet, Chat, or back to the mic. */
    internal sealed interface VoiceRoute {
        data class Fill(val entry: VoiceEntry) : VoiceRoute
        data object Chat : VoiceRoute
        data object Unclear : VoiceRoute

        /** A question or a plan, with the Chat tab hidden. */
        data object ChatHidden : VoiceRoute
    }

    companion object {
        private const val KEY_UNLOCKED = "unlocked"
        private const val KEY_VOICE = "voice"
        private const val CASH = "CASH"

        /**
         * One entry fills the sheet. A question or a plan goes to Chat (with no model it waits
         * there), or, with Chat hidden, says so. Two amounts go to Chat only with the model there
         * to read them: a cash spend said as "2 coffees 10" must not end up where it can't be
         * recorded. Anything else (no amount heard) is unclear.
         */
        internal fun voiceRoute(
            text: String,
            categories: List<CategoryEntity>,
            baseCurrency: String,
            chatOpen: Boolean,
            modelReady: Boolean,
            today: LocalDate = LocalDate.now(),
            label: (String) -> String = { it }
        ): VoiceRoute = VoiceEntry.parse(text, categories, baseCurrency, today, label)?.let { VoiceRoute.Fill(it) }
            ?: when {
                VoiceEntry.asksOrPlans(text) -> if (chatOpen) VoiceRoute.Chat else VoiceRoute.ChatHidden
                !chatOpen -> VoiceRoute.Unclear
                modelReady && VoiceEntry.needsModel(text) -> VoiceRoute.Chat
                else -> VoiceRoute.Unclear
            }

        /**
         * The cash accounts the sheet can charge, last used first: the
         * selected profile's (null: all profiles) in the entry currency.
         * Last used is the latest balance move; every save moves its
         * account's, so a changed pick is the default next time.
         */
        internal fun cashAccounts(
            accounts: List<AccountBalanceEntity>,
            currency: String,
            profileId: Long?
        ): List<AccountBalanceEntity> = accounts
            .filter { it.accountType == CASH && it.currency == currency && (profileId == null || it.profileId == profileId) }
            .sortedByDescending { it.timestamp }

        internal fun keyOf(account: AccountBalanceEntity) = "${account.bankName}_${account.accountLast4}"

        /** Most-used first, the rest in their usual order, [others] last: it's the non-answer. */
        internal fun rankCategories(
            categories: List<CategoryEntity>,
            usedMostFirst: List<String>,
            others: String
        ): List<CategoryEntity> {
            val rank = usedMostFirst.withIndex().associate { (i, name) -> name to i }
            return categories.sortedWith(compareBy({ it.name == others }, { rank[it.name] ?: Int.MAX_VALUE }))
        }
    }
}
