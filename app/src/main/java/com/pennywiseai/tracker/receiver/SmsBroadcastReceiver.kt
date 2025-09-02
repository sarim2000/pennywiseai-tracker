package com.pennywiseai.tracker.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.PennyWiseApplication
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.parser.bank.BankParserFactory
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver for handling incoming SMS messages in real-time.
 * Automatically adds transactions and notifies user for quick edits.
 */
@AndroidEntryPoint
class SmsBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var transactionRepository: TransactionRepository
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val TAG = "SmsBroadcastReceiver"
        private const val CHANNEL_ID = "transaction_notifications"
        private const val NOTIFICATION_ID_BASE = 10000
        const val ACTION_EDIT_TRANSACTION = "com.pennywiseai.tracker.EDIT_TRANSACTION"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }
        
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            messages?.forEach { smsMessage ->
                processSms(
                    context = context,
                    sender = smsMessage.displayOriginatingAddress ?: "",
                    body = smsMessage.displayMessageBody ?: "",
                    timestamp = smsMessage.timestampMillis
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS", e)
        }
    }
    
    private fun processSms(context: Context, sender: String, body: String, timestamp: Long) {
        // Check if sender is from a supported bank
        val parser = BankParserFactory.getParser(sender)
        if (parser == null) {
            Log.d(TAG, "No parser found for sender: $sender")
            return
        }
        
        // Query content provider to get the same timestamp that SmsReaderWorker would use
        val contentProviderTimestamp = getTimestampFromContentProvider(context, sender, body)
        val actualTimestamp = contentProviderTimestamp ?: timestamp
        
        if (contentProviderTimestamp != null) {
            Log.d(TAG, "Using content provider timestamp: $contentProviderTimestamp (original: $timestamp, diff: ${contentProviderTimestamp - timestamp}ms)")
        } else {
            Log.d(TAG, "Content provider query failed, using original timestamp: $timestamp")
        }
        
        // Parse the transaction using the content provider timestamp (same as SmsReaderWorker)
        val parsedTransaction = parser.parse(body, sender, actualTimestamp)
        if (parsedTransaction == null) {
            Log.d(TAG, "Could not parse transaction from SMS")
            return
        }
        
        // Generate hash for duplicate checking
        val transactionHash = parsedTransaction.transactionHash ?: parsedTransaction.generateTransactionId()
        
        Log.d(TAG, """
            Parsed transaction from SmsBroadcastReceiver:
            Bank: ${parser.getBankName()}
            Amount: ${parsedTransaction.amount}
            Type: ${parsedTransaction.type}
            Original Timestamp: $timestamp
            Used Timestamp: $actualTimestamp
            Hash: $transactionHash
        """.trimIndent())
        
        // Add transaction immediately
        scope.launch {
            try {
                // Convert to entity
                val entity = parsedTransaction.toEntity()
                
                Log.d(TAG, "Attempting to insert transaction with hash: ${entity.transactionHash}")
                
                // Use INSERT OR IGNORE strategy (same as SmsReaderWorker)
                val rowId = transactionRepository.insertTransaction(entity)
                
                Log.d(TAG, "Insert result - rowId: $rowId")
                
                if (rowId != -1L) {
                    Log.i(TAG, "New transaction added by SmsBroadcastReceiver: ${entity.merchantName} - ${entity.amount}")
                    
                    // Check if app is in foreground
                    if (PennyWiseApplication.isAppInForeground) {
                        Log.d(TAG, "App is in foreground, skipping notification for transaction ID: $rowId")
                    } else {
                        // Show notification with quick edit action
                        Log.d(TAG, "Showing notification for transaction ID: $rowId")
                        showTransactionNotification(
                            context = context,
                            transactionId = rowId,
                            merchant = entity.merchantName ?: "Unknown",
                            amount = entity.amount,
                            type = entity.transactionType.name,
                            category = entity.category,
                            bankName = entity.bankName,
                            accountLast4 = entity.accountNumber,
                            balance = entity.balanceAfter
                        )
                    }
                } else {
                    Log.d(TAG, "Transaction already exists (duplicate), skipping notification. Hash: $transactionHash")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving transaction", e)
            }
        }
    }
    
    private fun showTransactionNotification(
        context: Context,
        transactionId: Long,
        merchant: String,
        amount: java.math.BigDecimal,
        type: String,
        category: String? = null,
        bankName: String? = null,
        accountLast4: String? = null,
        balance: java.math.BigDecimal? = null
    ) {
        Log.d(TAG, "showTransactionNotification called - ID: $transactionId, Merchant: $merchant, Amount: $amount")
        
        // Create notification channel for Android O and above
        createNotificationChannel(context)
        
        // Format amount
        val formattedAmount = CurrencyFormatter.formatCurrency(amount)
        val notificationId = NOTIFICATION_ID_BASE + transactionId.toInt()
        
        // Create RemoteInput for merchant name editing
        val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_MERCHANT_REPLY)
            .setLabel("Enter merchant name")
            .setChoices(arrayOf(merchant)) // Provide current name as suggestion
            .build()
        
        // Create PendingIntent for merchant update with RemoteInput
        val merchantUpdateIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_UPDATE_MERCHANT
            putExtra(NotificationActionReceiver.EXTRA_TRANSACTION_ID, transactionId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val merchantUpdatePendingIntent = PendingIntent.getBroadcast(
            context,
            transactionId.toInt() + 1000,
            merchantUpdateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE // Mutable for RemoteInput
        )
        
        // Create action with RemoteInput for merchant editing
        val editMerchantAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_edit,
            "✏️ Edit Name",
            merchantUpdatePendingIntent
        )
            .addRemoteInput(remoteInput)
            .build()
        
        // Create category actions for quick selection
        val foodCategoryAction = createCategoryAction(context, transactionId, notificationId, "Food & Dining", "🍕")
        val transportCategoryAction = createCategoryAction(context, transactionId, notificationId, "Transportation", "🚗")
        val shoppingCategoryAction = createCategoryAction(context, transactionId, notificationId, "Shopping", "🛍️")
        
        // Create confirm action
        val confirmIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_CONFIRM_TRANSACTION
            putExtra(NotificationActionReceiver.EXTRA_TRANSACTION_ID, transactionId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val confirmPendingIntent = PendingIntent.getBroadcast(
            context,
            transactionId.toInt() + 4000,
            confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Determine action verb based on transaction type
        val actionVerb = when (type) {
            "INCOME" -> "Received"
            "EXPENSE" -> "Spent"
            "TRANSFER" -> "Transfer"
            else -> "Transaction"
        }
        
        // Build clean title
        val title = "$formattedAmount • $actionVerb"
        
        // Build content with merchant and category (show checkmark on selected)
        val autoCategory = category ?: "Others"
        val contentText = buildString {
            append(merchant)
            append(" • ")
            append(autoCategory)
            append(" ✓")  // Show checkmark to indicate this is selected
        }
        
        // Build subtext with bank details
        val subtextParts = mutableListOf<String>()
        if (!bankName.isNullOrBlank()) {
            if (!accountLast4.isNullOrBlank()) {
                subtextParts.add("$bankName ••••$accountLast4")
            } else {
                subtextParts.add(bankName)
            }
        }
        if (balance != null) {
            subtextParts.add("Balance: ${CurrencyFormatter.formatCurrency(balance)}")
        }
        val subtext = if (subtextParts.isNotEmpty()) subtextParts.joinToString(" • ") else ""
        
        // Create category actions based on transaction type
        // For income, show income-related categories
        // For expense, show expense-related categories
        val categoryActions = if (type == "INCOME") {
            listOf(
                createCategoryAction(
                    context, transactionId, notificationId, "Salary",
                    if (autoCategory == "Salary") "💼 Salary ✓" else "💼 Salary"
                ),
                createCategoryAction(
                    context, transactionId, notificationId, "Income",
                    if (autoCategory == "Income") "💰 Income ✓" else "💰 Income"
                ),
                createCategoryAction(
                    context, transactionId, notificationId, "Refunds",
                    if (autoCategory == "Refunds") "↩️ Refund ✓" else "↩️ Refund"
                ),
                createCategoryAction(
                    context, transactionId, notificationId, "Others",
                    if (autoCategory == "Others") "📂 Other ✓" else "📂 Other"
                )
            )
        } else {
            // Expense, Transfer, or other types
            listOf(
                createCategoryAction(
                    context, transactionId, notificationId, "Food & Dining",
                    if (autoCategory == "Food & Dining") "🍕 Food ✓" else "🍕 Food"
                ),
                createCategoryAction(
                    context, transactionId, notificationId, "Transportation",
                    if (autoCategory == "Transportation") "🚗 Transport ✓" else "🚗 Transport"
                ),
                createCategoryAction(
                    context, transactionId, notificationId, "Shopping",
                    if (autoCategory == "Shopping") "🛍️ Shop ✓" else "🛍️ Shop"
                ),
                createCategoryAction(
                    context, transactionId, notificationId, "Others",
                    if (autoCategory == "Others") "📂 Other ✓" else "📂 Other"
                )
            )
        }
        
        // Build expanded text for BigTextStyle - cleaner format
        val expandedText = buildString {
            append(contentText)
            if (subtext.isNotEmpty()) {
                append("\n")
                append(subtext)
            }
        }
        
        // Build notification
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false) // Don't auto-cancel so actions work properly
            .setOnlyAlertOnce(true)
            .addAction(editMerchantAction)
        
        // Add all category actions
        categoryActions.forEach { action ->
            notificationBuilder.addAction(action)
        }
        
        notificationBuilder
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(expandedText)
                    .setBigContentTitle(title)
            )
            .setTimeoutAfter(600000) // Auto-dismiss after 10 minutes
        
        // Add subtext only if not empty
        if (subtext.isNotEmpty()) {
            notificationBuilder.setSubText(subtext)
        }
        
        // Set color based on transaction type
        if (type == "INCOME") {
            notificationBuilder.setColor(context.getColor(android.R.color.holo_green_dark))
        } else if (type == "EXPENSE") {
            notificationBuilder.setColor(context.getColor(android.R.color.holo_red_dark))
        }
        
        val notification = notificationBuilder.build()
        
        // Show notification
        try {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_BASE + transactionId.toInt(),
                notification
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing notification permission", e)
        }
    }
    
    private fun createCategoryAction(
        context: Context,
        transactionId: Long,
        notificationId: Int,
        category: String,
        label: String
    ): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_UPDATE_CATEGORY
            putExtra(NotificationActionReceiver.EXTRA_TRANSACTION_ID, transactionId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NotificationActionReceiver.EXTRA_CATEGORY, category)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            transactionId.toInt() + category.hashCode(), // Unique request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_compass,
            label,
            pendingIntent
        ).build()
    }
    
    private fun getTimestampFromContentProvider(context: Context, sender: String, body: String): Long? {
        return try {
            // Query for the SMS in the content provider using sender and body
            // We look for messages from the last minute to account for processing delay
            val oneMinuteAgo = System.currentTimeMillis() - 60000
            
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.DATE),
                "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.BODY} = ? AND ${Telephony.Sms.DATE} > ?",
                arrayOf(sender, body, oneMinuteAgo.toString()),
                "${Telephony.Sms.DATE} DESC"
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                    return it.getLong(dateIndex)
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error querying content provider for timestamp", e)
            null
        }
    }
    
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Transaction Notifications"
            val descriptionText = "Notifications for new transactions with quick edit options"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                setShowBadge(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}