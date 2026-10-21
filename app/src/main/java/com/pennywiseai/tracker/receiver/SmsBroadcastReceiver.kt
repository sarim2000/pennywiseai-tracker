package com.pennywiseai.tracker.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.PennyWiseApplication
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.manager.SmsTransactionProcessor
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.usecase.LearnCategoryUseCase
import com.pennywiseai.tracker.ui.components.forPicker
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that intercepts incoming SMS messages in real-time
 * and processes them for transaction data using the shared SmsTransactionProcessor.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SmsBroadcastReceiverEntryPoint {
        fun smsTransactionProcessor(): SmsTransactionProcessor
        fun transactionRepository(): TransactionRepository
        fun categoryRepository(): CategoryRepository
        fun detectBalanceDiscrepancy(): com.pennywiseai.tracker.domain.usecase.DetectBalanceDiscrepancyUseCase
    }

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
        const val ACTION_EDIT_TRANSACTION = "com.pennywiseai.tracker.ACTION_EDIT_TRANSACTION"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val CHANNEL_ID = "transaction_notifications"

        // Category actions occupy slots 0..1; the learned note's Undo slot 8; picker reserves slot 9.
        // Codes are unique per (transactionId, slot): txId*10+slot cannot alias across
        // transactions because take(2) bounds slot < 10. This numbering is used ONLY by
        // ACTION_CHANGE_CATEGORY broadcasts — one filterEquals space; activity intents
        // (content → MainActivity, picker → QuickCategoryPickerActivity) are separate
        // components and keep their own lanes.
        private const val CATEGORY_SLOT_BASE = 10
        private const val PICKER_SLOT = 9

        internal fun categoryRequestCode(transactionId: Long, slotIndex: Int): Int =
            (transactionId * CATEGORY_SLOT_BASE + slotIndex).toInt()

        internal fun pickerRequestCode(transactionId: Long): Int =
            (transactionId * CATEGORY_SLOT_BASE + PICKER_SLOT).toInt()

        /**
         * Two one-tap answers for "what was this for?": the most-used categories
         * the picker would offer for this type, topped up from the offered list
         * so a new install still gets buttons. "Others" is the question, never
         * an answer.
         */
        internal fun quickPicks(usedMostFirst: List<String>, offered: List<String>, exclude: String): List<String> {
            val skip = setOf(exclude, BuiltinCategoryNames.current("Others"))
            return (usedMostFirst.filter { it in offered } + offered).distinct().filterNot { it in skip }.take(2)
        }

        /** [quickPicks] for [transaction]: the whole usage ranking, filtered to its type afterwards. */
        internal suspend fun quickPicksFor(
            transaction: TransactionEntity,
            transactionRepository: TransactionRepository,
            categoryRepository: CategoryRepository
        ): List<String> {
            val offered = runCatching {
                categoryRepository.getVisibleCategories().first()
                    .forPicker(isIncome = transaction.transactionType == TransactionType.INCOME)
                    .map { it.name }
            }.getOrDefault(emptyList())
            val used = runCatching { transactionRepository.getCategoriesByUsage() }.getOrDefault(emptyList())
            return quickPicks(used, offered, exclude = transaction.category)
        }

        fun ensureChannel(context: Context): NotificationManager {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_txn_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.notif_txn_channel_description) }
            )
            return notificationManager
        }

        fun openTransactionIntent(context: Context, transactionId: Long): PendingIntent =
            PendingIntent.getActivity(
                context,
                transactionId.toInt(),
                Intent(context, MainActivity::class.java).apply {
                    action = ACTION_EDIT_TRANSACTION
                    putExtra(EXTRA_TRANSACTION_ID, transactionId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        /** [requestCode] keeps another notification's picker for the same transaction from overwriting this one's extras. */
        fun pickerIntent(
            context: Context,
            transactionId: Long,
            notificationId: Int,
            requestCode: Int = pickerRequestCode(transactionId)
        ): PendingIntent =
            PendingIntent.getActivity(
                context,
                requestCode,
                Intent(context, QuickCategoryPickerActivity::class.java).apply {
                    putExtra(QuickCategoryPickerActivity.EXTRA_TRANSACTION_ID, transactionId)
                    putExtra(QuickCategoryPickerActivity.EXTRA_NOTIFICATION_ID, notificationId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        /**
         * Replaces the alert with a short, silent "saved — and learned" note so
         * the user sees the tap landed and knows the next one is automatic, with
         * an Undo: a quick pick is one tap, and a mis-tap teaches the merchant
         * and moves its earlier rows. [learned] is [LearnCategoryUseCase]'s
         * result; null (the transaction is gone) just clears the alert.
         */
        fun showLearnedNotification(context: Context, notificationId: Int, learned: LearnCategoryUseCase.Learned?) {
            if (notificationId == -1) return
            val notificationManager = ensureChannel(context)
            if (learned == null) {
                notificationManager.cancel(notificationId)
                return
            }
            // Picks post from different threads, so check-and-post is one step.
            synchronized(LEARNED_LOCK) { postLearned(context, notificationManager, notificationId, learned) }
        }

        private fun postLearned(
            context: Context,
            notificationManager: NotificationManager,
            notificationId: Int,
            learned: LearnCategoryUseCase.Learned
        ) {
            // Only the merchant's latest lesson is undoable: an older note's
            // Undo would restore a mapping the newer pick replaced. A note that
            // lost the race to a newer lesson is dropped, never the newer one.
            val learnedAt = learned.mappedAt?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0L
            learned.merchant?.let { merchant ->
                val sameMerchant = notificationManager.activeNotifications.filter {
                    it.notification.extras.getString(EXTRA_LEARNED_MERCHANT).equals(merchant, ignoreCase = true)
                }
                if (sameMerchant.any { it.notification.extras.getLong(EXTRA_LEARNED_AT) > learnedAt }) {
                    notificationManager.cancel(notificationId)
                    return
                }
                sameMerchant.forEach { notificationManager.cancel(it.tag, it.id) }
            }
            val moved = learned.movedIds.size
            val text = when {
                learned.merchant == null -> ""
                moved > 0 -> context.getString(R.string.notif_txn_learned_text) + " " +
                    context.resources.getQuantityString(R.plurals.notif_txn_learned_moved, moved, moved)
                else -> context.getString(R.string.notif_txn_learned_text)
            }
            notificationManager.notify(
                notificationId,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(context.getString(R.string.notif_txn_learned_title, learned.category))
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(openTransactionIntent(context, learned.transactionId))
                    .addAction(
                        0,
                        context.getString(R.string.home_undo),
                        NotificationActionReceiver.undoIntent(
                            context,
                            categoryRequestCode(learned.transactionId, UNDO_SLOT),
                            notificationId,
                            learned
                        )
                    )
                    .addExtras(
                        android.os.Bundle().apply {
                            putString(EXTRA_LEARNED_MERCHANT, learned.merchant)
                            putLong(EXTRA_LEARNED_AT, learnedAt)
                        }
                    )
                    .setAutoCancel(true)
                    .setSilent(true)
                    .setTimeoutAfter(LEARNED_TIMEOUT_MS)
                    .build()
            )
        }

        // Long enough to reach Undo after the picker closes the shade.
        private const val LEARNED_TIMEOUT_MS = 15_000L
        private const val UNDO_SLOT = 8
        private const val EXTRA_LEARNED_MERCHANT = "com.pennywiseai.tracker.learned_merchant"
        private const val EXTRA_LEARNED_AT = "com.pennywiseai.tracker.learned_at"
        private val LEARNED_LOCK = Any()
    }

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            return
        }

        // Combine multi-part SMS messages with their timestamps
        data class SmsData(val body: StringBuilder, var timestamp: Long)
        val smsMap = mutableMapOf<String, SmsData>()
        for (message in messages) {
            val sender = message.originatingAddress ?: continue
            val body = message.messageBody ?: continue
            val timestamp = message.timestampMillis

            val existing = smsMap.getOrPut(sender) { SmsData(StringBuilder(), timestamp) }
            existing.body.append(body)
            // Use the earliest timestamp for multi-part messages
            if (timestamp < existing.timestamp) {
                existing.timestamp = timestamp
            }
        }

        // Get the processor via Hilt EntryPoint
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SmsBroadcastReceiverEntryPoint::class.java
        )
        val processor = entryPoint.smsTransactionProcessor()

        // Process each unique SMS
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                smsMap.map { (sender, smsData) ->
                    launch {
                        processIncomingSms(context, processor, sender, smsData.body.toString(), smsData.timestamp)
                    }
                }.joinAll()
            } finally {
                pendingResult.finish()
            }
        }
    }

    // Internal so the debug-only SmsProbeReceiver can drive the real path with a made-up SMS.
    internal suspend fun processIncomingSms(
        context: Context,
        processor: SmsTransactionProcessor,
        sender: String,
        body: String,
        timestamp: Long
    ) {
        try {
            // Use the shared processor to parse and save the transaction
            val result = processor.processAndSaveTransaction(sender, body, timestamp)
            if (!result.success || result.transactionId == null) {
                Log.d(TAG, "Transaction not saved: ${result.reason}")
                return
            }
            Log.d(TAG, "Transaction saved with ID: ${result.transactionId}")

            // The open app shows the new row itself.
            if (isAppInForeground(context)) return

            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                SmsBroadcastReceiverEntryPoint::class.java
            )
            val repository = entryPoint.transactionRepository()
            val saved = repository.getTransactionById(result.transactionId) ?: return
            // #734: tell the user right away when the bank's balance disagrees
            // with the ledger — the detail screen offers the fix.
            val discrepancyLine = runCatching { entryPoint.detectBalanceDiscrepancy().execute(saved) }
                .getOrNull()
                ?.let { d ->
                    context.getString(
                        R.string.notif_txn_balance_discrepancy,
                        CurrencyFormatter.formatCurrency(d.delta.abs(), d.currency),
                        CurrencyFormatter.formatCurrency(d.expected, d.currency),
                        CurrencyFormatter.formatCurrency(d.reported, d.currency)
                    )
                }
            showTransactionNotification(context, saved, repository, entryPoint.categoryRepository(), discrepancyLine)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS", e)
        }
    }

    private fun isAppInForeground(context: Context): Boolean {
        return try {
            val application = context.applicationContext as? PennyWiseApplication
            application?.isAppInForeground ?: false
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun showTransactionNotification(
        context: Context,
        transaction: TransactionEntity,
        repository: TransactionRepository,
        categoryRepository: CategoryRepository,
        discrepancyLine: String? = null
    ) {
        try {
            val notificationManager = ensureChannel(context)
            val transactionId = transaction.id
            val pendingIntent = openTransactionIntent(context, transactionId)

            // Lock-screen version only; the full title says it in words.
            val typeEmoji = when (transaction.transactionType) {
                TransactionType.EXPENSE -> "💸"
                TransactionType.INCOME -> "💰"
                TransactionType.CREDIT -> "💳"
                TransactionType.TRANSFER -> "🔄"
                TransactionType.INVESTMENT -> "📈"
            }

            val amount = CurrencyFormatter.formatCurrency(transaction.amount, transaction.currency)
            val merchant = transaction.merchantName.ifBlank { context.getString(R.string.notif_txn_unknown_merchant) }
            val bankName = transaction.bankName ?: context.getString(R.string.notif_txn_unknown_bank)
            val title = when (transaction.transactionType) {
                TransactionType.INCOME -> context.getString(R.string.notif_txn_title_received, amount, merchant)
                TransactionType.EXPENSE, TransactionType.CREDIT ->
                    context.getString(R.string.notif_txn_title_spent, amount, merchant)
                else -> context.getString(R.string.notif_txn_title_other, amount, merchant)
            }
            // Unsorted: ask, with one-tap answers. Sorted: just say where it went.
            val uncategorized = transaction.category == BuiltinCategoryNames.current("Others")
            val summaryLine = if (uncategorized) {
                context.getString(R.string.notif_txn_ask_category, bankName)
            } else {
                context.getString(R.string.notif_txn_category_bank, transaction.category, bankName)
            }
            val content = listOfNotNull(summaryLine, discrepancyLine).joinToString("\n")

            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            // Notification action slots are precious (Android collapses past 3),
            // so the last opens the full picker and the first two are the
            // user's most-used categories of this type, for one-tap answers. (#303)
            val notificationId = transactionId.toInt()
            if (uncategorized) {
                quickPicksFor(transaction, repository, categoryRepository).forEachIndexed { index, pick ->
                    val categoryIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                        action = NotificationActionReceiver.ACTION_CHANGE_CATEGORY
                        putExtra(NotificationActionReceiver.EXTRA_TRANSACTION_ID, transactionId)
                        putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                        putExtra(NotificationActionReceiver.EXTRA_NEW_CATEGORY, pick)
                    }
                    val categoryPendingIntent = PendingIntent.getBroadcast(
                        context,
                        categoryRequestCode(transactionId, index),
                        categoryIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    notificationBuilder.addAction(0, pick, categoryPendingIntent)
                }
            }

            // The translucent picker: any category, not just the quick picks.
            notificationBuilder.addAction(
                0,
                context.getString(if (uncategorized) R.string.notif_txn_action_more else R.string.notif_txn_action_change),
                pickerIntent(context, transactionId, notificationId)
            )

            // Account balances stay off the lock screen (#734): private visibility
            // with a public version that carries no figures.
            if (discrepancyLine != null) {
                notificationBuilder
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(
                        NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setContentTitle(context.getString(R.string.notif_txn_public_title, typeEmoji))
                            .setContentText(context.getString(R.string.notif_txn_public_text, bankName))
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                            .build()
                    )
            }

            notificationManager.notify(notificationId, notificationBuilder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification", e)
        }
    }
}
