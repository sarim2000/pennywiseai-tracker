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

    // Transaction ID to edit when launched from notification
    var editTransactionId by mutableStateOf<Long?>(null)
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
                onEditComplete = { editTransactionId = null }
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
    }
}