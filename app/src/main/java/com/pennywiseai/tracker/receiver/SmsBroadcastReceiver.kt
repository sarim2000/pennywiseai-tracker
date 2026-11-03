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
import androidx.core.os.BundleCompat
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.PennyWiseApplication
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.manager.SmsTransactionProcessor
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.LearnedSendersStore
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.UnrecognizedSmsRepository
import com.pennywiseai.tracker.ui.screens.unrecognized.Suggestion
import com.pennywiseai.parser.core.GenericTransactionExtractor
import com.pennywiseai.parser.core.bank.BankParserFactory
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.usecase.LearnCategoryUseCase
import com.pennywiseai.tracker.domain.usecase.TrackRepeatChargeUseCase
import com.pennywiseai.tracker.ui.components.forPicker
import com.pennywiseai.tracker.utils.BalanceDiscrepancy
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
import com.pennywiseai.tracker.ui.icons.CategoryMapping

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
        fun trackRepeatChargeUseCase(): TrackRepeatChargeUseCase
        fun learnedSendersStore(): LearnedSendersStore
        fun ignoredAccountsStore(): IgnoredAccountsStore
        fun unrecognizedSmsRepository(): UnrecognizedSmsRepository
        fun userPreferencesRepository(): com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
    }

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
        const val ACTION_EDIT_TRANSACTION = "com.pennywiseai.tracker.ACTION_EDIT_TRANSACTION"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val CHANNEL_ID = "transaction_notifications"

        // Category actions occupy slots 0..1; Not an expense 2 and its Undo 3; "Track as … bill" /
        // "Update price" slot 7; the learned note's Undo slot 8; picker reserves slot 9.
        // Codes are unique per (transactionId, slot): txId*10+slot cannot alias across
        // transactions because take(2) bounds slot < 10. This numbering is used ONLY by
        // NotificationActionReceiver broadcasts — one filterEquals space; activity intents
        // (content → MainActivity, picker → QuickCategoryPickerActivity) are separate
        // components and keep their own lanes.
        private const val CATEGORY_SLOT_BASE = 10
        private const val PICKER_SLOT = 9
        private const val TRACK_SLOT = 7

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

        /**
         * Android shows three actions, and the picker always takes the last.
         * A bill's "Track"/"Update price" ([billAction]) goes first; a bill is
         * spending, so it never also offers "Not an expense". Otherwise expenses
         * and income get "Not an expense"/"Not income", so an unsorted one keeps
         * a single quick pick; transfers and investments aren't counted as
         * either, so they keep both. Returns (quick picks, exclude?).
         */
        internal fun alertLayout(
            type: TransactionType,
            uncategorized: Boolean,
            alreadyExcluded: Boolean,
            billAction: Boolean = false
        ): Pair<Int, Boolean> {
            val exclude = !billAction && !alreadyExcluded &&
                type != TransactionType.TRANSFER && type != TransactionType.INVESTMENT
            val picks = if (uncategorized) 2 - listOf(billAction, exclude).count { it } else 0
            return picks to exclude
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

        // Suggestion alerts share the transaction channel; the tag keeps their ids apart from transaction ids.
        private const val SUGGESTION_TAG = "sms_suggestion"

        // Its own action keeps the alert's PendingIntent from matching (and overwriting) another MainActivity one.
        private const val ACTION_ADD_SUGGESTION = "com.pennywiseai.tracker.ACTION_ADD_SUGGESTION"
        private const val RECURRING_OFFER_INTERVAL_DAYS = 90L

        fun cancelSuggestion(context: Context, unrecognizedSmsId: Long) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(SUGGESTION_TAG, unrecognizedSmsId.toInt())
        }

        fun cancelAllSuggestions(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.activeNotifications
                .filter { it.tag == SUGGESTION_TAG }
                .forEach { notificationManager.cancel(it.tag, it.id) }
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
            } else {
                // Picks post from different threads, so check-and-post is one step.
                synchronized(LEARNED_LOCK) { postLearned(context, notificationManager, notificationId, learned) }
            }
            // The note replaces the alert outside the stack, so the stack is one shorter.
            updateAlertGroup(context, removedId = notificationId)
        }

        /**
         * Keeps the header over the stacked alerts in step: posted from the
         * second alert on, recounted as they're answered, gone with the last.
         * Cancelling a summary cancels its children too, so it's only ever
         * dropped once the stack is empty. notify() and cancel() land
         * asynchronously, so the caller's own change is passed in rather than
         * read back from the shade.
         *
         * Two at once (a bank's two texts) each read a shade that showed
         * neither yet, so each counted one and no header went up; one recount
         * after the shade has caught up settles it.
         */
        fun updateAlertGroup(
            context: Context,
            posted: Pair<Int, android.app.Notification>? = null,
            removedId: Int? = null
        ) {
            syncAlertGroup(context, posted, removedId)
            val app = context.applicationContext
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ syncAlertGroup(app) }, GROUP_SETTLE_MS)
        }

        private fun syncAlertGroup(
            context: Context,
            posted: Pair<Int, android.app.Notification>? = null,
            removedId: Int? = null
        ) = synchronized(GROUP_LOCK) {
            val notificationManager = ensureChannel(context)
            val active = notificationManager.activeNotifications
            val alerts = active.filter { it.notification.group == GROUP_KEY && it.id != GROUP_SUMMARY_ID }
                .associate { it.id to it.notification }
                .toMutableMap()
            removedId?.let(alerts::remove)
            posted?.let { (id, notification) -> alerts[id] = notification }
            when {
                alerts.isEmpty() -> notificationManager.cancel(GROUP_SUMMARY_ID)
                alerts.size == 1 && active.none { it.id == GROUP_SUMMARY_ID } -> Unit
                else -> {
                    val title = context.resources.getQuantityString(R.plurals.notif_txn_group_title, alerts.size, alerts.size)
                    val lines = alerts.values.sortedByDescending { it.`when` }
                        .mapNotNull { it.extras.getCharSequence(android.app.Notification.EXTRA_TITLE) }
                    notificationManager.notify(
                        GROUP_SUMMARY_ID,
                        NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setContentTitle(title)
                            .setContentText(lines.firstOrNull())
                            .setStyle(NotificationCompat.InboxStyle().setBigContentTitle(title).also { style -> lines.forEach(style::addLine) })
                            .setContentIntent(
                                PendingIntent.getActivity(
                                    context,
                                    GROUP_SUMMARY_ID,
                                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                            )
                            .setGroup(GROUP_KEY)
                            .setGroupSummary(true)
                            // The alerts ring; their header never does.
                            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                            .setAutoCancel(true)
                            .build()
                    )
                }
            }
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
                val newer = sameMerchant.filter { it.notification.extras.getLong(EXTRA_LEARNED_AT) > learnedAt }
                if (newer.isNotEmpty()) {
                    // Clear this pick's own alert, unless the newer note already sits
                    // in its slot (both picks came from the same alert).
                    if (newer.none { it.id == notificationId && it.tag == null }) notificationManager.cancel(notificationId)
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
            // An alert still asking "Track as … bill" / "Update price" hands that
            // action to the note, and Undo puts the alert back, so a category pick
            // never costs the bill's answer.
            val billAlert = notificationManager.activeNotifications
                .firstOrNull { it.id == notificationId && it.tag == null }?.notification
                ?.takeIf { it.extras.getBoolean(EXTRA_BILL_ACTION) }
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.notif_txn_learned_title, CategoryMapping.label(context, learned.category)))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openTransactionIntent(context, learned.transactionId))
            billAlert?.actions?.firstOrNull()?.let { builder.addAction(0, it.title, it.actionIntent) }
            builder
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
                        if (billAlert != null) {
                            putBoolean(EXTRA_BILL_ACTION, true)
                            // A second pick replaces this note: keep the first alert.
                            putParcelable(EXTRA_ALERT, alertBehind(billAlert) ?: billAlert)
                        }
                    }
                )
                .setAutoCancel(true)
                .setSilent(true)
            // A pending bill answer stays until it's given; a plain note times out.
            if (billAlert == null) builder.setTimeoutAfter(LEARNED_TIMEOUT_MS)
            notificationManager.notify(notificationId, builder.build())
        }

        private fun alertBehind(note: android.app.Notification): android.app.Notification? =
            BundleCompat.getParcelable(note.extras, EXTRA_ALERT, android.app.Notification::class.java)

        /**
         * The learned note's Undo: brings back the alert it replaced when that
         * one still had a bill to answer, else just clears the note.
         */
        fun undoLearnedNotification(context: Context, notificationId: Int) {
            if (notificationId == -1) return
            val notificationManager = ensureChannel(context)
            val alert = notificationManager.activeNotifications
                .firstOrNull { it.id == notificationId && it.tag == null }
                ?.notification?.let(::alertBehind)
            if (alert == null) {
                notificationManager.cancel(notificationId)
                updateAlertGroup(context, removedId = notificationId)
            } else {
                // The note still holds this slot, so the alert returns without a second sound.
                alert.flags = alert.flags or android.app.Notification.FLAG_ONLY_ALERT_ONCE
                notificationManager.notify(notificationId, alert)
                updateAlertGroup(context, posted = notificationId to alert)
            }
        }

        /** Labels for a [SubscriptionEntity.billingCycle]: the alert's action, and the cycle as a word. */
        private fun trackActionLabel(cycle: String): Int = when (cycle) {
            "Weekly" -> R.string.recurring_alert_track_weekly
            "Quarterly" -> R.string.recurring_alert_track_quarterly
            "Semi-Annual" -> R.string.recurring_alert_track_semi_annual
            "Annual" -> R.string.recurring_alert_track_annual
            else -> R.string.recurring_alert_track_monthly
        }

        private fun cycleLabel(cycle: String): Int = when (cycle) {
            "Weekly" -> R.string.recurring_alert_cycle_weekly
            "Quarterly" -> R.string.recurring_alert_cycle_quarterly
            "Semi-Annual" -> R.string.recurring_alert_cycle_semi_annual
            "Annual" -> R.string.recurring_alert_cycle_annual
            else -> R.string.recurring_alert_cycle_monthly
        }

        /**
         * Replaces the alert with a short, silent "Tracking Netflix · monthly ·
         * next on 24 Nov" once "Track as … bill" landed, or "Netflix now ₹240 ·
         * next on 24 Nov" once "Update price" did ([priceUpdated]). [tracked]
         * null (nothing changed) just clears the alert.
         */
        fun showTrackingNotification(
            context: Context,
            notificationId: Int,
            transactionId: Long,
            tracked: SubscriptionEntity?,
            priceUpdated: Boolean = false
        ) {
            if (notificationId == -1) return
            val notificationManager = ensureChannel(context)
            if (tracked == null) {
                notificationManager.cancel(notificationId)
            } else {
                val next = tracked.nextPaymentDate?.format(java.time.format.DateTimeFormatter.ofPattern("d MMM")).orEmpty()
                val text = if (priceUpdated) {
                    context.getString(
                        R.string.recurring_alert_price_updated,
                        tracked.merchantName,
                        CurrencyFormatter.formatCurrency(tracked.amount, tracked.currency, maskIfHidden = false),
                        next
                    )
                } else {
                    context.getString(
                        R.string.recurring_alert_tracking,
                        tracked.merchantName,
                        context.getString(cycleLabel(tracked.billingCycle)),
                        next
                    )
                }
                notificationManager.notify(
                    notificationId,
                    NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle(text)
                        .setContentIntent(openTransactionIntent(context, transactionId))
                        .setAutoCancel(true)
                        .setSilent(true)
                        .setTimeoutAfter(LEARNED_TIMEOUT_MS)
                        .build()
                )
            }
            updateAlertGroup(context, removedId = notificationId)
        }

        /**
         * #734: the bank's balance disagrees with the ledger. Its own alert, one
         * per account (a newer one replaces it), outside the transaction stack;
         * the transaction's detail screen offers the fix. Balances stay off the
         * lock screen: private, with a public version that carries no figures.
         */
        internal fun showBalanceDiscrepancyNotification(context: Context, transaction: TransactionEntity, discrepancy: BalanceDiscrepancy) {
            val bankName = transaction.bankName ?: context.getString(R.string.notif_txn_unknown_bank)
            val difference = CurrencyFormatter.formatCurrency(discrepancy.delta.abs(), discrepancy.currency, maskIfHidden = false)
            val title = context.getString(
                if (discrepancy.delta.signum() > 0) R.string.notif_balance_higher else R.string.notif_balance_lower,
                difference
            )
            // The bank opens the text: in the title (or One UI's inline header) a long name cut the figure off.
            val text = "$bankName · ${context.getString(R.string.notif_balance_text)}"
            val pendingIntent = openTransactionIntent(context, transaction.id)
            ensureChannel(context).notify(
                BALANCE_TAG,
                balanceAlertId(transaction),
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(
                        NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setContentTitle(context.getString(R.string.notif_txn_public_text, bankName))
                            .setContentText(text)
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                            .build()
                    )
                    .build()
            )
        }

        // The tag keeps balance alerts' ids (per account) apart from transaction ids.
        private const val BALANCE_TAG = "balance_discrepancy"
        private fun balanceAlertId(transaction: TransactionEntity) = "${transaction.bankName}|${transaction.accountNumber}".hashCode()

        /** The account's gap closed (an adjustment, or a newer SMS that agrees): its alert goes too. */
        fun cancelBalanceDiscrepancyNotification(context: Context, transaction: TransactionEntity) {
            ensureChannel(context).cancel(BALANCE_TAG, balanceAlertId(transaction))
        }

        /**
         * Replaces the alert after "Not an expense"/"Not income": a short, silent
         * note with an Undo, outside the stack like the learned note. The row
         * stays (deleting would shift the balance ledger); it just stops counting.
         */
        fun showExcludedNotification(context: Context, notificationId: Int, transaction: TransactionEntity) {
            if (notificationId == -1) return
            val title = context.getString(
                if (transaction.transactionType == TransactionType.INCOME) R.string.notif_txn_excluded_income
                else R.string.notif_txn_excluded_spending
            )
            val merchant = transaction.merchantName.ifBlank { context.getString(R.string.notif_txn_unknown_merchant) }
            ensureChannel(context).notify(
                notificationId,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(title)
                    .setContentText("$merchant · ${CurrencyFormatter.formatCurrency(transaction.amount, transaction.currency, maskIfHidden = false)}")
                    .setContentIntent(openTransactionIntent(context, transaction.id))
                    .addAction(
                        0,
                        context.getString(R.string.home_undo),
                        NotificationActionReceiver.excludeIntent(
                            context,
                            categoryRequestCode(transaction.id, UNDO_EXCLUDE_SLOT),
                            transaction.id,
                            notificationId,
                            undo = true
                        )
                    )
                    .setAutoCancel(true)
                    .setSilent(true)
                    .setTimeoutAfter(LEARNED_TIMEOUT_MS)
                    .build()
            )
            updateAlertGroup(context, removedId = notificationId)
        }

        // Long enough to reach Undo after the picker closes the shade.
        private const val LEARNED_TIMEOUT_MS = 15_000L
        private const val UNDO_SLOT = 8
        private const val EXCLUDE_SLOT = 2
        private const val UNDO_EXCLUDE_SLOT = 3
        private const val EXTRA_LEARNED_MERCHANT = "com.pennywiseai.tracker.learned_merchant"
        private const val EXTRA_LEARNED_AT = "com.pennywiseai.tracker.learned_at"
        // Set on an alert (and its learned note) whose first action is "Track as … bill" / "Update price".
        private const val EXTRA_BILL_ACTION = "com.pennywiseai.tracker.bill_action"
        private const val EXTRA_ALERT = "com.pennywiseai.tracker.alert"
        private val LEARNED_LOCK = Any()

        // A busy day is one stack in the shade, not a wall of alerts.
        private const val GROUP_KEY = "com.pennywiseai.tracker.TRANSACTION_ALERTS"
        // Transaction alerts use the transaction id (never negative); the recap is -2.
        private const val GROUP_SUMMARY_ID = -3
        private val GROUP_LOCK = Any()
        private const val GROUP_SETTLE_MS = 1_000L

        /**
         * #994: an SMS from a sender the user has added a suggestion from is offered
         * right away, in Unrecognized SMS and as an alert that opens Add pre-filled.
         * It is never saved on its own; the user's Add is the confirmation.
         * A notification from an app the user chose to track ([fromTrackedApp]) is
         * always listed when it reads as a transaction; the alert still waits until
         * the user has added one from that app.
         */
        internal suspend fun suggestFromLearnedSender(
            context: Context,
            sender: String,
            body: String,
            timestamp: Long,
            fromTrackedApp: Boolean = false
        ) {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                SmsBroadcastReceiverEntryPoint::class.java
            )
            val learned = entryPoint.learnedSendersStore()
            // A parser that turned this SMS down (an OTP, a promo) knows the sender better than we do.
            val isLearned = learned.isLearned(sender)
            if (!fromTrackedApp && (!isLearned || BankParserFactory.getParsers(sender).isNotEmpty())) return
            val transaction = GenericTransactionExtractor.extract(sender, body)?.transaction ?: return
            val learnedAccount = learned.accountFor(sender)
            LearnedSendersStore.accountToUse(learnedAccount, transaction.accountLast4, transaction.currency)?.let {
                if (entryPoint.ignoredAccountsStore().isIgnored(it.bankName, it.accountLast4)) return
            }
            val sms = UnrecognizedSmsEntity(
                sender = sender,
                smsBody = body,
                receivedAt = java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(timestamp), java.time.ZoneId.systemDefault()
                )
            )
            // Already offered (the same SMS delivered twice): -1, no second alert.
            val id = entryPoint.unrecognizedSmsRepository().insertOrRevive(sms).takeIf { it > 0 } ?: return
            if (isLearned) showSuggestionNotification(context, Suggestion(sms.copy(id = id), transaction, learnedAccount))
        }

        private fun showSuggestionNotification(context: Context, suggestion: Suggestion) {
            val draft = suggestion.draft()
            val tx = suggestion.transaction
            val amount = CurrencyFormatter.formatCurrency(tx.amount, tx.currency, maskIfHidden = false)
            val merchant = tx.merchant ?: context.getString(R.string.notif_txn_unknown_merchant)
            val title = if (tx.type == com.pennywiseai.parser.core.TransactionType.INCOME) {
                context.getString(R.string.notif_suggestion_title_received, amount, merchant)
            } else {
                context.getString(R.string.notif_suggestion_title_spent, amount, merchant)
            }
            val intent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_ADD_SUGGESTION
                putExtra(MainActivity.EXTRA_OPEN_ADD_TRANSACTION, true)
                putExtra(MainActivity.EXTRA_ADD_AMOUNT, draft.amount)
                putExtra(MainActivity.EXTRA_ADD_MERCHANT, draft.merchant)
                putExtra(MainActivity.EXTRA_ADD_BANK, draft.bankName)
                putExtra(MainActivity.EXTRA_ADD_LAST4, draft.accountLast4)
                putExtra(MainActivity.EXTRA_ADD_CURRENCY, draft.currency)
                putExtra(MainActivity.EXTRA_ADD_TYPE, draft.type)
                putExtra(MainActivity.EXTRA_ADD_DATE_TIME, draft.dateTime)
                putExtra(MainActivity.EXTRA_ADD_SMS_ID, suggestion.sms.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val notificationId = suggestion.sms.id.toInt()
            ensureChannel(context).notify(
                SUGGESTION_TAG,
                notificationId,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(title)
                    .setContentText(context.getString(R.string.notif_suggestion_text, BankNotificationConfig.displayName(context, suggestion.sms.sender)))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(
                        PendingIntent.getActivity(
                            context,
                            notificationId,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    .setAutoCancel(true)
                    .build()
            )
        }
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
                suggestFromLearnedSender(context, sender, body, timestamp)
                return
            }
            Log.d(TAG, "Transaction saved with ID: ${result.transactionId}")

            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                SmsBroadcastReceiverEntryPoint::class.java
            )
            val repository = entryPoint.transactionRepository()
            val saved = repository.getTransactionById(result.transactionId) ?: return
            // The same bill as last month (or week, or year)? Offer to track it.
            // Capped (one offer per merchant per 90 days, one a day): a missed offer
            // isn't lost, the Subscriptions screen lists it under "Suggested".
            // An offer nobody can see mustn't use up the merchant's 90 days: skip it
            // while the alerts are off, and hand the slot back if posting fails.
            val offerKey = com.pennywiseai.tracker.domain.spending.RecurringSuggestions.keyFor(saved.merchantName, saved.currency)
            val today = java.time.LocalDate.now()
            val trackCycle = runCatching {
                entryPoint.trackRepeatChargeUseCase().suggestedCycle(saved)?.takeIf {
                    canShowTransactionAlerts(context) &&
                        entryPoint.userPreferencesRepository().tryRecordRecurringOffer(offerKey, today, RECURRING_OFFER_INTERVAL_DAYS)
                }
            }
                .onFailure { Log.e(TAG, "Error checking for a repeat charge", it) }
                .getOrNull()
            // The open app shows the new row itself, but has nowhere to ask "Track
            // as … bill?" or "Update price?", so those still get the alert.
            if (result.priceChange == null && trackCycle == null && isAppInForeground(context)) return
            // Maybe a subscription at a new price: ask, don't apply.
            val priceLine = result.priceChange?.let { change ->
                context.getString(
                    if (saved.amount > change.previousAmount) R.string.recurring_alert_price_up else R.string.recurring_alert_price_down,
                    change.subscriptionName,
                    CurrencyFormatter.formatCurrency(change.previousAmount, saved.currency, maskIfHidden = false),
                    CurrencyFormatter.formatCurrency(saved.amount, saved.currency, maskIfHidden = false)
                )
            }
            val posted = showTransactionNotification(
                context, saved, repository, entryPoint.categoryRepository(),
                priceLine, trackCycle, offerPriceUpdate = result.priceChange != null
            )
            if (trackCycle != null && !posted) {
                runCatching { entryPoint.userPreferencesRepository().releaseRecurringOffer(offerKey, today) }
            }
            // #734: tell the user right away when the bank's balance disagrees with the ledger.
            runCatching { entryPoint.detectBalanceDiscrepancy().execute(saved) }
                .getOrNull()
                ?.let { showBalanceDiscrepancyNotification(context, saved, it) }
                ?: cancelBalanceDiscrepancyNotification(context, saved)
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
        priceLine: String? = null,
        trackCycle: String? = null,
        offerPriceUpdate: Boolean = false
    ): Boolean {
        try {
            val notificationManager = ensureChannel(context)
            val transactionId = transaction.id
            val pendingIntent = openTransactionIntent(context, transactionId)

            val amount = CurrencyFormatter.formatCurrency(transaction.amount, transaction.currency, maskIfHidden = false)
            val merchant = transaction.merchantName.ifBlank { context.getString(R.string.notif_txn_unknown_merchant) }
            val bankName = transaction.bankName ?: context.getString(R.string.notif_txn_unknown_bank)
            val title = when (transaction.transactionType) {
                TransactionType.INCOME -> context.getString(R.string.notif_txn_title_received, amount, merchant)
                TransactionType.EXPENSE, TransactionType.CREDIT ->
                    context.getString(R.string.notif_txn_title_spent, amount, merchant)
                else -> context.getString(R.string.notif_txn_title_other, amount, merchant)
            }
            // From the table: a process started for this SMS may not have mirrored CategoryMapping.userStyles yet.
            val categories = categoryRepository.getAllCategories().first()
            // Unsorted: ask, with one-tap answers. Sorted: the category sits in the header.
            val uncategorized = transaction.category == BuiltinCategoryNames.current("Others")
            val summaryLine = if (uncategorized) context.getString(R.string.notif_txn_ask_category, bankName) else bankName
            val content = listOfNotNull(summaryLine, priceLine).joinToString("\n")

            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(content)
                .setSubText(if (uncategorized) null else CategoryMapping.label(context, transaction.category, categories))
                // A price change adds a second line.
                .apply { if (priceLine != null) setStyle(NotificationCompat.BigTextStyle().bigText(content)) }
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setGroup(GROUP_KEY)
                // Swiped away (or cleared): recount the stack's header.
                .setDeleteIntent(
                    PendingIntent.getBroadcast(
                        context,
                        transactionId.toInt(),
                        Intent(context, NotificationActionReceiver::class.java).apply {
                            action = NotificationActionReceiver.ACTION_CONFIRM_TRANSACTION
                            putExtra(NotificationActionReceiver.EXTRA_TRANSACTION_ID, transactionId)
                            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, transactionId.toInt())
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )

            // Notification action slots are precious (Android collapses past 3),
            // so the last opens the full picker; see [alertLayout] for the rest. (#303)
            val notificationId = transactionId.toInt()
            // A repeat charge's "Track as … bill", or a repriced bill's "Update price",
            // goes first. They never both apply: a price change needs a subscription,
            // and Track needs there to be none.
            val billAction = offerPriceUpdate || trackCycle != null
            if (billAction) {
                notificationBuilder.addExtras(android.os.Bundle().apply { putBoolean(EXTRA_BILL_ACTION, true) })
            }
            if (offerPriceUpdate) {
                notificationBuilder.addAction(
                    0,
                    context.getString(R.string.recurring_alert_update_price),
                    NotificationActionReceiver.updatePriceIntent(
                        context,
                        categoryRequestCode(transactionId, TRACK_SLOT),
                        transactionId,
                        notificationId
                    )
                )
            } else if (trackCycle != null) {
                notificationBuilder.addAction(
                    0,
                    context.getString(trackActionLabel(trackCycle)),
                    NotificationActionReceiver.trackIntent(
                        context,
                        categoryRequestCode(transactionId, TRACK_SLOT),
                        transactionId,
                        notificationId,
                        trackCycle
                    )
                )
            }
            val (pickCount, canExclude) =
                alertLayout(transaction.transactionType, uncategorized, transaction.excludedFromAnalytics, billAction)
            if (pickCount > 0) {
                quickPicksFor(transaction, repository, categoryRepository).take(pickCount).forEachIndexed { index, pick ->
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
                    notificationBuilder.addAction(0, CategoryMapping.label(context, pick, categories), categoryPendingIntent)
                }
            }

            if (canExclude) {
                notificationBuilder.addAction(
                    0,
                    context.getString(
                        if (transaction.transactionType == TransactionType.INCOME) R.string.notif_txn_action_not_income
                        else R.string.notif_txn_action_not_expense
                    ),
                    NotificationActionReceiver.excludeIntent(
                        context,
                        categoryRequestCode(transactionId, EXCLUDE_SLOT),
                        transactionId,
                        notificationId,
                        undo = false
                    )
                )
            }

            // The translucent picker: any category, not just the quick picks.
            notificationBuilder.addAction(
                0,
                context.getString(if (uncategorized) R.string.notif_txn_action_more else R.string.notif_txn_action_change),
                pickerIntent(context, transactionId, notificationId)
            )

            val notification = notificationBuilder.build()
            notificationManager.notify(notificationId, notification)
            updateAlertGroup(context, posted = notificationId to notification)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification", e)
            return false
        }
    }

    /** Whether a transaction alert would be seen: the app's notifications and its alert channel are on. */
    private fun canShowTransactionAlerts(context: Context): Boolean {
        if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = ensureChannel(context).getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }
}
