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

        setContent {
            PennyWiseApp(
                editTransactionId = editTransactionId,
                openAddTransaction = openAddTransaction,
                onEditComplete = { editTransactionId = null },
                onAddTransactionShortcutHandled = { openAddTransaction = false }
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
        if (intent?.getBooleanExtra(EXTRA_OPEN_ADD_TRANSACTION, false) == true) {
            openAddTransaction = true
        }
    }
}
