package com.pennywiseai.tracker

import com.pennywiseai.tracker.utils.withLatinDigits
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.pennywiseai.tracker.data.repository.AppLockRepository
import com.pennywiseai.tracker.receiver.SmsBroadcastReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_OPEN_ADD_TRANSACTION = "com.pennywiseai.tracker.OPEN_ADD_TRANSACTION"
        const val EXTRA_OPEN_UNRECOGNIZED = "com.pennywiseai.tracker.OPEN_UNRECOGNIZED"

        /** The quick add sheet's draft, carried into the full form by "More details". */
        const val EXTRA_ADD_AMOUNT = "com.pennywiseai.tracker.ADD_AMOUNT"
        const val EXTRA_ADD_MERCHANT = "com.pennywiseai.tracker.ADD_MERCHANT"
        const val EXTRA_ADD_CATEGORY = "com.pennywiseai.tracker.ADD_CATEGORY"
        const val EXTRA_ADD_BANK = "com.pennywiseai.tracker.ADD_BANK"
        const val EXTRA_ADD_LAST4 = "com.pennywiseai.tracker.ADD_LAST4"

        /** An SMS suggestion's notification (#994): what [AddTransaction] carries beyond the draft. */
        const val EXTRA_ADD_CURRENCY = "com.pennywiseai.tracker.ADD_CURRENCY"
        const val EXTRA_ADD_TYPE = "com.pennywiseai.tracker.ADD_TYPE"
        const val EXTRA_ADD_DATE_TIME = "com.pennywiseai.tracker.ADD_DATE_TIME"
        const val EXTRA_ADD_SMS_ID = "com.pennywiseai.tracker.ADD_SMS_ID"

        /** Goal widgets (#914): open a goal, the Budgets screen, or "New goal" there. */
        const val EXTRA_OPEN_GOAL_ID = "com.pennywiseai.tracker.OPEN_GOAL_ID"
        const val EXTRA_OPEN_REVIEW = "com.pennywiseai.tracker.OPEN_REVIEW"
        const val EXTRA_REVIEW_WEEK_START = "com.pennywiseai.tracker.REVIEW_WEEK_START"
        private const val KEY_LAUNCH_PENDING = "launch_request_pending"
        const val EXTRA_OPEN_BUDGETS = "com.pennywiseai.tracker.OPEN_BUDGETS"
        const val EXTRA_CREATE_GOAL = "com.pennywiseai.tracker.CREATE_GOAL"

        /**
         * Deep link that jumps straight to Add Transaction: `pennywise://add`.
         * The single entry point every quick-add surface routes through — the
         * QS tile, the launcher shortcut, and anything the user wires up
         * themselves (Tasker, an OEM gesture, a Pixel Quick Tap macro).
         */
        const val DEEP_LINK_SCHEME = "pennywise"
        const val DEEP_LINK_HOST_ADD = "add"

        fun addDeepLink(): Uri = "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST_ADD".toUri()

        private const val MAX_SHARED_TEXT = 1000
    }

    // Transaction ID to edit when launched from notification
    var editTransactionId by mutableStateOf<Long?>(null)
        private set

    // Add Transaction to open when launched from a shortcut/widget, with any draft to fill in
    var openAddTransaction by mutableStateOf<com.pennywiseai.tracker.navigation.AddTransaction?>(null)
        private set

    // Screen a goal widget asked for (a nav destination), consumed by PennyWiseApp.
    var widgetDestination by mutableStateOf<androidx.navigation3.runtime.NavKey?>(null)
        private set

    // Text shared to PennyWise from another app; opens Add Transaction pre-filled.
    var sharedText by mutableStateOf<String?>(null)
        private set

    @Inject lateinit var appLockRepository: AppLockRepository

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(newBase.withLatinDigits())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate(). Held until the hide-amounts
        // switch is read, so a hidden balance never flashes on a cold start.
        installSplashScreen().setKeepOnScreenCondition { !com.pennywiseai.tracker.utils.AmountPrivacy.loaded }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // With App Lock on, the Recents card is blank instead of the last screen
        // (balances, transactions), which anyone could read there without unlocking.
        // Hidden until the setting is read, in case the app is left right away.
        hideFromRecents(true)
        lifecycleScope.launch {
            appLockRepository.isAppLockEnabled.distinctUntilChanged().collect(::hideFromRecents)
        }

        // Launch intents (notification, share, Add shortcut/tile, widget) — not on
        // recreation: the restored back stack already holds what they opened, and
        // replaying them would push a fresh screen over it (an Add draft lost on rotate).
        // A request still waiting (e.g. behind the lock screen) is read again, since it
        // lived only in this activity's fields.
        if (savedInstanceState == null || savedInstanceState.getBoolean(KEY_LAUNCH_PENDING)) {
            handleEditIntent(intent)
            handleWidgetIntent(intent)
        }

        val editCompleteCallback = { editTransactionId = null }
        val addShortcutCallback = { openAddTransaction = null }
        val sharedTextCallback = { sharedText = null }

        registerQuickAddShortcut()

        setContent {
            PennyWiseApp(
                editTransactionId = editTransactionId,
                openAddTransaction = openAddTransaction,
                onEditComplete = editCompleteCallback,
                onAddTransactionShortcutHandled = addShortcutCallback,
                sharedText = sharedText,
                onSharedTextHandled = sharedTextCallback,
                widgetDestination = widgetDestination,
                onWidgetDestinationHandled = { widgetDestination = null }
            )
        }
    }

    /**
     * Before Android 13 there's no setRecentsScreenshotEnabled, and marking the
     * window secure only as the app pauses is too late: a gesture-navigation
     * swipe to Recents captures the card while the app is still resumed. So
     * there the window stays secure while App Lock is on, which also blocks
     * screenshots in the app.
     */
    private fun hideFromRecents(hide: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!hide)
        } else if (hide) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(
            KEY_LAUNCH_PENDING,
            editTransactionId != null || openAddTransaction != null || sharedText != null || widgetDestination != null
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The intent a recreation reads again if its request is still pending.
        setIntent(intent)
        // Handle intent when activity is already running
        handleEditIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        intent ?: return
        val goalId = intent.getLongExtra(EXTRA_OPEN_GOAL_ID, -1L)
        widgetDestination = when {
            goalId > 0 -> com.pennywiseai.tracker.navigation.GoalDetail(goalId)
            intent.getBooleanExtra(EXTRA_OPEN_UNRECOGNIZED, false) ->
                com.pennywiseai.tracker.navigation.UnrecognizedSms
            intent.hasExtra(EXTRA_OPEN_REVIEW) ->
                com.pennywiseai.tracker.navigation.Review(
                    kind = intent.getStringExtra(EXTRA_OPEN_REVIEW) ?: "WEEK",
                    weekStart = intent.getStringExtra(EXTRA_REVIEW_WEEK_START),
                )
            intent.getBooleanExtra(EXTRA_OPEN_BUDGETS, false) ->
                com.pennywiseai.tracker.navigation.BudgetGroups(
                    createGoal = intent.getBooleanExtra(EXTRA_CREATE_GOAL, false),
                    tapId = android.os.SystemClock.elapsedRealtime()
                )
            else -> return
        }
    }

    private fun handleEditIntent(intent: Intent?) {
        if (intent?.action == SmsBroadcastReceiver.ACTION_EDIT_TRANSACTION) {
            val transactionId = intent.getLongExtra(SmsBroadcastReceiver.EXTRA_TRANSACTION_ID, -1)
            if (transactionId != -1L) {
                editTransactionId = transactionId
                // A tapped alert auto-cancels without a callback; recount the stack's header.
                SmsBroadcastReceiver.updateAlertGroup(this)
            }
        }
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            // Capped: it travels as a navigation argument and ends up as a note.
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.take(MAX_SHARED_TEXT)?.ifEmpty { null }
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_ADD_TRANSACTION, false) == true || intent.isAddDeepLink()) {
            openAddTransaction = com.pennywiseai.tracker.navigation.AddTransaction(
                amount = intent?.getStringExtra(EXTRA_ADD_AMOUNT),
                merchant = intent?.getStringExtra(EXTRA_ADD_MERCHANT),
                category = intent?.getStringExtra(EXTRA_ADD_CATEGORY),
                bankName = intent?.getStringExtra(EXTRA_ADD_BANK),
                accountLast4 = intent?.getStringExtra(EXTRA_ADD_LAST4),
                currency = intent?.getStringExtra(EXTRA_ADD_CURRENCY),
                type = intent?.getStringExtra(EXTRA_ADD_TYPE),
                dateTime = intent?.getStringExtra(EXTRA_ADD_DATE_TIME),
                unrecognizedSmsId = intent?.getLongExtra(EXTRA_ADD_SMS_ID, -1L)?.takeIf { it > 0 }
            )
        }
    }

    /**
     * The "Add transaction" item under a long-press of the launcher icon.
     *
     * Registered here rather than declared in res/xml so the intent names this
     * activity by component. A static shortcut would have to spell the package
     * out as a literal — which the debug applicationId suffix breaks — and a
     * component-less pennywise:// intent would resolve against any app that
     * claims the scheme.
     */
    private fun registerQuickAddShortcut() {
        val shortcut = ShortcutInfoCompat.Builder(this, "add_transaction")
            .setShortLabel(getString(R.string.shortcut_add_transaction_short))
            .setLongLabel(getString(R.string.shortcut_add_transaction_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_quick_add))
            .setIntent(Intent(Intent.ACTION_VIEW, addDeepLink(), this, MainActivity::class.java))
            .build()
        ShortcutManagerCompat.setDynamicShortcuts(this, listOf(shortcut))
    }

    private fun Intent?.isAddDeepLink(): Boolean {
        val data = this?.data ?: return false
        return data.scheme == DEEP_LINK_SCHEME && data.host == DEEP_LINK_HOST_ADD
    }
}
