package com.pennywiseai.tracker.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pennywiseai.tracker.domain.usecase.DeleteTransactionUseCase
import com.pennywiseai.tracker.domain.usecase.LearnCategoryUseCase
import com.pennywiseai.tracker.widget.WidgetRefresher
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that handles actions from transaction notifications.
 * Supports updating merchant, category, confirming, and deleting transactions.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NotificationActionReceiverEntryPoint {
        fun learnCategoryUseCase(): LearnCategoryUseCase
        fun deleteTransactionUseCase(): DeleteTransactionUseCase
    }

    companion object {
        private const val TAG = "NotificationActionReceiver"

        const val ACTION_DELETE_TRANSACTION = "com.pennywiseai.tracker.ACTION_DELETE_TRANSACTION"
        const val ACTION_CONFIRM_TRANSACTION = "com.pennywiseai.tracker.ACTION_CONFIRM_TRANSACTION"
        const val ACTION_CHANGE_CATEGORY = "com.pennywiseai.tracker.ACTION_CHANGE_CATEGORY"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_NEW_CATEGORY = "new_category"
        const val ACTION_UNDO_LEARN = "com.pennywiseai.tracker.ACTION_UNDO_LEARN"
        private const val EXTRA_PREVIOUS_CATEGORY = "previous_category"
        private const val EXTRA_MERCHANT = "merchant"
        private const val EXTRA_PREVIOUS_MAPPING = "previous_mapping"
        private const val EXTRA_MOVED_IDS = "moved_ids"
        private const val EXTRA_MAPPED_AT = "mapped_at"

        /** The learned note's Undo: carries everything [LearnCategoryUseCase.undo] needs. */
        fun undoIntent(context: Context, requestCode: Int, notificationId: Int, learned: LearnCategoryUseCase.Learned): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, NotificationActionReceiver::class.java).apply {
                    action = ACTION_UNDO_LEARN
                    putExtra(EXTRA_TRANSACTION_ID, learned.transactionId)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    putExtra(EXTRA_NEW_CATEGORY, learned.category)
                    putExtra(EXTRA_PREVIOUS_CATEGORY, learned.previousCategory)
                    putExtra(EXTRA_MERCHANT, learned.merchant)
                    putExtra(EXTRA_PREVIOUS_MAPPING, learned.previousMapping)
                    putExtra(EXTRA_MOVED_IDS, learned.movedIds.toLongArray())
                    putExtra(EXTRA_MAPPED_AT, learned.mappedAt?.toString())
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun Intent.toLearned(transactionId: Long): LearnCategoryUseCase.Learned? {
            val category = getStringExtra(EXTRA_NEW_CATEGORY) ?: return null
            val previous = getStringExtra(EXTRA_PREVIOUS_CATEGORY) ?: return null
            return LearnCategoryUseCase.Learned(
                transactionId = transactionId,
                category = category,
                previousCategory = previous,
                merchant = getStringExtra(EXTRA_MERCHANT),
                previousMapping = getStringExtra(EXTRA_PREVIOUS_MAPPING),
                mappedAt = getStringExtra(EXTRA_MAPPED_AT)?.let { runCatching { java.time.LocalDateTime.parse(it) }.getOrNull() },
                movedIds = getLongArrayExtra(EXTRA_MOVED_IDS)?.toList().orEmpty()
            )
        }
    }

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        if (transactionId == -1L) {
            Log.e(TAG, "No transaction ID provided")
            return
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            NotificationActionReceiverEntryPoint::class.java
        )
        val deleteTransactionUseCase = entryPoint.deleteTransactionUseCase()

        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                when (intent.action) {
                    ACTION_DELETE_TRANSACTION -> {
                        deleteTransaction(context, deleteTransactionUseCase, transactionId, notificationId)
                    }
                    ACTION_CONFIRM_TRANSACTION -> {
                        confirmTransaction(context, notificationId)
                    }
                    ACTION_CHANGE_CATEGORY -> {
                        val newCategory = intent.getStringExtra(EXTRA_NEW_CATEGORY)
                        if (newCategory != null) {
                            changeCategory(context, entryPoint.learnCategoryUseCase(), transactionId, newCategory, notificationId)
                        } else {
                            Log.e(TAG, "No category provided")
                        }
                    }
                    ACTION_UNDO_LEARN -> {
                        runCatching {
                            intent.toLearned(transactionId)?.let { entryPoint.learnCategoryUseCase().undo(it) }
                            WidgetRefresher.refreshTransactionWidgets(context)
                        }.onFailure { Log.e(TAG, "Error undoing a learned category", it) }
                        dismissNotification(context, notificationId)
                    }
                    else -> {
                        Log.w(TAG, "Unknown action: ${intent.action}")
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun deleteTransaction(
        context: Context,
        deleteTransactionUseCase: DeleteTransactionUseCase,
        transactionId: Long,
        notificationId: Int
    ) {
        try {
            val deleted = deleteTransactionUseCase(transactionId)
            if (deleted) {
                WidgetRefresher.refreshTransactionWidgets(context)
                Log.d(TAG, "Deleted transaction and shifted balance: $transactionId")
            } else {
                Log.w(TAG, "Transaction not found for deletion: $transactionId")
            }

            // Dismiss the notification
            dismissNotification(context, notificationId)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting transaction", e)
        }
    }

    private fun confirmTransaction(context: Context, notificationId: Int) {
        // Simply dismiss the notification - transaction is already saved
        dismissNotification(context, notificationId)
        Log.d(TAG, "Transaction confirmed, notification dismissed")
    }

    private suspend fun changeCategory(
        context: Context,
        learnCategory: LearnCategoryUseCase,
        transactionId: Long,
        newCategory: String,
        notificationId: Int
    ) {
        try {
            val learned = learnCategory(transactionId, newCategory)
            WidgetRefresher.refreshTransactionWidgets(context)
            SmsBroadcastReceiver.showLearnedNotification(context, notificationId, learned)
        } catch (e: Exception) {
            Log.e(TAG, "Error changing category", e)
        }
    }

    private fun dismissNotification(context: Context, notificationId: Int) {
        if (notificationId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }
    }
}
