package com.pennywiseai.tracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    private fun Intent?.isAddDeepLink(): Boolean {
        val data = this?.data ?: return false
        return data.scheme == DEEP_LINK_SCHEME && data.host == DEEP_LINK_HOST_ADD
    }
}
