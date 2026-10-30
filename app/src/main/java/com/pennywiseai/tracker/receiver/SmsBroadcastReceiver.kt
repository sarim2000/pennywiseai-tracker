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
        fun learnedSendersStore(): LearnedSendersStore
        fun ignoredAccountsStore(): IgnoredAccountsStore
        fun unrecognizedSmsRepository(): UnrecognizedSmsRepository
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

        // Suggestion alerts share the transaction channel; the tag keeps their ids apart from transaction ids.
        private const val SUGGESTION_TAG = "sms_suggestion"

        // Its own action keeps the alert's PendingIntent from matching (and overwriting) another MainActivity one.
        private const val ACTION_ADD_SUGGESTION = "com.pennywiseai.tracker.ACTION_ADD_SUGGESTION"

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
            notificationManager.notify(
                notificationId,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(context.getString(R.string.notif_txn_learned_title, CategoryMapping.label(context, learned.category)))
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
            val id = entryPoint.unrecognizedSmsRepository().insert(sms).takeIf { it > 0 } ?: return
            if (isLearned) showSuggestionNotification(context, Suggestion(sms.copy(id = id), transaction, learnedAccount))
        }

        private fun showSuggestionNotification(context: Context, suggestion: Suggestion) {
            val draft = suggestion.draft()
            val tx = suggestion.transaction
            val amount = CurrencyFormatter.formatCurrency(tx.amount, tx.currency)
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
                    .setContentText(context.getString(R.string.notif_suggestion_text, suggestion.sms.sender))
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
            // From the table: a process started for this SMS may not have mirrored CategoryMapping.userStyles yet.
            val categories = categoryRepository.getAllCategories().first()
            // Unsorted: ask, with one-tap answers. Sorted: just say where it went.
            val uncategorized = transaction.category == BuiltinCategoryNames.current("Others")
            val summaryLine = if (uncategorized) {
                context.getString(R.string.notif_txn_ask_category, bankName)
            } else {
                context.getString(R.string.notif_txn_category_bank, CategoryMapping.label(context, transaction.category, categories), bankName)
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
                    notificationBuilder.addAction(0, CategoryMapping.label(context, pick, categories), categoryPendingIntent)
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

            val notification = notificationBuilder.build()
            notificationManager.notify(notificationId, notification)
            updateAlertGroup(context, posted = notificationId to notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification", e)
        }
    }
}
