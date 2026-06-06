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
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import com.pennywiseai.tracker.data.manager.TransactionDeduplication
import com.pennywiseai.tracker.data.mapper.toEntity
import com.pennywiseai.tracker.data.mapper.toEntityType
import com.pennywiseai.tracker.core.TimeConstants
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.*
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleEngine
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.first
import kotlinx.datetime.toJavaLocalDateTime
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
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
    private val generateIncomeAutopayUseCase: com.pennywiseai.tracker.domain.usecase.GenerateIncomeAutopayUseCase
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG                               = "OptimizedSmsReaderWorker"
        const val WORK_NAME                         = "optimized_sms_reader_work"
        const val INPUT_FORCE_RESYNC                = "input_force_resync"
        const val PROGRESS_TOTAL                    = "progress_total"
        const val PROGRESS_PROCESSED                = "progress_processed"
        const val PROGRESS_PARSED                   = "progress_parsed"
        const val PROGRESS_SAVED                    = "progress_saved"
        const val PROGRESS_BLOCKED                  = "progress_blocked"
        const val PROGRESS_TIME_ELAPSED             = "progress_time_elapsed"
        const val PROGRESS_ESTIMATED_TIME_REMAINING = "progress_estimated_time_remaining"
        const val PROGRESS_CURRENT_BATCH            = "progress_current_batch"
        const val PROGRESS_TOTAL_BATCHES            = "progress_total_batches"
        const val PROGRESS_MSG_PER_SEC              = "progress_msg_per_sec"
        const val PROGRESS_ETA_SECONDS              = "progress_eta_seconds"

        private const val NOTIFICATION_ID           = 9001
        private const val PARSE_CHANNEL_CAPACITY    = 512
        private const val RESULT_CHANNEL_CAPACITY   = 512
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
    private var thirtyDaysAgoMillis: Long = 0L

    /** O(1) sender-to-parser lookup. Factory is only called once per unique sender string. */
    private class ParserHolder(val parser: BankParser?)
    private val parserCache = java.util.concurrent.ConcurrentHashMap<String, ParserHolder>(256)
    private fun cachedParser(sender: String): BankParser? =
        parserCache.computeIfAbsent(sender) { ParserHolder(BankParserFactory.getParser(sender)) }.parser

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
    private var ruleCache: Map<TransactionType, List<TransactionRule>> = emptyMap()

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
        val duplicates = AtomicInteger(0)
        val blocked   = AtomicInteger(0)
        val startTime = System.currentTimeMillis()

        private val RING_SIZE          = 8192
        private val MIN_SAMPLES        = 10
        private val ring               = java.util.concurrent.atomic.AtomicLongArray(RING_SIZE)
        private val head               = AtomicInteger(0)
        private val writtenCount       = AtomicInteger(0)

        fun recordCompletion() {
            val pos = head.getAndIncrement() and (RING_SIZE - 1)
            ring.lazySet(pos, System.currentTimeMillis())
            if (writtenCount.get() < RING_SIZE) writtenCount.incrementAndGet()
        }

        fun elapsedMs() = System.currentTimeMillis() - startTime

        fun msgPerSec(): Float {
            val now      = System.currentTimeMillis()
            val cutoff   = now - ETA_WINDOW_MS
            var count    = 0
            val mask     = RING_SIZE - 1
            val limit    = writtenCount.get()
            val currentHead = head.get()
            for (i in 0 until limit) {
                val pos = (currentHead - 1 - i) and mask
                val ts  = ring.get(pos)
                if (ts in (cutoff + 1)..now) count++
            }
            return when {
                count >= MIN_SAMPLES ->
                    count / (ETA_WINDOW_MS / 1000f)

                else -> {
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

    private enum class SaveOutcome {
        SAVED,
        UPDATED_DUPLICATE,
        SKIPPED_DUPLICATE,
        SKIPPED
    }

    private data class DeferredBalanceUpdate(
        val parsed: ParsedTransaction,
        val entity: com.pennywiseai.tracker.data.database.entity.TransactionEntity,
        val transactionId: Long
    )

    // ─── Expedited work ───────────────────────────────────────────────────────

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(NOTIFICATION_ID, buildProgressNotification(applicationContext, 0, 0))

    // ─── Entry point ──────────────────────────────────────────────────────────

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
        thirtyDaysAgoMillis = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000

        Trace.beginSection("SmsWorker.doWork")
        try {
            val forceResync = inputData.getBoolean(INPUT_FORCE_RESYNC, false)
            Log.i(TAG, "Starting SMS worker (forceResync=$forceResync)")

            if (forceResync) {
                Trace.beginSection("clearDatabase")
                try {
                    // Preserve user-curated rows (loan-linked + grouped) so
                    // months of curation work survive a rescan. Re-parse uses
                    // transaction_hash UNIQUE + OnConflictStrategy.IGNORE, so
                    // surviving rows are not duplicated. Fixes #401.
                    //
                    // Order matters: drop the transactions FIRST, then run the
                    // companion balance-cleanup which decides orphan-status
                    // against the now-current transactions table. Without the
                    // companion, deleteAllBalances() would wipe balance entries
                    // for the preserved transactions and the re-parse would
                    // never regenerate them (hash collision short-circuits
                    // processBalanceUpdate), leaving gaps in the balance
                    // time-series.
                    transactionRepository.deleteUncuratedTransactions()
                    accountBalanceRepository.deleteRebuildableBalances()
                } finally {
                    Trace.endSection()
                }
                Log.i(TAG, "Force resync: uncurated rows cleared (loans + groups preserved)")
            }

            val (scanStartTime, needsFullScan) = computeScanParams(forceResync)
            val now = System.currentTimeMillis()

            Trace.beginSection("preloadCaches")
            try {
                merchantMappingCache = merchantMappingRepository.getAllMappingsAsMap()
                ruleCache = TransactionType.entries.associateWith { type ->
                    ruleRepository.getActiveRulesByType(type)
                }
            } finally {
                Trace.endSection()
            }
            Log.i(TAG, "Caches: ${merchantMappingCache.size} merchant mappings, ${ruleCache.values.map { it.size }.sum()} rules")

            // Fast COUNT queries — avoids loading all messages before the pipeline
            val smsCount = countSmsMessages(scanStartTime)
            val rcsCount = countRcsMessages(scanStartTime / 1000)
            val total = smsCount + rcsCount
            val parserConcurrency = maxOf(1, Runtime.getRuntime().availableProcessors() - 1)
            Log.i(TAG, "Pipeline: $parserConcurrency parsers | 1 saver | $total messages")

            val stats = ProcessingStats(total = total)
            reportProgress(stats)

            val totalTime = measureTimeMillis {
                streamPipeline(scanStartTime, needsFullScan, now, stats, parserConcurrency)
            }

            Log.i(TAG, buildSummary(stats, totalTime))
            cleanUpAndFinalize(stats)
            // Materialise any due income-autopay phantoms (#371). Runs after
            // SMS processing so a real INCOME SMS that landed for the same
            // amount + merchant in this scan can dedupe via the autopay hash
            // check if the user manually re-uses the same hash format later.
            try {
                generateIncomeAutopayUseCase.execute()
            } catch (e: Exception) {
                Log.e(TAG, "Income autopay phantom creator failed: ${e.message}", e)
            }
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

    private suspend fun streamPipeline(
        scanStartTime: Long,
        needsFullScan: Boolean,
        now: Long,
        stats: ProcessingStats,
        parserConcurrency: Int
    ) = coroutineScope {
        val feed    = Channel<SmsMessage>(PARSE_CHANNEL_CAPACITY)
        val results = Channel<ParseResult>(RESULT_CHANNEL_CAPACITY)

        // Stage 1 – Feed (streams SMS cursor + RCS directly into the channel)
        launch(Dispatchers.IO) {
            try {
                streamSmsToChannel(feed, scanStartTime)
                streamRcsToChannel(feed, scanStartTime / 1000)
            } finally {
                feed.close()
            }
        }

        // Stage 2 – Parse (all available CPU cores)
        val parserJobs = (1..parserConcurrency).map { id ->
            launch(Dispatchers.Default) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
                Trace.beginSection("parse.$id")
                try {
                    for (sms in feed) {
                        try {
                            results.send(parseSms(sms))
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.e(TAG, "Error parsing SMS from ${sms.sender}: ${e.message}")
                            results.send(ParseResult.Discard(sms))
                        }
                    }
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
        // Balance updates run concurrently via a dedicated consumer so they never block the saver.
        val balanceUpdates = Channel<DeferredBalanceUpdate>(Channel.BUFFERED)
        val balanceUpdater = launch(Dispatchers.IO) {
            for (update in balanceUpdates) {
                try { processBalanceUpdate(update.parsed, update.entity, update.transactionId) }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Balance update failed: ${e.message}")
                }
            }
        }

        val saver = launch(Dispatchers.IO) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
            val unrecognizedBatch = ArrayList<SmsMessage>(UNRECOGNIZED_BATCH_SIZE)
            var widgetNeedsUpdate = false
            var lastReportTime = 0L

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
                                when (saveTransaction(result.parsed, result.sms, stats, balanceUpdates)) {
                                    SaveOutcome.SAVED -> {
                                        stats.saved.incrementAndGet()
                                        widgetNeedsUpdate = true
                                    }
                                    SaveOutcome.UPDATED_DUPLICATE -> {
                                        stats.duplicates.incrementAndGet()
                                        widgetNeedsUpdate = true
                                    }
                                    SaveOutcome.SKIPPED_DUPLICATE -> {
                                        stats.duplicates.incrementAndGet()
                                    }
                                    SaveOutcome.SKIPPED -> Unit
                                }
                            } finally {
                                Trace.endSection()
                            }
                        }
                    }

                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastReportTime >= 250L || p == 1 || p == stats.total) {
                        reportProgress(stats)
                        lastReportTime = nowMs
                    }
                }
            } finally {
                Trace.endSection()
            }

            if (unrecognizedBatch.isNotEmpty()) flushUnrecognizedBatch(unrecognizedBatch)
            if (widgetNeedsUpdate)
                com.pennywiseai.tracker.widget.RecentTransactionsWidgetUpdateWorker.enqueueOneShot(applicationContext)
        }

        saver.join()
        balanceUpdates.close()
        balanceUpdater.join()

        // Persist scan state after all stages (including saving) finish successfully
        userPreferencesRepository.setLastScanTimestamp(now)
        if (needsFullScan) {
            val scanMonths = userPreferencesRepository.getSmsScanMonths()
            val scanAllTime = userPreferencesRepository.getSmsScanAllTime()
            userPreferencesRepository.setLastScanPeriod(if (scanAllTime) -1 else scanMonths)
        }

        reportProgress(stats)
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

        val isRecent = sms.timestamp > thirtyDaysAgoMillis

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
    ): ParseResult? = when (parser) {

        is SBIBankParser -> {
            if (!parser.isUPIMandateNotification(sms.body) || !isRecent) null
            else parser.parseUPIMandateSubscription(sms.body)?.let { info ->
                ParseResult.SpecialNotification(sms) {
                    subscriptionRepository.createOrUpdateFromSBIMandate(info, parser.getBankName(), sms.body)
                }
            }
        }

        is PNBBankParser -> {
            if (!parser.isUPIMandateNotification(sms.body) || !isRecent) null
            else parser.parseUPIMandateSubscription(sms.body)?.let { info ->
                ParseResult.SpecialNotification(sms) {
                    subscriptionRepository.createOrUpdateFromMandate(info, parser.getBankName(), sms.body)
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
                    // Process if the SMS is recent, OR if the payment date is still in the future
                    // (a mandate from 45 days ago with next-debit next week must still create the subscription).
                    val paymentStillUpcoming = info.nextDeductionDate?.let { dateStr ->
                        try {
                            java.time.LocalDate.parse(
                                dateStr,
                                java.time.format.DateTimeFormatter.ofPattern(info.dateFormat)
                            ).isAfter(java.time.LocalDate.now())
                        } catch (_: Exception) { false }
                    } ?: false

                    if (isRecent || paymentStillUpcoming) {
                        ParseResult.SpecialNotification(sms) {
                            subscriptionRepository.createOrUpdateFromFederalBankMandate(info, parser.getBankName(), sms.body)
                        }
                    } else null
                }
        }

        is HDFCBankParser -> {
            val actions = mutableListOf<suspend () -> Unit>()
            if (parser.isBalanceUpdateNotification(sms.body)) {
                parser.parseBalanceUpdate(sms.body)?.let { info ->
                    actions += {
                        accountBalanceRepository.insertBalanceUpdate(
                            bankName     = info.bankName,
                            accountLast4 = info.accountLast4 ?: "XXXX",
                            balance      = info.balance,
                            timestamp    = info.asOfDate?.toJavaLocalDateTime() ?: sms.timestamp.toLocalDateTime(),
                            currency     = parser.getCurrency()
                        )
                    }
                }
                // If unparseable as balance update, fall through to check eMandate/futureDebit below
            }
            if (parser.isEMandateNotification(sms.body) && isRecent)
                parser.parseEMandateSubscription(sms.body)?.let { info ->
                    actions += { subscriptionRepository.createOrUpdateFromEMandate(info, parser.getBankName(), sms.body) }
                }
            if (parser.isFutureDebitNotification(sms.body) && isRecent)
                parser.parseFutureDebit(sms.body)?.let { info ->
                    actions += { subscriptionRepository.createOrUpdateFromEMandate(info, parser.getBankName(), sms.body) }
                }
            if (actions.isNotEmpty()) ParseResult.SpecialNotification(sms) {
                actions.forEach { action ->
                    try { action() } catch (e: Exception) {
                        Log.e(TAG, "HDFC action failed: ${e.message}", e)
                    }
                }
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
                        timestamp    = info.asOfDate?.toJavaLocalDateTime() ?: sms.timestamp.toLocalDateTime(),
                        currency     = parser.getCurrency()
                    )
                }
            }
        }

        else -> null
    }

    // ─── Stage 3b: Regular transactions ──────────────────────────────────────

    private suspend fun saveTransaction(
        parsed: ParsedTransaction,
        sms: SmsMessage,
        stats: ProcessingStats,
        balanceUpdates: SendChannel<DeferredBalanceUpdate>
    ): SaveOutcome = try {
        coroutineScope {
            val entity = parsed.toEntity()
            val hashDeferred = async { transactionRepository.getTransactionByHash(entity.transactionHash) }

            val customCategory = merchantMappingCache[entity.merchantName]
            val mapped = if (customCategory != null) entity.copy(category = customCategory) else entity

            val activeRules = ruleCache[mapped.transactionType] ?: emptyList()
            val isBlocked = ruleEngine.shouldBlockTransaction(mapped, sms.body, activeRules) != null

            if (hashDeferred.await() != null) return@coroutineScope SaveOutcome.SKIPPED

            if (isBlocked) {
                stats.blocked.incrementAndGet()
                return@coroutineScope SaveOutcome.SKIPPED
            }

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

            val duplicate = transactionRepository.findPotentialDuplicates(finalEntity).firstOrNull()
            if (duplicate != null) {
                if (TransactionDeduplication.shouldReplaceWithIncoming(duplicate, finalEntity)) {
                    val replacement = finalEntity.copy(
                        id = duplicate.id,
                        transactionHash = duplicate.transactionHash,
                        isRecurring = duplicate.isRecurring || finalEntity.isRecurring,
                        createdAt = duplicate.createdAt
                    )
                    transactionRepository.updateTransaction(replacement)
                    accountBalanceRepository.deleteBalancesForTransaction(duplicate.id)
                    replaceRuleApplications(duplicate.id, ruleApps)
                    balanceUpdates.send(DeferredBalanceUpdate(parsed, replacement, duplicate.id))
                    return@coroutineScope SaveOutcome.UPDATED_DUPLICATE
                }
                return@coroutineScope SaveOutcome.SKIPPED_DUPLICATE
            }

            val rowId = transactionRepository.insertTransaction(finalEntity)
            if (rowId == -1L) return@coroutineScope SaveOutcome.SKIPPED

            saveRuleApplications(rowId, ruleApps)
            balanceUpdates.send(DeferredBalanceUpdate(parsed, finalEntity, rowId))
            SaveOutcome.SAVED
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(TAG, "Error saving transaction: ${e.message}")
        SaveOutcome.SKIPPED
    }

    private suspend fun replaceRuleApplications(
        transactionId: Long,
        ruleApps: List<com.pennywiseai.tracker.domain.model.rule.RuleApplication>
    ) {
        ruleRepository.deleteRuleApplicationsForTransaction(transactionId.toString())
        saveRuleApplications(transactionId, ruleApps)
    }

    private suspend fun saveRuleApplications(
        transactionId: Long,
        ruleApps: List<com.pennywiseai.tracker.domain.model.rule.RuleApplication>
    ) {
        if (ruleApps.isEmpty()) return
        ruleRepository.saveRuleApplications(
            ruleApps.map { it.copy(transactionId = transactionId.toString()) }
        )
    }

    // ─── Balance update ───────────────────────────────────────────────────────

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
            card == null                                                 -> accountLast4
            card.cardType == CardType.CREDIT                             -> accountLast4
            card.cardType == CardType.DEBIT && card.accountLast4 != null -> card.accountLast4
            else                                                         -> return
        }

        val isCreditCard = card?.cardType == CardType.CREDIT ||
                parsed.type.toEntityType() == TransactionType.CREDIT

        val existing = accountBalanceRepository.getLatestBalance(parsed.bankName, targetAccount)

        // 1. Calculate the new balance
        val newBalance = when {
            // Explicit balance from SMS takes priority
            parsed.balance != null -> parsed.balance!!

            // For Credit Cards: usually transaction amount adds to current outstanding balance
            isCreditCard -> (existing?.balance ?: BigDecimal.ZERO) + parsed.amount

            // For existing Credit Card accounts receiving a payment (INCOME)
            existing?.isCreditCard == true && parsed.type.toEntityType() == TransactionType.INCOME -> {
                (existing.balance - parsed.amount).max(BigDecimal.ZERO)
            }

            // Standard fallback calculation based on transaction type
            else -> {
                val cur = existing?.balance ?: BigDecimal.ZERO
                when (parsed.type.toEntityType()) {
                    TransactionType.INCOME -> cur + parsed.amount
                    TransactionType.EXPENSE, TransactionType.INVESTMENT -> (cur - parsed.amount).max(BigDecimal.ZERO)
                    else -> cur
                }
            }
        }

        val balanceEntity = AccountBalanceEntity(
            bankName      = parsed.bankName,
            accountLast4  = targetAccount,
            balance       = newBalance,
            timestamp     = entity.dateTime,
            transactionId = if (rowId != -1L) rowId else null,
            creditLimit   = existing?.creditLimit,
            isCreditCard  = isCreditCard || (existing?.isCreditCard ?: false),
            smsSource     = parsed.smsBody.take(500),
            sourceType    = "TRANSACTION",
            currency      = parsed.currency
        )

        accountBalanceRepository.insertBalance(balanceEntity)

        val logMsg = if (parsed.creditLimit != null) {
            "Saved balance/limit (${CurrencyFormatter.formatCurrency(parsed.creditLimit!!, parsed.currency)})"
        } else {
            "Saved balance (${CurrencyFormatter.formatCurrency(newBalance, parsed.currency)})"
        }
        Log.i(TAG, logMsg)
    }

    // ─── Unrecognized SMS batch ────────────────────────────────────────────────

    private suspend fun flushUnrecognizedBatch(batch: ArrayList<SmsMessage>) {
        val entities = batch.map { sms ->
            UnrecognizedSmsEntity(
                sender     = sms.sender,
                smsBody    = sms.body,
                receivedAt = sms.timestamp.toLocalDateTime()
            )
        }
        try {
            unrecognizedSmsRepository.insertAll(entities)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error storing unrecognized SMS batch: ${e.message}")
        }
        batch.clear()
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    private suspend fun cleanUpAndFinalize(stats: ProcessingStats) {
        try { unrecognizedSmsRepository.cleanupOldEntries() }
        catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Cleanup error: ${e.message}")
        }
        try {
            val deletedDuplicates = cleanupExistingGPayDuplicates()
            if (deletedDuplicates > 0) {
                Log.i(TAG, "Cleaned up $deletedDuplicates existing GPay duplicate transactions")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "GPay duplicate cleanup error: ${e.message}")
        }
        if (stats.saved.get() > 0) {
            try { llmRepository.updateSystemPrompt() }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Prompt update error: ${e.message}")
            }
        }
    }

    private suspend fun cleanupExistingGPayDuplicates(): Int {
        val duplicateIds = transactionRepository.findGPayDuplicateIdsForCleanup()
        duplicateIds.forEach { id ->
            transactionRepository.deleteTransactionById(id)
            accountBalanceRepository.deleteBalancesForTransaction(id)
            ruleRepository.deleteRuleApplicationsForTransaction(id.toString())
        }
        return duplicateIds.size
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
                PROGRESS_BLOCKED                  to stats.blocked.get(),
                PROGRESS_TIME_ELAPSED             to stats.elapsedMs(),
                PROGRESS_ESTIMATED_TIME_REMAINING to (eta * TimeConstants.MILLIS_PER_SECOND),
                PROGRESS_ETA_SECONDS              to eta,
                PROGRESS_MSG_PER_SEC              to mps,
                PROGRESS_CURRENT_BATCH            to p,
                PROGRESS_TOTAL_BATCHES            to stats.total
            ))
        } catch (_: Exception) {}
    }

    // ─── SMS / RCS reading (streaming) ─────────────────────────────────────────

    /** Computes scan params without reading any message bodies. */
    private suspend fun computeScanParams(forceResync: Boolean): Pair<Long, Boolean> {
        val lastScanTimestamp = userPreferencesRepository.getLastScanTimestamp().first() ?: 0L
        val scanMonths        = userPreferencesRepository.getSmsScanMonths()
        val scanAllTime       = userPreferencesRepository.getSmsScanAllTime()
        val lastScanPeriod    = userPreferencesRepository.getLastScanPeriod().first() ?: 0
        val now               = System.currentTimeMillis()

        val scanAllTimeToggled = scanAllTime && lastScanPeriod != -1
        val scanAllTimeToggledOff = !scanAllTime && lastScanPeriod == -1
        val needsFullScan = forceResync || lastScanTimestamp == 0L ||
            (lastScanPeriod >= 0 && scanMonths > lastScanPeriod) ||
            scanAllTimeToggled || scanAllTimeToggledOff

        val scanStartTime = if (needsFullScan) {
            if (scanAllTime) 0L
            else java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.MONTH, -scanMonths)
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0);      set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else {
            val threeDaysAgo = now - TimeConstants.MILLIS_PER_3_DAYS
            val periodLimit  = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.MONTH, -scanMonths)
            }.timeInMillis
            maxOf(minOf(lastScanTimestamp, threeDaysAgo), periodLimit)
        }
        return scanStartTime to needsFullScan
    }

    /** Fast COUNT-only query — reads zero message bodies. */
    private suspend fun countSmsMessages(scanStartTime: Long): Int {
        var count = 0
        try {
            applicationContext.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("COUNT(*)"),
                "${Telephony.Sms.TYPE} = ? AND ${Telephony.Sms.DATE} >= ?",
                arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString(), scanStartTime.toString()),
                null
            )?.use { c ->
                if (c.moveToFirst()) count = c.getInt(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error counting SMS", e)
        }
        return count
    }

    /** Robust query for RCS messages. */
    private suspend fun countRcsMessages(scanStartSeconds: Long): Int {
        try {
            applicationContext.contentResolver.query(
                "content://mms".toUri(),
                arrayOf("COUNT(*)"),
                "date >= ? AND tr_id LIKE 'proto:%'",
                arrayOf(scanStartSeconds.toString()),
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    return c.getInt(0)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Fast RCS count failed, falling back to cursor iteration: ${e.message}")
        }

        var count = 0
        try {
            applicationContext.contentResolver.query(
                "content://mms".toUri(),
                arrayOf("tr_id"),
                "date >= ?",
                arrayOf(scanStartSeconds.toString()),
                null
            )?.use { c ->
                val trIdIdx = c.getColumnIndex("tr_id")
                if (trIdIdx >= 0) {
                    while (c.moveToNext()) {
                        val trId = c.getString(trIdIdx)
                        if (trId != null && trId.startsWith("proto:")) {
                            count++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error counting RCS via fallback scan", e)
        }
        return count
    }

    /** Streams SMS cursor rows directly into the feed channel — no intermediate list. */
    private suspend fun streamSmsToChannel(
        feed: SendChannel<SmsMessage>,
        scanStartTime: Long
    ) {
        var count = 0
        try {
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
                    feed.send(SmsMessage(
                        id        = c.getLong(idIdx),
                        sender    = c.getString(addressIdx) ?: "",
                        timestamp = c.getLong(dateIdx),
                        body      = c.getString(bodyIdx) ?: "",
                        type      = c.getInt(typeIdx)
                    ))
                    count++
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error streaming SMS", e)
        }
        Log.i(TAG, "Streamed $count SMS messages to pipeline")
    }

    /** Streams RCS messages into the feed channel. */
    private suspend fun streamRcsToChannel(
        feed: SendChannel<SmsMessage>,
        scanStartSeconds: Long
    ) {
        var count = 0
        try {
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
                    var text = getRcsMessageText(messageId) ?: continue
                    if (text.trim().startsWith("{")) text = extractTextFromRcsJson(text) ?: continue

                    feed.send(SmsMessage(messageId, sender, date * 1000, text, Telephony.Sms.MESSAGE_TYPE_INBOX))
                    count++
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error streaming RCS", e)
        }
        Log.i(TAG, "Streamed $count RCS messages to pipeline")
    }

    // ─── RCS helpers ──────────────────────────────────────────────────────────

    private fun extractRcsSender(trId: String): String? = try {
        val decoded = String(android.util.Base64.decode(trId.removePrefix("proto:"), android.util.Base64.DEFAULT))
        Regex("""([a-z_]+)_[a-z0-9]+_agent@rbm\.goog""").find(decoded)?.let { m ->
            return m.groupValues[1].split("_")
                .joinToString(" ") { if (it.isNotEmpty()) it.substring(0, 1).uppercase() + it.substring(1) else it }
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
        │  Duplicates: ${stats.duplicates.get()}
        │  Elapsed   : ${elapsedMs}ms
        │  Speed     : ${"%.1f".format(stats.msgPerSec())} msg/s
        └──────────────────────────────────────────────
    """.trimIndent()
}
