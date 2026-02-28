package com.pennywiseai.tracker.worker

import android.content.Context
import android.os.Process
import android.os.Trace
import android.provider.Telephony
import android.util.Log
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.bank.*
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CardType
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import com.pennywiseai.tracker.data.manager.UPIDeduplicator
import com.pennywiseai.tracker.data.mapper.toEntity
import com.pennywiseai.tracker.data.mapper.toEntityType
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.*
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/**
 * Optimized SMS Worker — parallel parse → sequential save pipeline.
 *
 * Architecture:
 * ┌────────────────────────────────────────────────────────────────────┐
 * │  readSmsMessages()  ──►  [feed]                                   │
 * │                               │                                   │
 * │              ┌────────────────┘  (N coroutines, Dispatchers.Default)│
 * │              ▼                                                    │
 * │        [parse in parallel]  ──►  [results]                        │
 * │                                        │                          │
 * │              ┌─────────────────────────┘  (1 coroutine, Dispatchers.IO)│
 * │              ▼                                                    │
 * │        [sequential DB save + balance update]                      │
 * └────────────────────────────────────────────────────────────────────┘
 */
@HiltWorker
class OptimizedSmsReaderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionRepository: TransactionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val cardRepository: CardRepository,
    private val llmRepository: LlmRepository,
    private val merchantMappingRepository: MerchantMappingRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val unrecognizedSmsRepository: UnrecognizedSmsRepository,
    private val ruleRepository: RuleRepository,
    private val ruleEngine: RuleEngine,
    private val upiDeduplicator: UPIDeduplicator
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG                               = "OptimizedSmsReaderWorker"
        const val WORK_NAME                         = "optimized_sms_reader_work"
        const val INPUT_FORCE_RESYNC                = "input_force_resync"
        const val PROGRESS_TOTAL                    = "progress_total"
        const val PROGRESS_PROCESSED                = "progress_processed"
        const val PROGRESS_PARSED                   = "progress_parsed"
        const val PROGRESS_SAVED                    = "progress_saved"
        const val PROGRESS_TIME_ELAPSED             = "progress_time_elapsed"
        const val PROGRESS_ESTIMATED_TIME_REMAINING = "progress_estimated_time_remaining"
        const val PROGRESS_CURRENT_BATCH            = "progress_current_batch"
        const val PROGRESS_TOTAL_BATCHES            = "progress_total_batches"
        const val PROGRESS_MSG_PER_SEC              = "progress_msg_per_sec"
        const val PROGRESS_ETA_SECONDS              = "progress_eta_seconds"

        private const val NOTIFICATION_ID           = 9001
        private const val PARSE_CHANNEL_CAPACITY    = 512
        private const val RESULT_CHANNEL_CAPACITY   = 512
        private const val PROGRESS_REPORT_INTERVAL  = 10
        private const val PROGRESS_MONITOR_INTERVAL = 50L
        private const val UNRECOGNIZED_BATCH_SIZE   = 50
        private const val ETA_WINDOW_MS             = 2000L

        private val SMS_PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.BODY,
            Telephony.Sms.TYPE
        )

        fun buildProgressNotification(context: Context, processed: Int, total: Int): android.app.Notification {
            val channelId = "sms_scan_channel"
            // SDK_INT is always >= 26 for this project; NotificationChannel is always available
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    android.app.NotificationChannel(channelId, "SMS Scan", android.app.NotificationManager.IMPORTANCE_LOW)
                )
            }
            return androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("Scanning transactions…")
                .setContentText(if (total > 0) "Processed $processed / $total" else "Reading SMS…")
                .setProgress(total, processed, total == 0)
                .setOngoing(true)
                .setSilent(true)
                .build()
        }
    }

    // ─── Run-scoped values (computed once in doWork, reused across all coroutines) ──

    /** Cached at class level — eliminates a system call on every SMS timestamp conversion. */
    private val systemZone: ZoneId = ZoneId.systemDefault()

    /** Set once at the start of doWork to avoid calling LocalDateTime.now() per SMS. */
    private lateinit var thirtyDaysAgo: LocalDateTime

    /** O(1) sender-to-parser lookup. Factory is only called once per unique sender string. */
    private val parserCache = HashMap<String, BankParser?>(256)
    private fun cachedParser(sender: String): BankParser? =
        parserCache.getOrPut(sender) { BankParserFactory.getParser(sender) }

    /**
     * Merchant-name to custom category, preloaded once at scan start.
     * Previously: 1 DB read per transaction. Now: 1 DB read total, O(1) lookup per transaction.
     */
    private var merchantMappingCache: Map<String, String> = emptyMap()

    /**
     * Rules preloaded by transaction type at scan start.
     * Previously: getActiveRulesByType DB call per transaction.
     * Now: one call per TransactionType (4 total), zero DB calls per transaction.
     */
    private var ruleCache: Map<TransactionType, List<*>> = emptyMap()

    // ─── Extension: DRYs up repeated timestamp conversions ───────────────────

    private fun Long.toLocalDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(this), systemZone)

    // ─── Data classes ─────────────────────────────────────────────────────────

    private data class SmsMessage(
        val id: Long,
        val sender: String,
        val timestamp: Long,
        val body: String,
        val type: Int
    )

    /**
     * Sealed result from the parse stage — each outcome is a distinct type,
     * impossible states are unrepresentable.
     */
    private sealed class ParseResult {
        abstract val sms: SmsMessage

        /** Promo/gov/unknown sender — discard silently. */
        data class Discard(override val sms: SmsMessage) : ParseResult()

        /** Unknown -T/-S sender — save to unrecognized table for review. */
        data class StoreUnrecognized(override val sms: SmsMessage) : ParseResult()

        /**
         * Subscription mandate or balance update.
         * [onSave] captures the exact typed repository call at parse time;
         * invoked in the sequential save coroutine.
         */
        class SpecialNotification(
            override val sms: SmsMessage,
            val onSave: suspend () -> Unit
        ) : ParseResult()

        /** Normal debit/credit — ready to be written to the transactions table. */
        data class Transaction(
            override val sms: SmsMessage,
            val parsed: ParsedTransaction
        ) : ParseResult()
    }

    /**
     * Thread-safe processing stats with a sliding-window rate estimator.
     *
     * Tracks timestamps of the last [ringSize] completions in a ring buffer and computes
     * rate over only the most recent [ETA_WINDOW_MS] ms, giving accurate real-time msg/sec
     * even as the pipeline warms up or the save coroutine has bursts.
     */
    /**
     * Thread-safe processing stats with a self-correcting sliding-window rate estimator.
     *
     * Root cause of wrong ETA: a 200-slot ring at 500 msg/s wraps in 0.4 s, so
     * msgPerSec() was measuring only ~0.4 s instead of the intended 2 s window —
     * producing a 5× inflated rate and a 5× underestimated ETA.
     *
     * Fix:
     *  1. Ring size 8192 (power-of-2 for cheap bitmask modulo) — handles up to
     *     ~2730 msg/s within ETA_WINDOW_MS = 3 s without wrapping.
     *  2. Bounds check: only count timestamps that are BOTH > cutoff AND <= now,
     *     so stale zeros from the uninitialized ring never inflate the count.
     *  3. Warmup fallback: if the window has fewer than MIN_SAMPLES entries, fall
     *     back to the overall elapsed average — ETA is always a real number from t=0.
     *  4. ETA clamped to 0 — can't go negative when processed > total in a race.
     *
     * Self-correction: msgPerSec() recomputes from the live ring on every call, so a
     * sudden slowdown (e.g. save coroutine hits a slow DB write) is reflected within
     * one ETA_WINDOW_MS interval automatically.
     */
    private class ProcessingStats(val total: Int) {
        val processed = AtomicInteger(0)
        val parsed    = AtomicInteger(0)
        val saved     = AtomicInteger(0)
        val startTime = System.currentTimeMillis()

        // Must be a power of 2 (for bitmask) and large enough that the ring never
        // wraps within ETA_WINDOW_MS at peak throughput. 8192 / 3 ≈ 2730 msg/s max.
        private val RING_SIZE          = 8192
        private val MIN_SAMPLES        = 10
        private val ring               = LongArray(RING_SIZE)   // zeros = epoch 1970, never in window
        private val head               = AtomicInteger(0)

        fun recordCompletion() {
            // Bitmask modulo: (RING_SIZE - 1) works because RING_SIZE is power of 2
            val pos = head.getAndIncrement() and (RING_SIZE - 1)
            ring[pos] = System.currentTimeMillis()
        }

        fun elapsedMs() = System.currentTimeMillis() - startTime

        /**
         * Instantaneous msg/s over the last [ETA_WINDOW_MS].
         * Falls back to overall average during warmup so ETA is always meaningful.
         */
        fun msgPerSec(): Float {
            val now      = System.currentTimeMillis()
            val cutoff   = now - ETA_WINDOW_MS
            var count    = 0
            val snapshot = ring.copyOf()   // snapshot avoids reading a mix of old/new values mid-write
            for (ts in snapshot) {
                if (ts in (cutoff + 1)..now) count++   // bounds-check: exclude zeros AND future timestamps
            }
            return when {
                count >= MIN_SAMPLES ->
                    count / (ETA_WINDOW_MS / 1000f)     // accurate window rate

                else -> {
                    // Warmup or very slow: fall back to overall elapsed average
                    val elapsedSec = elapsedMs() / 1000f
                    val done       = processed.get()
                    if (elapsedSec > 0.1f && done > 0) done / elapsedSec else 0f
                }
            }
        }

        fun etaSec(): Int {
            val mps       = msgPerSec()
            val remaining = total - processed.get()
            return if (mps > 0f && remaining > 0) maxOf(0, (remaining / mps).toInt()) else 0
        }
    }

    // ─── Expedited work ───────────────────────────────────────────────────────

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(NOTIFICATION_ID, buildProgressNotification(applicationContext, 0, 0))

    // ─── Entry point ──────────────────────────────────────────────────────────

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
        thirtyDaysAgo = LocalDateTime.now(systemZone).minusDays(30)

        Trace.beginSection("SmsWorker.doWork")
        try {
            val forceResync = inputData.getBoolean(INPUT_FORCE_RESYNC, false)
            Log.i(TAG, "Starting SMS worker (forceResync=$forceResync)")

            if (forceResync) {
                // try/finally ensures endSection even if the suspend calls throw
                Trace.beginSection("clearDatabase")
                try {
                    transactionRepository.deleteAllTransactions()
                    accountBalanceRepository.deleteAllBalances()
                } finally {
                    Trace.endSection()
                }
                Log.i(TAG, "Force resync: database cleared")
            }

            Trace.beginSection("readSmsMessages")
            val messages = try {
                readSmsMessages(forceResync)
            } finally {
                Trace.endSection()
            }

            Trace.beginSection("preloadCaches")
            try {
                merchantMappingCache = merchantMappingRepository.getAllMappingsAsMap()
                ruleCache = TransactionType.entries.associateWith { type ->
                    ruleRepository.getActiveRulesByType(type)
                }
            } finally {
                Trace.endSection()
            }
            Log.i(TAG, "Caches: ${merchantMappingCache.size} merchant mappings, ${ruleCache.values.sumOf { it.size }} rules")

            val parserConcurrency = maxOf(1, Runtime.getRuntime().availableProcessors() - 1)
            Log.i(TAG, "Pipeline: $parserConcurrency parsers | 1 saver | ${messages.size} messages")

            val stats = ProcessingStats(total = messages.size)
            reportProgress(stats)

            val totalTime = measureTimeMillis { runPipeline(messages, stats, parserConcurrency) }

            Log.i(TAG, buildSummary(stats, totalTime))
            cleanUpAndFinalize(stats)
            reportProgress(stats)
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in SMS worker", e)
            Result.failure()
        } finally {
            Trace.endSection()
        }
    }

    // ─── Pipeline ─────────────────────────────────────────────────────────────

    private suspend fun runPipeline(
        messages: List<SmsMessage>,
        stats: ProcessingStats,
        parserConcurrency: Int
    ) = coroutineScope {
        // Explicit local vals — captured by the launch lambdas below
        val feed    = Channel<SmsMessage>(PARSE_CHANNEL_CAPACITY)
        val results = Channel<ParseResult>(RESULT_CHANNEL_CAPACITY)

        // Stage 1 – Feed
        launch(Dispatchers.IO) {
            try { messages.forEach { sms -> feed.send(sms) } }
            finally { feed.close() }
        }

        // Stage 2 – Parse (all available CPU cores)
        val parserJobs = (1..parserConcurrency).map { id ->
            launch(Dispatchers.Default) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
                Trace.beginSection("parse.$id")
                try {
                    for (sms in feed) results.send(parseSms(sms))
                } finally {
                    Trace.endSection()
                }
            }
        }

        // Close results once all parsers finish
        launch(Dispatchers.Default) {
            parserJobs.joinAll()   // joinAll() preferred over forEach { it.join() }
            results.close()
        }

        // Stage 3 – Save (single sequential coroutine — no balance race conditions)
        val saver = launch(Dispatchers.IO) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
            val unrecognizedBatch = ArrayList<SmsMessage>(UNRECOGNIZED_BATCH_SIZE)
            var widgetNeedsUpdate = false

            Trace.beginSection("save")
            try {
                for (result in results) {
                    val p = stats.processed.incrementAndGet()
                    stats.recordCompletion()

                    when (result) {
                        is ParseResult.Discard            -> Unit
                        is ParseResult.StoreUnrecognized  -> {
                            unrecognizedBatch.add(result.sms)
                            if (unrecognizedBatch.size >= UNRECOGNIZED_BATCH_SIZE)
                                flushUnrecognizedBatch(unrecognizedBatch)
                        }
                        is ParseResult.SpecialNotification -> {
                            try { result.onSave() }
                            catch (e: Exception) { Log.e(TAG, "Error saving special notification: ${e.message}") }
                        }
                        is ParseResult.Transaction -> {
                            stats.parsed.incrementAndGet()
                            Trace.beginSection("saveTransaction")
                            try {
                                if (saveTransaction(result.parsed, result.sms)) {
                                    stats.saved.incrementAndGet()
                                    widgetNeedsUpdate = true
                                }
                            } finally {
                                Trace.endSection()
                            }
                        }
                    }
                    if (p % PROGRESS_REPORT_INTERVAL == 0 || p == stats.total) reportProgress(stats)
                }
            } finally {
                Trace.endSection()
            }

            if (unrecognizedBatch.isNotEmpty()) flushUnrecognizedBatch(unrecognizedBatch)
            if (widgetNeedsUpdate)
                com.pennywiseai.tracker.widget.RecentTransactionsWidgetUpdateWorker.enqueueOneShot(applicationContext)
        }

        // Real-time UI refresh every 50ms
        val progressMonitor = launch(Dispatchers.IO) {
            while (stats.processed.get() < stats.total) {
                delay(PROGRESS_MONITOR_INTERVAL)
                reportProgress(stats)
            }
        }

        saver.join()
        progressMonitor.cancel()

        // Final deduplication scan - catches duplicates that slipped through due to parallel processing
        runFinalDeduplicationScan()
    }

    // ─── Final deduplication scan ───────────────────────────────────────────────────
    
    private suspend fun runFinalDeduplicationScan() {
        try {
            Log.d(TAG, "Running final deduplication scan...")
            val duplicatesRemoved = upiDeduplicator.scanAndRemoveDuplicates()
            if (duplicatesRemoved > 0) {
                Log.d(TAG, "Removed $duplicatesRemoved duplicate transactions during final scan")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during final deduplication scan: ${e.message}")
        }
    }

    // ─── Stage 2: Parse (plain fun — no suspend overhead on the hot path) ─────

    private fun parseSms(sms: SmsMessage): ParseResult {
        val upper = sms.sender.uppercase()

        if (upper.endsWith("-P") || upper.endsWith("-G"))
            return ParseResult.Discard(sms)

        val parser = cachedParser(sms.sender)
            ?: return if (upper.endsWith("-T") || upper.endsWith("-S"))
                ParseResult.StoreUnrecognized(sms)
            else
                ParseResult.Discard(sms)

        val isRecent = sms.timestamp.toLocalDateTime().isAfter(thirtyDaysAgo)

        checkSubscriptionOrBalance(parser, sms, isRecent)?.let { return it }

        val parsed = parser.parse(sms.body, sms.sender, sms.timestamp)
            ?: return ParseResult.Discard(sms)

        return ParseResult.Transaction(sms, parsed)
    }

    // ─── Subscription / balance detection (pure parse, zero DB access) ────────

    /**
     * Returns a SpecialNotification whose [onSave] lambda will be invoked in the sequential
     * save coroutine. Everything is captured by value at parse time so types are preserved.
     * Returns null → normal transaction parse should proceed.
     */
    private fun checkSubscriptionOrBalance(
        parser: BankParser,
        sms: SmsMessage,
        isRecent: Boolean
    ): ParseResult.SpecialNotification? = when (parser) {

        is SBIBankParser -> {
            if (!parser.isUPIMandateNotification(sms.body) || !isRecent) null
            else parser.parseUPIMandateSubscription(sms.body)?.let { info ->
                ParseResult.SpecialNotification(sms) {
                    subscriptionRepository.createOrUpdateFromSBIMandate(info, parser.getBankName(), sms.body)
                }
            }
        }

        is FederalBankParser -> when {
            parser.isMandateCreationNotification(sms.body) ->
                parser.parseEMandateSubscription(sms.body)?.let { info ->
                    ParseResult.SpecialNotification(sms) {
                        subscriptionRepository.createOrUpdateFromFederalBankMandate(info, parser.getBankName(), sms.body)
                    }
                }
            else ->
                parser.parseFutureDebit(sms.body)?.let { info ->
                    ParseResult.SpecialNotification(sms) {
                        subscriptionRepository.createOrUpdateFromFederalBankMandate(info, parser.getBankName(), sms.body)
                    }
                }
        }

        is HDFCBankParser -> {
            if (parser.isBalanceUpdateNotification(sms.body)) {
                parser.parseBalanceUpdate(sms.body)?.let { info ->
                    return ParseResult.SpecialNotification(sms) {
                        accountBalanceRepository.insertBalanceUpdate(
                            bankName     = info.bankName,
                            accountLast4 = info.accountLast4 ?: "XXXX",
                            balance      = info.balance,
                            timestamp    = info.asOfDate ?: sms.timestamp.toLocalDateTime(),
                            currency     = parser.getCurrency()
                        )
                    }
                }
            }
            val actions = mutableListOf<suspend () -> Unit>()
            if (parser.isEMandateNotification(sms.body) && isRecent)
                parser.parseEMandateSubscription(sms.body)?.let { info ->
                    actions += { subscriptionRepository.createOrUpdateFromEMandate(info, parser.getBankName(), sms.body) }
                }
            if (parser.isFutureDebitNotification(sms.body) && isRecent)
                parser.parseFutureDebit(sms.body)?.let { info ->
                    actions += { subscriptionRepository.createOrUpdateFromEMandate(info, parser.getBankName(), sms.body) }
                }
            if (actions.isNotEmpty()) ParseResult.SpecialNotification(sms) {
                actions.forEach { it() }
            } else null
        }

        is IndianBankParser -> {
            if (!parser.isMandateNotification(sms.body) || !isRecent) null
            else parser.parseMandateSubscription(sms.body)?.let { info ->
                ParseResult.SpecialNotification(sms) {
                    subscriptionRepository.createOrUpdateFromIndianBankMandate(info, parser.getBankName(), sms.body)
                }
            }
        }

        is IndusIndBankParser -> {
            if (!parser.isBalanceUpdateNotification(sms.body)) null
            else parser.parseBalanceUpdate(sms.body)?.let { info ->
                ParseResult.SpecialNotification(sms) {
                    accountBalanceRepository.insertBalanceUpdate(
                        bankName     = info.bankName,
                        accountLast4 = info.accountLast4 ?: "XXXX",
                        balance      = info.balance,
                        timestamp    = info.asOfDate ?: sms.timestamp.toLocalDateTime(),
                        currency     = parser.getCurrency()
                    )
                }
            }
        }

        else -> null
    }

    // ─── Stage 3b: Regular transactions ──────────────────────────────────────

    private suspend fun saveTransaction(parsed: ParsedTransaction, sms: SmsMessage): Boolean {
        return try {
            val entity = parsed.toEntity()

            // Check for duplicates using UPIDeduplicator (hash, reference, account+amount+time)
            val dedupResult = upiDeduplicator.checkForDuplicate(parsed)
            if (dedupResult is UPIDeduplicator.DeduplicationResult.Duplicate) {
                return false
            }

            val customCategory = merchantMappingCache[entity.merchantName]
            val mapped = if (customCategory != null) entity.copy(category = customCategory) else entity

            @Suppress("UNCHECKED_CAST")
            val activeRules = (ruleCache[mapped.transactionType] ?: emptyList<Nothing>()) as List<Nothing>
            if (ruleEngine.shouldBlockTransaction(mapped, sms.body, activeRules) != null) return false

            val (withRules, ruleApps) = ruleEngine.evaluateRules(mapped, sms.body, activeRules)

            val matchedSub = subscriptionRepository.matchTransactionToSubscription(
                withRules.merchantName, withRules.amount
            )
            val finalEntity = if (matchedSub != null) {
                subscriptionRepository.updateNextPaymentDateAfterCharge(
                    matchedSub.id, withRules.dateTime.toLocalDate()
                )
                withRules.copy(isRecurring = true)
            } else withRules

            val rowId = transactionRepository.insertTransaction(finalEntity)
            if (rowId == -1L) return false

            if (ruleApps.isNotEmpty()) ruleRepository.saveRuleApplications(ruleApps)
            processBalanceUpdate(parsed, finalEntity, rowId)
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error saving transaction: ${e.message}")
            false
        }
    }

    // ─── Balance update ───────────────────────────────────────────────────────

    private suspend fun processBalanceUpdate(
        parsed: ParsedTransaction,
        entity: com.pennywiseai.tracker.data.database.entity.TransactionEntity,
        rowId: Long
    ) {
        val accountLast4 = parsed.accountLast4 ?: return

        val card = if (parsed.isFromCard) {
            (cardRepository.getCard(parsed.bankName, accountLast4)
                ?: run {
                    cardRepository.findOrCreateCard(
                        accountLast4, parsed.bankName,
                        isCredit = parsed.type.toEntityType() == TransactionType.CREDIT
                    )
                    cardRepository.getCard(parsed.bankName, accountLast4)
                }
                    )?.also { c ->
                    cardRepository.updateCardBalance(
                        cardId  = c.id,
                        balance = parsed.balance,
                        source  = parsed.smsBody.take(200),
                        date    = parsed.timestamp.toLocalDateTime()
                    )
                }
        } else null

        val targetAccount = when {
            card == null                                                  -> accountLast4
            card.cardType == CardType.CREDIT                             -> accountLast4
            card.cardType == CardType.DEBIT && card.accountLast4 != null -> card.accountLast4
            else                                                         -> return
        }

        val isCreditCard = card?.cardType == CardType.CREDIT ||
                parsed.type.toEntityType() == TransactionType.CREDIT

        val existing = accountBalanceRepository.getLatestBalance(parsed.bankName, targetAccount)

        val newBalance = when {
            isCreditCard ->
                (existing?.balance ?: BigDecimal.ZERO) + parsed.amount

            existing?.isCreditCard == true && parsed.type.toEntityType() == TransactionType.INCOME ->
                // existing is smart-cast non-null here; balance is non-nullable BigDecimal
                (existing.balance - parsed.amount).max(BigDecimal.ZERO)

            parsed.balance != null ->
                parsed.balance!!

            else -> {
                val cur = existing?.balance ?: BigDecimal.ZERO
                when (parsed.type.toEntityType()) {
                    TransactionType.INCOME                              -> cur + parsed.amount
                    TransactionType.EXPENSE, TransactionType.INVESTMENT -> (cur - parsed.amount).max(BigDecimal.ZERO)
                    else                                                -> cur
                }
            }
        }

        accountBalanceRepository.insertBalance(
            AccountBalanceEntity(
                bankName      = parsed.bankName,
                accountLast4  = targetAccount,
                balance       = newBalance,
                timestamp     = entity.dateTime,
                transactionId = rowId,
                creditLimit   = existing?.creditLimit,
                isCreditCard  = isCreditCard || (existing?.isCreditCard ?: false),
                smsSource     = parsed.smsBody.take(500),
                sourceType    = "TRANSACTION",
                currency      = parsed.currency
            )
        )
    }

    // ─── Unrecognized SMS batch ────────────────────────────────────────────────

    private suspend fun flushUnrecognizedBatch(batch: ArrayList<SmsMessage>) {
        for (sms in batch) {
            try {
                if (!unrecognizedSmsRepository.exists(sms.sender, sms.body)) {
                    unrecognizedSmsRepository.insert(
                        UnrecognizedSmsEntity(
                            sender     = sms.sender,
                            smsBody    = sms.body,
                            receivedAt = sms.timestamp.toLocalDateTime()
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error storing unrecognized SMS: ${e.message}")
            }
        }
        batch.clear()
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    private suspend fun cleanUpAndFinalize(stats: ProcessingStats) {
        try { unrecognizedSmsRepository.cleanupOldEntries() }
        catch (e: Exception) { Log.e(TAG, "Cleanup error: ${e.message}") }
        if (stats.saved.get() > 0) {
            try { llmRepository.updateSystemPrompt() }
            catch (e: Exception) { Log.e(TAG, "Prompt update error: ${e.message}") }
        }
    }

    // ─── Progress ─────────────────────────────────────────────────────────────

    private suspend fun reportProgress(stats: ProcessingStats) {
        try {
            val p   = stats.processed.get()
            val mps = stats.msgPerSec()
            val eta = stats.etaSec()
            setProgress(workDataOf(
                PROGRESS_TOTAL                    to stats.total,
                PROGRESS_PROCESSED                to p,
                PROGRESS_PARSED                   to stats.parsed.get(),
                PROGRESS_SAVED                    to stats.saved.get(),
                PROGRESS_TIME_ELAPSED             to stats.elapsedMs(),
                PROGRESS_ESTIMATED_TIME_REMAINING to (eta * 1000L),
                PROGRESS_ETA_SECONDS              to eta,
                PROGRESS_MSG_PER_SEC              to mps,
                PROGRESS_CURRENT_BATCH            to p,
                PROGRESS_TOTAL_BATCHES            to stats.total
            ))
        } catch (_: Exception) {}
    }

    // ─── SMS / RCS reading ────────────────────────────────────────────────────

    private suspend fun readSmsMessages(forceResync: Boolean = false): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        try {
            val lastScanTimestamp = userPreferencesRepository.getLastScanTimestamp().first() ?: 0L
            val scanMonths        = userPreferencesRepository.getSmsScanMonths()
            val scanAllTime       = userPreferencesRepository.getSmsScanAllTime()
            val lastScanPeriod    = userPreferencesRepository.getLastScanPeriod().first() ?: 0
            val now               = System.currentTimeMillis()

            val needsFullScan = forceResync || lastScanTimestamp == 0L || scanAllTime || scanMonths > lastScanPeriod

            val scanStartTime = if (needsFullScan) {
                java.util.Calendar.getInstance().apply {
                    if (scanAllTime) add(java.util.Calendar.YEAR, -10)
                    else             add(java.util.Calendar.MONTH, -scanMonths)
                    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0);      set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
            } else {
                val threeDaysAgo = now - 3 * 24 * 60 * 60 * 1000L
                val periodLimit  = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.MONTH, -scanMonths)
                }.timeInMillis
                maxOf(minOf(lastScanTimestamp, threeDaysAgo), periodLimit)
            }

            applicationContext.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                SMS_PROJECTION,
                "${Telephony.Sms.TYPE} = ? AND ${Telephony.Sms.DATE} >= ?",
                arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString(), scanStartTime.toString()),
                "${Telephony.Sms.DATE} ASC"
            )?.use { c ->
                val idIdx      = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val dateIdx    = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val bodyIdx    = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val typeIdx    = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                while (c.moveToNext()) {
                    messages.add(SmsMessage(
                        id        = c.getLong(idIdx),
                        sender    = c.getString(addressIdx) ?: "",
                        timestamp = c.getLong(dateIdx),
                        body      = c.getString(bodyIdx) ?: "",
                        type      = c.getInt(typeIdx)
                    ))
                }
            }

            userPreferencesRepository.setLastScanTimestamp(now)
            if (needsFullScan) userPreferencesRepository.setLastScanPeriod(scanMonths)

            try { messages.addAll(readRcsMessages(scanStartTime / 1000)) }
            catch (e: Exception) { Log.e(TAG, "RCS read error: ${e.message}") }

        } catch (e: Exception) {
            Log.e(TAG, "Error reading SMS", e)
        }
        Log.i(TAG, "Loaded ${messages.size} messages (SMS + RCS)")
        return messages
    }

    private fun readRcsMessages(scanStartSeconds: Long): List<SmsMessage> {
        val result = mutableListOf<SmsMessage>()
        applicationContext.contentResolver.query(
            "content://mms".toUri(),
            arrayOf("_id", "thread_id", "date", "tr_id", "m_id"),
            "date >= ?",
            arrayOf(scanStartSeconds.toString()),
            "date DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val messageId = c.getLong(c.getColumnIndexOrThrow("_id"))
                val date      = c.getLong(c.getColumnIndexOrThrow("date"))
                val trId      = c.getColumnIndex("tr_id").takeIf { it >= 0 }
                    ?.let { c.getString(it) } ?: continue
                if (!trId.startsWith("proto:")) continue

                val sender = extractRcsSender(trId) ?: continue
                if (!sender.uppercase().contains("PUNJAB NATIONAL BANK")) continue

                var text = getRcsMessageText(messageId) ?: continue
                if (text.trim().startsWith("{")) text = extractTextFromRcsJson(text) ?: continue

                result.add(SmsMessage(messageId, sender, date * 1000, text, Telephony.Sms.MESSAGE_TYPE_INBOX))
            }
        }
        return result
    }

    // ─── RCS helpers ──────────────────────────────────────────────────────────

    private fun extractRcsSender(trId: String): String? = try {
        val decoded = String(android.util.Base64.decode(trId.removePrefix("proto:"), android.util.Base64.DEFAULT))
        Regex("""([a-z_]+)_[a-z0-9]+_agent@rbm\.goog""").find(decoded)?.let { m ->
            return m.groupValues[1].split("_")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
        Regex("""[\x12\x1a][\x00-\x20]([A-Za-z][A-Za-z\s]+)""").find(decoded)?.let { m ->
            val name = m.groupValues[1].trim()
            if (name.length in 4..49) return name
        }
        null
    } catch (_: Exception) { null }

    private fun getRcsMessageText(messageId: Long): String? = try {
        applicationContext.contentResolver.query(
            "content://mms/part".toUri(), null, "mid = ?", arrayOf(messageId.toString()), null
        )?.use { c ->
            while (c.moveToNext()) {
                val ct = c.getColumnIndex("ct").takeIf { it >= 0 }?.let { c.getString(it) } ?: continue
                if (!ct.startsWith("text/") && ct != "application/smil") continue
                c.getColumnIndex("text").takeIf { it >= 0 }?.let { idx ->
                    c.getString(idx)?.takeIf { it.isNotEmpty() }?.let { return it }
                }
                val partId = c.getLong(c.getColumnIndexOrThrow("_id"))
                try {
                    applicationContext.contentResolver
                        .openInputStream("content://mms/part/$partId".toUri())
                        ?.bufferedReader()?.use { it.readText() }
                        ?.takeIf { it.isNotEmpty() }?.let { return it }
                } catch (_: Exception) {}
            }
            null
        }
    } catch (_: Exception) { null }

    private fun extractTextFromRcsJson(json: String): String? = try {
        val obj = org.json.JSONObject(json)
        obj.optString("text").takeIf { it.isNotEmpty() }
            ?: obj.optJSONObject("message")?.optString("text")?.takeIf { it.isNotEmpty() }
            ?: run {
                val texts    = mutableListOf<String>()
                val skipKeys = setOf("media", "suggestions", "postback", "urlAction")
                val textKeys = listOf("text", "message", "body", "title", "description", "content", "caption")
                fun extract(any: Any?, depth: Int = 0) {
                    if (depth > 10) return
                    when (any) {
                        is org.json.JSONObject -> {
                            textKeys.forEach { k ->
                                any.optString(k).takeIf { it.isNotEmpty() && !it.startsWith("{") }
                                    ?.let { texts.add(it) }
                            }
                            any.keys().forEach { k ->
                                if (k !in skipKeys) try { extract(any.get(k), depth + 1) } catch (_: Exception) {}
                            }
                        }
                        is org.json.JSONArray -> for (i in 0 until any.length()) extract(any.get(i), depth + 1)
                    }
                }
                extract(obj)
                texts.distinct().joinToString(" | ").takeIf { it.isNotEmpty() }
            }
    } catch (_: Exception) { json }

    // ─── Logging ──────────────────────────────────────────────────────────────

    private fun buildSummary(stats: ProcessingStats, elapsedMs: Long) = """
        ┌─────── SMS Worker Complete ──────────────────
        │  Total     : ${stats.total}
        │  Processed : ${stats.processed.get()}
        │  Parsed    : ${stats.parsed.get()}
        │  Saved     : ${stats.saved.get()}
        │  Elapsed   : ${elapsedMs}ms
        │  Speed     : ${"%.1f".format(stats.msgPerSec())} msg/s
        └──────────────────────────────────────────────
    """.trimIndent()
}