package com.pennywiseai.tracker.receiver

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.AppLockRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.di.ApplicationScope
import com.pennywiseai.tracker.domain.security.BiometricAuthManager
import com.pennywiseai.tracker.domain.usecase.LearnCategoryUseCase
import com.pennywiseai.tracker.ui.components.QuickCategoryPickerSheet
import com.pennywiseai.tracker.ui.components.forPicker
import com.pennywiseai.tracker.widget.WidgetRefresher
import com.pennywiseai.tracker.ui.theme.PennyWiseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
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
 * On dismiss without picking: finish.
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initial = PickerArgs.from(intent)
        if (initial == null) {
            finish()
            return
        }
        currentArgs.value = initial

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
            PennyWiseTheme {
                val args by currentArgs
                if (unlocked.value) args?.let { PickerHost(it) }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, unlocked.value)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask routes second-tap intents here. Update the args so the
        // Compose tree re-keys produceState onto the new transactionId; the
        // bottom-sheet keeps its surface and just refreshes its content.
        setIntent(intent)
        PickerArgs.from(intent)?.let { currentArgs.value = it }
        // A picker left open in the background must not show the next
        // transaction after the lock has timed out: hide it and ask again.
        unlocked.value = false
        checkLock()
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
            transaction = transactionRepository.getTransactionById(args.transactionId)
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
            QuickCategoryPickerSheet(
                currentCategory = txn.category,
                // #876: only the transaction's own type, like every other picker.
                categories = categories.forPicker(isIncome = txn.transactionType == TransactionType.INCOME),
                onCategorySelected = { newCategory ->
                    // Re-tapping the ticked row is "leave it", not a lesson.
                    if (newCategory == txn.category) {
                        finish()
                        return@QuickCategoryPickerSheet
                    }
                    appScope.launch {
                        runCatching {
                            learnCategory(txn.id, newCategory) { learned ->
                                SmsBroadcastReceiver.showLearnedNotification(applicationContext, activeNotificationId, learned)
                            }
                            WidgetRefresher.refreshTransactionWidgets(applicationContext)
                        }.onFailure { Log.e(TAG, "Error saving the picked category", it) }
                    }
                    finish()
                },
                onDismiss = { finish() },
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
        private const val KEY_UNLOCKED = "unlocked"
    }
}
