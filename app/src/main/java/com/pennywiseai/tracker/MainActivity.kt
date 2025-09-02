package com.pennywiseai.tracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.pennywiseai.tracker.receiver.SmsBroadcastReceiver
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val editTransactionId = mutableStateOf<Long?>(null)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Check if we're opening from notification to edit a transaction
        editTransactionId.value = handleEditIntent(intent)
        
        setContent {
            PennyWiseApp(
                editTransactionId = editTransactionId.value,
                onEditComplete = {
                    editTransactionId.value = null
                }
            )
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Update the transaction ID to navigate to edit screen
        editTransactionId.value = handleEditIntent(intent)
    }
    
    private fun handleEditIntent(intent: Intent): Long? {
        if (intent.action == SmsBroadcastReceiver.ACTION_EDIT_TRANSACTION) {
            val transactionId = intent.getLongExtra(SmsBroadcastReceiver.EXTRA_TRANSACTION_ID, -1)
            return if (transactionId != -1L) transactionId else null
        }
        return null
    }
}