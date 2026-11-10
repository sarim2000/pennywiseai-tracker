package com.pennywiseai.tracker.receiver

import com.pennywiseai.tracker.utils.withLatinDigits
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.data.repository.AppLockRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.di.ApplicationScope
import com.pennywiseai.tracker.ui.security.BiometricAuthManager
import com.pennywiseai.tracker.domain.usecase.LearnCategoryUseCase
import com.pennywiseai.tracker.ui.components.QuickCategoryPickerSheet
import com.pennywiseai.tracker.ui.components.forPicker
import com.pennywiseai.tracker.widget.WidgetRefresher
import com.pennywiseai.tracker.ui.viewmodel.PennyWiseUserTheme
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.worker.DailySummaryWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Translucent activity launched from the txn-alert notification's
 * "More categories…" action (#303). Hosts the shared
 * [QuickCategoryPickerSheet] so the user can pick any category — not just
 * the 3 quick-picks rendered as inline notification buttons — without
 * navigating into the full transaction-detail screen.
 *
 * Declared `singleTask` in the manifest so re-launches don't stack; that
 * means a second notification tap routes through [onNewIntent] rather than
 * a fresh [onCreate], which is why the picker args live in a mutableState
 * the Compose tree observes.
 *
 * On pick: file the transaction and learn the merchant (see
 * [LearnCategoryUseCase]), swap the alert for a short confirmation, finish.
 * On dismiss without picking: finish. "Not an expense" (or "Not income")
 * keeps the row but drops it from totals, like the detail screen's toggle
 * (#451) — never a delete, which would shift the balance ledger.
 *
 * From the evening recap it walks the day's unsorted rows ([EXTRA_WALK_IDS]):
 * each pick shows the next one still unsorted (re-read, since learning a
 * merchant can sort others) until none are left or the user dismisses.
 *
 * Behind the app lock, like opening the app: the sheet shows the
 * transaction's category and tags, so a locked app asks first.
 */
@AndroidEntryPoint
class QuickCategoryPickerActivity : FragmentActivity() {

    @Inject lateinit var transactionRepository: TransactionRepository
    @Inject lateinit var categoryRepository: CategoryRepository
    @Inject lateinit var tagRepository: TagRepository
    @Inject lateinit var learnCategory: LearnCategoryUseCase
    @Inject lateinit var appLockRepository: AppLockRepository
    @Inject lateinit var biometricAuthManager: BiometricAuthManager

    /** Nothing is read or shown until this is true. */
    private val unlocked = mutableStateOf(false)

    // App-lifetime scope so the DB write survives finish() — the activity
    // closes immediately after the user picks.
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope


    // Holds the args from the latest intent (initial or via onNewIntent), so
    // a second notification tap re-targets the picker at the new transaction.
    private val currentArgs = mutableStateOf<PickerArgs?>(null)

    // Walk mode only: the recap's unsorted ids still to visit (current first),
    // and how many were already answered, for the "2 of 3".
    private var walkQueue: List<Long>? = null
    private val walked = mutableStateOf(0)
    private var saving = false
    // Bumped by each (re)start: a walk's pending step must not steer a picker re-targeted meanwhile.
    private var walkGen = 0

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(newBase.withLatinDigits())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initial = PickerArgs.from(intent)
        if (initial == null) {
            finish()
            return
        }
        currentArgs.value = initial
        startWalk(intent)
        // Recreated (rotation, dark mode) mid-walk: resume where it was, not at the recap's first row.
        savedInstanceState?.getLongArray(KEY_WALK_QUEUE)?.let { queue ->
            walkQueue = queue.toList()
            walked.value = savedInstanceState.getInt(KEY_WALKED)
            queue.firstOrNull()?.let { currentArgs.value = PickerArgs(it, it.toInt()) }
        }

        if (savedInstanceState?.getBoolean(KEY_UNLOCKED) == true) {
            unlocked.value = true
        } else if (savedInstanceState == null) {
            checkLock()
        } else {
            // Recreated mid-prompt: start over from the notification.
            finish()
            return
        }

        setContent {
            PennyWiseUserTheme {
                val args by currentArgs
                if (unlocked.value) args?.let { PickerHost(it) }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, unlocked.value)
        walkQueue?.let {
            outState.putLongArray(KEY_WALK_QUEUE, it.toLongArray())
            outState.putInt(KEY_WALKED, walked.value)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask routes second-tap intents here. Update the args so the
        // Compose tree re-keys produceState onto the new transactionId; the
        // bottom-sheet keeps its surface and just refreshes its content.
        setIntent(intent)
        PickerArgs.from(intent)?.let {
            currentArgs.value = it
            startWalk(intent)
        }
        // A picker left open in the background must not show the next
        // transaction after the lock has timed out: hide it and ask again.
        unlocked.value = false
        checkLock()
    }

    private fun startWalk(intent: Intent) {
        walkQueue = intent.getLongArrayExtra(EXTRA_WALK_IDS)?.toList()
        walked.value = 0
        saving = false
        walkGen++
    }

    /**
     * Runs [save] for the shown transaction, then closes the picker, or in walk
     * mode moves on to the next row that is still unsorted once the write has
     * landed. The write runs on the app scope so it survives a closed picker.
     */
    private fun answer(txnId: Long, save: suspend () -> Unit) {
        val queue = walkQueue
        if (queue == null) {
            appScope.launch { runCatching { save() }.onFailure { Log.e(TAG, "Error saving the pick", it) } }
            finish()
            return
        }
        if (saving) return
        saving = true
        val gen = walkGen
        lifecycleScope.launch {
            val saved = appScope.async {
                runCatching { save() }.onFailure { Log.e(TAG, "Error saving the pick", it) }.isSuccess
            }.await()
            if (gen != walkGen) return@launch
            if (!saved) {
                // The row stays on screen, still unsorted, for another try.
                saving = false
                android.widget.Toast.makeText(this@QuickCategoryPickerActivity, R.string.quick_category_save_failed, android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            advance(gen, txnId, answered = true)
        }
    }

    /**
     * Moves the walk past [txnId] to the next row still unsorted (re-read, since
     * learning a merchant can sort others), or ends it. [answered] counts it toward
     * the "2 of 3"; a row skipped for being stale ([PickerHost]) doesn't count.
     */
    private suspend fun advance(gen: Int, txnId: Long, answered: Boolean) {
        val queue = walkQueue ?: return
        val left = stillUnsorted(
            queue.filter { it != txnId }.mapNotNull { transactionRepository.getTransactionById(it) },
            BuiltinCategoryNames.current("Others")
        )
        // Re-targeted by another notification during the reads: the old walk stops here.
        if (gen != walkGen) return
        walkQueue = left.map { it.id }
        if (answered) walked.value += 1
        saving = false
        val next = left.firstOrNull()
        if (next == null) {
            // All of today's are answered: the recap's "N need a category" is stale.
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(DailySummaryWorker.NOTIFICATION_ID)
            finish()
        } else {
            currentArgs.value = PickerArgs(next.id, next.id.toInt())
        }
    }

    /** Shows the sheet only once the app lock (if on) is satisfied; a cancel closes the picker. */
    private fun checkLock() {
        lifecycleScope.launch {
            if (!appLockRepository.shouldLockApp()) {
                unlocked.value = true
                return@launch
            }
            biometricAuthManager.authenticate(
                activity = this@QuickCategoryPickerActivity,
                onSuccess = {
                    lifecycleScope.launch { appLockRepository.updateAuthTimestamp() }
                    unlocked.value = true
                },
                onError = { finish() },
                onCancel = { finish() }
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun PickerHost(args: PickerArgs) {
        // Explicit "loaded" flags so we can tell "still resolving" apart from
        // "resolved as null/empty" — important because the activity otherwise
        // leaves a blank transparent window on screen when data is missing.
        var transaction by remember(args.transactionId) { mutableStateOf<TransactionEntity?>(null) }
        var transactionLoaded by remember(args.transactionId) { mutableStateOf(false) }
        var categories by remember { mutableStateOf<List<CategoryEntity>>(emptyList()) }
        var categoriesLoaded by remember { mutableStateOf(false) }
        // Tags for the inline tag section (#696): the txn's current tags seed
        // the field, and every known tag name feeds autocomplete.
        var initialTags by remember(args.transactionId) { mutableStateOf<List<String>>(emptyList()) }
        var tagsLoaded by remember(args.transactionId) { mutableStateOf(false) }
        var tagSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }

        LaunchedEffect(args.transactionId) {
            val loaded = transactionRepository.getTransactionById(args.transactionId)
            // Walk: a row deleted, sorted or excluded since the recap (or answered just
            // before a rotation) is skipped, not shown, and a missing one doesn't end the walk.
            if (walkQueue != null && stillUnsorted(listOfNotNull(loaded), BuiltinCategoryNames.current("Others")).isEmpty()) {
                advance(walkGen, args.transactionId, answered = false)
                return@LaunchedEffect
            }
            transaction = loaded
            transactionLoaded = true
            // Load existing tags BEFORE the sheet renders (gated by tagsLoaded below).
            // Otherwise the sheet's remember{} captures an empty list, and the first
            // add/remove would replace-all with an incomplete set, deleting existing
            // tag associations. (#710 Greptile)
            initialTags = tagRepository.getTagNamesForTransaction(args.transactionId)
            tagsLoaded = true
        }
        LaunchedEffect(Unit) {
            categories = categoryRepository.getVisibleCategories().first()
            categoriesLoaded = true
            tagSuggestions = tagRepository.observeAllTagNames().first()
        }

        // Finish silently when the load resolves to nothing renderable.
        LaunchedEffect(transactionLoaded, categoriesLoaded) {
            if (transactionLoaded && categoriesLoaded &&
                (transaction == null || categories.isEmpty())
            ) {
                Log.w(TAG, "Picker has no transaction or no categories — finishing.")
                finish()
            }
        }

        val txn = transaction
        if (txn != null && categories.isNotEmpty() && tagsLoaded) {
            // Snapshot the args this composition is targeting so the callback
            // doesn't accidentally see an args update from onNewIntent mid-flight.
            val activeNotificationId = args.notificationId
            val isIncome = txn.transactionType == TransactionType.INCOME
            val total = walkQueue?.let { walked.value + it.size } ?: 0
            QuickCategoryPickerSheet(
                currentCategory = txn.category,
                // #876: only the transaction's own type, like every other picker.
                categories = categories.forPicker(isIncome = isIncome),
                onCategorySelected = { newCategory ->
                    // Re-tapping the ticked row is "leave it", not a lesson (in a walk: skip it).
                    if (newCategory == txn.category) {
                        if (walkQueue == null) finish() else answer(txn.id) {}
                        return@QuickCategoryPickerSheet
                    }
                    answer(txn.id) {
                        learnCategory(txn.id, newCategory) { learned ->
                            SmsBroadcastReceiver.showLearnedNotification(applicationContext, activeNotificationId, learned)
                        }
                        WidgetRefresher.refreshTransactionWidgets(applicationContext)
                    }
                },
                onDismiss = { finish() },
                subject = "${txn.merchantName.ifBlank { stringResource(R.string.notif_txn_unknown_merchant) }} · ${CurrencyFormatter.formatCurrency(txn.amount, txn.currency)}",
                progress = if (total > 1) stringResource(R.string.quick_category_progress, walked.value + 1, total) else null,
                excludeLabel = stringResource(if (isIncome) R.string.quick_category_not_income else R.string.quick_category_not_expense),
                onExclude = {
                    answer(txn.id) {
                        transactionRepository.setExcludedFromAnalytics(txn.id, true)
                        WidgetRefresher.refreshTransactionWidgets(applicationContext)
                        // Nothing was learned, so the alert just clears, as for a pick on a vanished row.
                        SmsBroadcastReceiver.showLearnedNotification(applicationContext, activeNotificationId, null)
                    }
                },
                tagSuggestions = tagSuggestions,
                initialTags = initialTags,
                onTagsChanged = { newTags ->
                    // Persist immediately so tagging works even if the user
                    // dismisses without picking a category (#696). Enqueue rather
                    // than write directly so rapid edits apply FIFO on a single
                    // consumer — no reorder, and no loss when the picker retargets
                    // to another transaction via onNewIntent. (#710)
                    tagRepository.enqueueSetTags(txn.id, newTags)
                }
            )
        }
    }

    private data class PickerArgs(val transactionId: Long, val notificationId: Int) {
        companion object {
            fun from(intent: Intent?): PickerArgs? {
                intent ?: return null
                val txnId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
                if (txnId == -1L) return null
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                return PickerArgs(txnId, notifId)
            }
        }
    }

    companion object {
        private const val TAG = "QuickCategoryPicker"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        /** Walk mode: the recap's unsorted transaction ids, newest first. */
        const val EXTRA_WALK_IDS = "walk_ids"
        private const val KEY_UNLOCKED = "unlocked"
        private const val KEY_WALK_QUEUE = "walk_queue"
        private const val KEY_WALKED = "walked"

        /** The walk's rows still waiting, in order: sorted (perhaps by a merchant just learned), excluded or deleted ones drop out. */
        internal fun stillUnsorted(rows: List<TransactionEntity>, others: String): List<TransactionEntity> =
            rows.filter { it.category == others && !it.excludedFromAnalytics && !it.isDeleted }

        /** Opens the picker on [ids]' first and walks the rest; each alert's id is its transaction's. */
        fun walkIntent(context: Context, ids: List<Long>, requestCode: Int): PendingIntent =
            PendingIntent.getActivity(
                context,
                requestCode,
                Intent(context, QuickCategoryPickerActivity::class.java).apply {
                    putExtra(EXTRA_TRANSACTION_ID, ids.first())
                    putExtra(EXTRA_NOTIFICATION_ID, ids.first().toInt())
                    putExtra(EXTRA_WALK_IDS, ids.toLongArray())
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
