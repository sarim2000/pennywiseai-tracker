package com.pennywiseai.tracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import com.pennywiseai.tracker.receiver.SmsBroadcastReceiver
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_OPEN_ADD_TRANSACTION = "com.pennywiseai.tracker.OPEN_ADD_TRANSACTION"

        /**
         * Deep link that jumps straight to Add Transaction: `pennywise://add`.
         * The single entry point every quick-add surface routes through — the
         * QS tile, the launcher shortcut, and anything the user wires up
         * themselves (Tasker, an OEM gesture, a Pixel Quick Tap macro).
         */
        const val DEEP_LINK_SCHEME = "pennywise"
        const val DEEP_LINK_HOST_ADD = "add"

        fun addDeepLink(): Uri = "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST_ADD".toUri()
    }

    // Transaction ID to edit when launched from notification
    var editTransactionId by mutableStateOf<Long?>(null)
        private set

    // Flag to navigate directly to Add Transaction when launched from a shortcut/widget
    var openAddTransaction by mutableStateOf(false)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle intent if activity is launched from notification
        handleEditIntent(intent)

        val editCompleteCallback = { editTransactionId = null }
        val addShortcutCallback = { openAddTransaction = false }

        registerQuickAddShortcut()

        setContent {
            PennyWiseApp(
                editTransactionId = editTransactionId,
                openAddTransaction = openAddTransaction,
                onEditComplete = editCompleteCallback,
                onAddTransactionShortcutHandled = addShortcutCallback
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle intent when activity is already running
        handleEditIntent(intent)
    }

    private fun handleEditIntent(intent: Intent?) {
        if (intent?.action == SmsBroadcastReceiver.ACTION_EDIT_TRANSACTION) {
            val transactionId = intent.getLongExtra(SmsBroadcastReceiver.EXTRA_TRANSACTION_ID, -1)
            if (transactionId != -1L) {
                editTransactionId = transactionId
            }
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_ADD_TRANSACTION, false) == true || intent.isAddDeepLink()) {
            openAddTransaction = true
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
