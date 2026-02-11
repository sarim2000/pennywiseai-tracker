package com.pennywiseai.tracker.receiver

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.pennywiseai.tracker.data.repository.BankNotificationRepository
import com.pennywiseai.tracker.data.manager.SmsTransactionProcessor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Notification listener that ingests bank app notifications and routes them
 * through the existing parser pipeline.
 */
class BankNotificationListenerService : NotificationListenerService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NotificationListenerEntryPoint {
        fun smsTransactionProcessor(): SmsTransactionProcessor
        fun bankNotificationRepository(): BankNotificationRepository
    }

    companion object {
        private const val TAG = "BankNotificationListener"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return

        if (!BankNotificationConfig.isAllowed(packageName)) {
            return
        }

        // Skip group summaries to avoid duplicate processing
        if ((sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0) {
            return
        }

        val body = BankNotificationConfig.extractMessage(sbn.notification)
        if (body.isBlank()) {
            return
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            NotificationListenerEntryPoint::class.java
        )
        val processor = entryPoint.smsTransactionProcessor()
        val notificationRepository = entryPoint.bankNotificationRepository()
        val senderAlias = BankNotificationConfig.senderAlias(packageName)
        val timestamp = sbn.postTime

        serviceScope.launch {
            var notificationId: Long? = null
            try {
                notificationId = notificationRepository.logNotification(
                    packageName = packageName,
                    senderAlias = senderAlias,
                    messageBody = body,
                    postedAtMillis = timestamp
                ).takeIf { it > 0 }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to store bank notification", e)
            }

            try {
                val result = processor.processAndSaveTransaction(
                    sender = senderAlias,
                    body = body,
                    timestamp = timestamp
                )
                if (!result.success) {
                    Log.d(TAG, "Notification skipped: ${result.reason}")
                } else if (notificationId != null) {
                    notificationRepository.markProcessed(notificationId, result.transactionId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process bank notification", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
