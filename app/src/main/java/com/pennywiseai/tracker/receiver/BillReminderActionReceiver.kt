package com.pennywiseai.tracker.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.domain.usecase.MarkSubscriptionPaidUseCase
import com.pennywiseai.tracker.widget.WidgetRefresher
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The "bill due tomorrow" reminder's actions: mark it paid (as the in-app button does), or skip this cycle. */
class BillReminderActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BillReminderActionReceiverEntryPoint {
        fun markSubscriptionPaidUseCase(): MarkSubscriptionPaidUseCase
        fun subscriptionRepository(): SubscriptionRepository
    }

    companion object {
        private const val TAG = "BillReminderAction"

        const val ACTION_PAID = "com.pennywiseai.tracker.ACTION_BILL_PAID"
        const val ACTION_SKIP = "com.pennywiseai.tracker.ACTION_BILL_SKIP"
        private const val EXTRA_SUBSCRIPTION_ID = "subscription_id"
        private const val EXTRA_DUE_DATE = "due_date"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun actionIntent(context: Context, action: String, subscriptionId: Long, dueDate: LocalDate, notificationId: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                // Distinct per action and per subscription, so neither overwrites the other.
                notificationId * 2 + if (action == ACTION_PAID) 0 else 1,
                Intent(context, BillReminderActionReceiver::class.java).apply {
                    this.action = action
                    putExtra(EXTRA_SUBSCRIPTION_ID, subscriptionId)
                    putExtra(EXTRA_DUE_DATE, dueDate.toString())
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val subscriptionId = intent.getLongExtra(EXTRA_SUBSCRIPTION_ID, -1)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val dueDate = intent.getStringExtra(EXTRA_DUE_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (subscriptionId == -1L || dueDate == null) {
            Log.e(TAG, "Missing subscription or due date")
            return
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            BillReminderActionReceiverEntryPoint::class.java
        )
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val repository = entryPoint.subscriptionRepository()
                val sub = repository.getSubscriptionById(subscriptionId)
                // A stale reminder (already paid, skipped, edited, hidden or ended since)
                // must not move the schedule again.
                if (sub == null || sub.state != SubscriptionState.ACTIVE || sub.nextPaymentDate != dueDate) return@launch
                when (intent.action) {
                    ACTION_PAID -> {
                        val result = entryPoint.markSubscriptionPaidUseCase().execute(subscriptionId)
                        if (result is MarkSubscriptionPaidUseCase.Result.Created) {
                            WidgetRefresher.refreshTransactionWidgets(context)
                        }
                    }
                    ACTION_SKIP -> repository.updateNextPaymentDate(subscriptionId, repository.advance(dueDate, sub.billingCycle))
                    else -> Log.w(TAG, "Unknown action: ${intent.action}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling bill reminder action", e)
            } finally {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notificationId)
                pendingResult.finish()
            }
        }
    }
}
