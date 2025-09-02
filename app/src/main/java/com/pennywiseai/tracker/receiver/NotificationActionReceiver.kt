package com.pennywiseai.tracker.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.pennywiseai.tracker.data.repository.TransactionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver for handling notification actions like inline reply and category selection
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var transactionRepository: TransactionRepository
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val TAG = "NotificationActionReceiver"
        
        // Action types
        const val ACTION_UPDATE_MERCHANT = "com.pennywiseai.tracker.UPDATE_MERCHANT"
        const val ACTION_UPDATE_CATEGORY = "com.pennywiseai.tracker.UPDATE_CATEGORY"
        const val ACTION_CONFIRM_TRANSACTION = "com.pennywiseai.tracker.CONFIRM_TRANSACTION"
        const val ACTION_DELETE_TRANSACTION = "com.pennywiseai.tracker.DELETE_TRANSACTION"
        
        // Extra keys
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_CATEGORY = "category"
        const val KEY_MERCHANT_REPLY = "key_merchant_reply"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        
        if (transactionId == -1L) {
            Log.e(TAG, "Invalid transaction ID")
            return
        }
        
        when (intent.action) {
            ACTION_UPDATE_MERCHANT -> {
                handleMerchantUpdate(context, intent, transactionId, notificationId)
            }
            ACTION_UPDATE_CATEGORY -> {
                val category = intent.getStringExtra(EXTRA_CATEGORY) ?: return
                handleCategoryUpdate(context, transactionId, category, notificationId)
            }
            ACTION_CONFIRM_TRANSACTION -> {
                handleConfirmTransaction(context, notificationId)
            }
            ACTION_DELETE_TRANSACTION -> {
                handleDeleteTransaction(context, transactionId, notificationId)
            }
        }
    }
    
    private fun handleMerchantUpdate(
        context: Context, 
        intent: Intent, 
        transactionId: Long,
        notificationId: Int
    ) {
        // Get the inline reply text
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val newMerchantName = remoteInput?.getCharSequence(KEY_MERCHANT_REPLY)?.toString()
        
        if (newMerchantName.isNullOrBlank()) {
            Log.e(TAG, "Empty merchant name received")
            return
        }
        
        scope.launch {
            try {
                // Get the transaction
                val transaction = transactionRepository.getTransactionById(transactionId)
                if (transaction != null) {
                    // Update merchant name
                    val updatedTransaction = transaction.copy(
                        merchantName = newMerchantName,
                        updatedAt = java.time.LocalDateTime.now()
                    )
                    transactionRepository.updateTransaction(updatedTransaction)
                    
                    // Show success feedback
                    showToast(context, "Updated to: $newMerchantName")
                    Log.i(TAG, "Updated merchant name for transaction $transactionId to: $newMerchantName")
                    
                    // Dismiss notification
                    dismissNotification(context, notificationId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating merchant name", e)
                showToast(context, "Failed to update merchant name")
            }
        }
    }
    
    private fun handleCategoryUpdate(
        context: Context,
        transactionId: Long,
        category: String,
        notificationId: Int
    ) {
        scope.launch {
            try {
                // Get the transaction
                val transaction = transactionRepository.getTransactionById(transactionId)
                if (transaction != null) {
                    // Update category
                    val updatedTransaction = transaction.copy(
                        category = category,
                        updatedAt = java.time.LocalDateTime.now()
                    )
                    transactionRepository.updateTransaction(updatedTransaction)
                    
                    // Update category for all transactions with same merchant
                    if (!transaction.merchantName.isNullOrBlank()) {
                        transactionRepository.updateCategoryForMerchant(
                            transaction.merchantName,
                            category
                        )
                        showToast(context, "Category updated for all ${transaction.merchantName} transactions")
                    } else {
                        showToast(context, "Category set to: $category")
                    }
                    
                    Log.i(TAG, "Updated category for transaction $transactionId to: $category")
                    
                    // Dismiss notification
                    dismissNotification(context, notificationId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating category", e)
                showToast(context, "Failed to update category")
            }
        }
    }
    
    private fun handleConfirmTransaction(context: Context, notificationId: Int) {
        // Just dismiss the notification - transaction is already saved
        dismissNotification(context, notificationId)
        showToast(context, "Transaction confirmed")
        Log.i(TAG, "Transaction confirmed, notification dismissed")
    }
    
    private fun handleDeleteTransaction(
        context: Context,
        transactionId: Long,
        notificationId: Int
    ) {
        scope.launch {
            try {
                // Delete the transaction
                transactionRepository.deleteTransactionById(transactionId)
                
                showToast(context, "Transaction deleted")
                Log.i(TAG, "Deleted transaction: $transactionId")
                
                // Dismiss notification
                dismissNotification(context, notificationId)
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting transaction", e)
                showToast(context, "Failed to delete transaction")
            }
        }
    }
    
    private fun dismissNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }
    
    private fun showToast(context: Context, message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}