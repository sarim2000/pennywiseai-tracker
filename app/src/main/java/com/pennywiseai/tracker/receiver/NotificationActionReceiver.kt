package com.pennywiseai.tracker.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that handles actions from transaction notifications.
 * Supports updating merchant, category, confirming, and deleting transactions.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationActionReceiver"

        const val ACTION_DELETE_TRANSACTION = "com.pennywiseai.tracker.ACTION_DELETE_TRANSACTION"
        const val ACTION_CONFIRM_TRANSACTION = "com.pennywiseai.tracker.ACTION_CONFIRM_TRANSACTION"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        if (transactionId == -1L) {
            Log.e(TAG, "No transaction ID provided")
            return
        }

        when (intent.action) {
            ACTION_DELETE_TRANSACTION -> {
                deleteTransaction(context, transactionId, notificationId)
            }
            ACTION_CONFIRM_TRANSACTION -> {
                confirmTransaction(context, notificationId)
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent.action}")
            }
        }
    }

    private fun deleteTransaction(context: Context, transactionId: Long, notificationId: Int) {
        receiverScope.launch {
            try {
                val database = PennyWiseDatabase.getInstance(context)
                val transactionDao = database.transactionDao()

                // Soft delete the transaction
                transactionDao.softDeleteTransaction(transactionId)
                Log.d(TAG, "Deleted transaction: $transactionId")

                // Dismiss the notification
                dismissNotification(context, notificationId)
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting transaction", e)
            }
        }
    }

    private fun confirmTransaction(context: Context, notificationId: Int) {
        // Simply dismiss the notification - transaction is already saved
        dismissNotification(context, notificationId)
        Log.d(TAG, "Transaction confirmed, notification dismissed")
    }

    private fun dismissNotification(context: Context, notificationId: Int) {
        if (notificationId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }
    }
}
