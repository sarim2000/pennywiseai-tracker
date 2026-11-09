package com.pennywiseai.tracker.worker

import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.data.preferences.BankAccountMergeStore

import com.pennywiseai.tracker.R
import android.content.Context
import android.os.Process
import android.os.Trace
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.manager.TransactionDeduplication
import com.pennywiseai.tracker.core.TimeConstants
import com.pennywiseai.tracker.data.manager.SmsScanParamsCalculator
import com.pennywiseai.tracker.data.manager.SmsScanParamsInput
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.ScanPreferences
import com.pennywiseai.tracker.data.repository.*
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleEngine
import com.pennywiseai.tracker.worker.smsscan.DeviceSmsMessageSource
import com.pennywiseai.tracker.worker.smsscan.ProcessingStats
import com.pennywiseai.tracker.worker.smsscan.ScanRunContext
import com.pennywiseai.tracker.worker.smsscan.ScanTransactionWriter
import com.pennywiseai.tracker.worker.smsscan.SmsScanClassifier
import com.pennywiseai.tracker.worker.smsscan.SmsScanPipeline
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import kotlin.system.measureTimeMillis

/**
 * Optimized SMS Worker — the WorkManager entry point for a scan. Prepares the run
 * (window, rebuild, caches), then runs [SmsScanPipeline]: parallel parse → sequential save.
 *
 * Architecture:
 * ┌────────────────────────────────────────────────────────────────────┐
 * │  SmsMessageSource   ──►  [feed]                                   │
 * │                               │                                   │
 * │              ┌────────────────┘  (N coroutines, Dispatchers.Default)│
 * │              ▼                                                    │
 * │        SmsScanClassifier  ──►  [results]                          │
 * │                                        │                          │
 * │              ┌─────────────────────────┘  (1 coroutine, Dispatchers.IO)│
 * │              ▼                                                    │
 * │        ScanTransactionWriter (DB save + balance update)           │
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
    private val userPreferencesRepository: ScanPreferences,
    private val unrecognizedSmsRepository: UnrecognizedSmsRepository,
    private val ruleRepository: RuleRepository,
    private val database: com.pennywiseai.tracker.data.database.PennyWiseDatabase,
    private val ignoredAccountsStore: IgnoredAccountsStore,
    private val ruleEngine: RuleEngine,
    private val tagRepository: TagRepository,
    private val generateIncomeAutopayUseCase: com.pennywiseai.tracker.domain.usecase.GenerateIncomeAutopayUseCase,
    taughtTemplatesStore: com.pennywiseai.tracker.data.preferences.TaughtTemplatesStore
) : CoroutineWorker(appContext, workerParams) {
    private val bankAccountMerges = BankAccountMergeStore(appContext)
    private val taughtTemplates = lazy {
        taughtTemplatesStore.all().groupBy { it.senderKey }
    }

    companion object {
        const val TAG                               = "OptimizedSmsReaderWorker"
        const val WORK_NAME                         = "optimized_sms_reader_work"
        const val INPUT_FORCE_RESYNC                = "input_force_resync"
        // Re-read the whole scan window without deleting anything: adds what an
        // earlier scan missed and keeps every edit (the gentle "scan again").
        const val INPUT_RESCAN_ALL                  = "input_rescan_all"
        const val PROGRESS_TOTAL                    = "progress_total"
        const val PROGRESS_PROCESSED                = "progress_processed"
        const val PROGRESS_PARSED                   = "progress_parsed"
        const val PROGRESS_SAVED                    = "progress_saved"

        /** The final counts handed back on success — `progress` is gone by then. */
        fun completionData(total: Int, processed: Int, parsed: Int, saved: Int): Data = workDataOf(
            PROGRESS_TOTAL     to total,
            PROGRESS_PROCESSED to processed,
            PROGRESS_PARSED    to parsed,
            PROGRESS_SAVED     to saved
        )
        const val PROGRESS_BLOCKED                  = "progress_blocked"
        const val PROGRESS_TIME_ELAPSED             = "progress_time_elapsed"
        const val PROGRESS_ESTIMATED_TIME_REMAINING = "progress_estimated_time_remaining"
        const val PROGRESS_CURRENT_BATCH            = "progress_current_batch"
        const val PROGRESS_TOTAL_BATCHES            = "progress_total_batches"
        const val PROGRESS_MSG_PER_SEC              = "progress_msg_per_sec"
        const val PROGRESS_ETA_SECONDS              = "progress_eta_seconds"

        /** Everything the scan screens read while a scan runs. The value types are part of that contract. */
        internal fun progressData(stats: ProcessingStats): Data {
            val p   = stats.processed.get()
            val mps = stats.msgPerSec()
            val eta = stats.etaSec()
            return workDataOf(
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
            )
        }

        private const val NOTIFICATION_ID           = 9001

        // GPay P2P is parsed by SBIBankParser and attributed to this partner bank; it is
        // the one dedup collapses and the only one that leaves a phantom account (#456).
        // Matches SBIBankParser.getBankName() and the literal in TransactionDeduplication.
        private const val GPAY_PARTNER_BANK         = "State Bank of India"

        fun buildProgressNotification(context: Context, processed: Int, total: Int): android.app.Notification {
            val channelId = "sms_scan_channel"
            // SDK_INT is always >= 26 for this project; NotificationChannel is always available
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    android.app.NotificationChannel(channelId, context.getString(R.string.notif_sms_scan_channel_name), android.app.NotificationManager.IMPORTANCE_LOW)
                )
            }
            return androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle(context.getString(R.string.notif_sms_scan_title))
                .setContentText(if (total > 0) context.getString(R.string.notif_sms_scan_progress, processed, total) else context.getString(R.string.notif_sms_scan_reading))
                .setProgress(total, processed, total == 0)
                .setOngoing(true)
                .setSilent(true)
                .build()
        }
    }

    // ─── Expedited work ───────────────────────────────────────────────────────

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(NOTIFICATION_ID, buildProgressNotification(applicationContext, 0, 0))

    // ─── Entry point ──────────────────────────────────────────────────────────

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
        val recentSinceMillis = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000

        Trace.beginSection("SmsWorker.doWork")
        try {
            val forceResync = inputData.getBoolean(INPUT_FORCE_RESYNC, false)
            val rescanAll = inputData.getBoolean(INPUT_RESCAN_ALL, false)
            Log.i(TAG, "Starting SMS worker (forceResync=$forceResync, rescanAll=$rescanAll)")

            // The window is computed before any delete: a rebuild may only drop
            // rows it will re-read.
            val (scanStartTime, needsFullScan) = computeScanParams(forceResync || rescanAll)

            var rebuildStartedAt: LocalDateTime? = null
            var accountsBeforeRebuild: Map<Pair<String, String>, AccountBalanceEntity> = emptyMap()
            if (forceResync) {
                rebuildStartedAt = LocalDateTime.now()
                Trace.beginSection("clearDatabase")
                try {
                    // Delete only what the inbox can rebuild (active SMS rows
                    // outside loans/groups); manual, imported, notification and
                    // deleted rows survive — nothing would bring them back. Re-parse uses
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
                    accountsBeforeRebuild = accountBalanceRepository.getAllLatestBalancesOnce()
                        .filter { it.accountType != null }
                        .associateBy { it.bankName to it.accountLast4 }
                    transactionRepository.deleteRebuildableSmsTransactions(
                        java.time.LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(scanStartTime), java.time.ZoneId.systemDefault()
                        )
                    )
                    accountBalanceRepository.deleteRebuildableBalances()
                } finally {
                    Trace.endSection()
                }
                Log.i(TAG, "Force resync: SMS rows cleared (loans, groups, deleted and non-SMS rows kept)")
            }

            val now = System.currentTimeMillis()

            Trace.beginSection("preloadCaches")
            val scanContext = try {
                ScanRunContext(
                    merchantCategories = merchantMappingRepository.getAllMappingsAsMap(),
                    categoryIsIncome = database.categoryDao().getAllCategoriesList().associate { it.name to it.isIncome },
                    rulesByType = TransactionType.entries.associateWith { type ->
                        ruleRepository.getActiveRulesByType(type)
                    },
                    accountsBeforeRebuild = accountsBeforeRebuild,
                    rebuildStartedAt = rebuildStartedAt,
                )
            } finally {
                Trace.endSection()
            }
            Log.i(TAG, "Caches: ${scanContext.merchantCategories.size} merchant mappings, ${scanContext.rulesByType.values.map { it.size }.sum()} rules")

            // Fast COUNT queries — avoids loading all messages before the pipeline
            val source = DeviceSmsMessageSource(applicationContext)
            val total = source.count(scanStartTime)
            val parserConcurrency = maxOf(1, Runtime.getRuntime().availableProcessors() - 1)
            Log.i(TAG, "Pipeline: $parserConcurrency parsers | 1 saver | $total messages")

            val stats = ProcessingStats(total = total)
            reportProgress(stats)

            val writer = ScanTransactionWriter(
                transactionRepository = transactionRepository,
                subscriptionRepository = subscriptionRepository,
                accountBalanceRepository = accountBalanceRepository,
                cardRepository = cardRepository,
                ruleRepository = ruleRepository,
                ruleEngine = ruleEngine,
                tagRepository = tagRepository,
                unrecognizedSmsRepository = unrecognizedSmsRepository,
                ignoredAccountsStore = ignoredAccountsStore,
                database = database,
                bankAccountMerges = bankAccountMerges,
                scanContext = scanContext,
            )
            val classifier = SmsScanClassifier(
                recentSinceMillis = recentSinceMillis,
                taughtTemplates = taughtTemplates,
                subscriptionRepository = subscriptionRepository,
                saveBalanceNotification = writer::saveBalanceNotification,
            )
            val pipeline = SmsScanPipeline(
                source = source,
                classify = classifier::classify,
                isTaught = classifier::matchesTaughtTemplate,
                writer = writer,
                parserConcurrency = parserConcurrency,
                onProgress = { reportProgress(it) },
                onTransactionsChanged = {
                    com.pennywiseai.tracker.widget.WidgetRefresher.refreshTransactionWidgets(applicationContext)
                },
            )

            val totalTime = measureTimeMillis {
                pipeline.run(scanStartTime, stats)
                persistScanState(now, needsFullScan)
                reportProgress(stats)
            }

            Log.i(TAG, buildSummary(stats, totalTime))
            // Rows rebuilt from balance-only SMS don't pass through processBalanceUpdate,
            // so put each typed account's type back on the rows that lost it (#792).
            for (row in accountsBeforeRebuild.values) accountBalanceRepository.restoreAccountType(row)
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
            // WorkManager clears `progress` once work finishes, so a caller
            // reading the result on SUCCEEDED saw every count as 0 — onboarding
            // said "No transactions found" after importing a whole inbox. Hand
            // the final counts back as output data.
            Result.success(
                completionData(stats.total, stats.processed.get(), stats.parsed.get(), stats.saved.get())
            )

        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in SMS worker", e)
            Result.failure()
        } finally {
            Trace.endSection()
        }
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    private suspend fun cleanUpAndFinalize(stats: ProcessingStats) {
        try { unrecognizedSmsRepository.cleanupOldEntries() }
        catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Cleanup error: ${e.message}")
        }
        try {
            val smsDuplicates = cleanupSmsDuplicates()
            if (smsDuplicates > 0) {
                Log.i(TAG, "Removed $smsDuplicates duplicate rows of the same SMS")
            }
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

    /**
     * Earlier rescans could store one SMS twice when a parser fix changed its
     * amount (#886). Keep the oldest row — it carries the user's category, notes
     * and tags — repairing its amount from the newest read when the old one is a
     * known parser artifact, and remove the rest the same way GPay duplicates are
     * removed.
     */
    private suspend fun cleanupSmsDuplicates(): Int {
        var removed = 0
        transactionRepository.getActiveSmsDuplicateRows()
            .groupBy { it.smsBody to (it.smsSender ?: "") }
            .values
            .forEach { group ->
                val keep = group.minBy { it.id }
                val newest = group.maxBy { it.id }
                // One transaction per SMS: an interrupted run must not leave a
                // duplicate soft-deleted with its balance rows or rule
                // applications behind — once deleted it no longer shows up here.
                database.withTransaction {
                    if (TransactionDeduplication.isParseArtifact(keep, newest)) {
                        transactionRepository.correctParsedAmount(keep, newest)
                    }
                    group.filter { it.id != keep.id }.forEach { dup ->
                        // Goal contributions on the duplicate move to [keep] (#914); a
                        // duplicate whose split shares can't move is kept (#346).
                        if (!transactionRepository.deleteDuplicate(dup.id, keep.id)) return@forEach
                        accountBalanceRepository.deleteBalancesForTransaction(dup.id)
                        ruleRepository.deleteRuleApplicationsForTransaction(dup.id.toString())
                        removed++
                    }
                }
            }
        return removed
    }

    private suspend fun cleanupExistingGPayDuplicates(): Int {
        val duplicates = transactionRepository.findGPayDuplicatesForCleanup()
        var removed = 0
        duplicates.forEach { (id, keeperId) ->
            if (!transactionRepository.deleteDuplicate(id, keeperId)) return@forEach
            accountBalanceRepository.deleteBalancesForTransaction(id)
            ruleRepository.deleteRuleApplicationsForTransaction(id.toString())
            removed++
        }

        prunePhantomGPayAccounts()
        return removed
    }

    /**
     * Remove the phantom account GPay dedup leaves behind (#456). GPay P2P is attributed
     * to the partner bank ("State Bank of India"); once its transactions are deduped away
     * against the real bank's SMS, balance rows that were never linked to a transaction
     * (transaction_id NULL) survive [deleteBalancesForTransaction] and surface as an
     * account with an inflated balance but no transactions.
     *
     * Sweep the partner-bank accounts and delete any that is purely a residue. The
     * per-account qualification and delete run atomically in a single DB transaction
     * (see [AccountBalanceRepository.deletePhantomGPayAccountIfQualifies]) so a
     * concurrent real-time balance insert can't be erased between the check and the
     * delete. Also cleans phantoms that predate this fix, not just fresh ones.
     */
    private suspend fun prunePhantomGPayAccounts() {
        val partnerBank = GPAY_PARTNER_BANK
        accountBalanceRepository.getAccountLast4sForBank(partnerBank).forEach { account ->
            val removed = accountBalanceRepository.deletePhantomGPayAccountIfQualifies(partnerBank, account)
            if (removed > 0) {
                Log.i(TAG, "Removed phantom GPay account ($removed orphaned balance rows)")
            }
        }
    }

    // ─── Progress ─────────────────────────────────────────────────────────────

    private suspend fun reportProgress(stats: ProcessingStats) {
        try {
            setProgress(progressData(stats))
        } catch (_: Exception) {}
    }

    private suspend fun persistScanState(now: Long, needsFullScan: Boolean) {
        // Persist scan state after all stages (including saving) finish successfully
        userPreferencesRepository.setLastScanTimestamp(now)
        if (needsFullScan) {
            val scanMonths = userPreferencesRepository.getSmsScanMonths()
            val scanAllTime = userPreferencesRepository.getSmsScanAllTime()
            val scanUseCustomDate = userPreferencesRepository.getSmsScanUseCustomDate()
            userPreferencesRepository.setLastScanPeriod(
                SmsScanParamsCalculator.resolveLastScanPeriod(
                    scanAllTime = scanAllTime,
                    scanUseCustomDate = scanUseCustomDate,
                    scanMonths = scanMonths,
                )
            )
        }
    }

    // ─── Scan window ───────────────────────────────────────────────────────────

    /** Computes scan params without reading any message bodies. */
    private suspend fun computeScanParams(forceResync: Boolean): Pair<Long, Boolean> {
        val params = SmsScanParamsCalculator.compute(
            SmsScanParamsInput(
                forceResync = forceResync,
                lastScanTimestamp = userPreferencesRepository.getLastScanTimestamp().first(),
                scanMonths = userPreferencesRepository.getSmsScanMonths(),
                scanAllTime = userPreferencesRepository.getSmsScanAllTime(),
                scanUseCustomDate = userPreferencesRepository.getSmsScanUseCustomDate(),
                scanCustomDateMillis = userPreferencesRepository.getSmsScanCustomDate(),
                lastScanPeriod = userPreferencesRepository.getLastScanPeriod().first(),
                nowMillis = System.currentTimeMillis(),
            )
        )
        return params.scanStartTime to params.needsFullScan
    }

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
