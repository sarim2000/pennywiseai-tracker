package com.pennywiseai.tracker.receiver

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.di.ApplicationScope
import com.pennywiseai.tracker.ui.components.QuickCategoryPickerSheet
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
 * On pick: update the transaction's category, dismiss the originating
 * notification, finish. On dismiss without picking: finish.
 */
@AndroidEntryPoint
class QuickCategoryPickerActivity : ComponentActivity() {

    @Inject lateinit var transactionRepository: TransactionRepository
    @Inject lateinit var categoryRepository: CategoryRepository

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

        setContent {
            PennyWiseTheme {
                val args by currentArgs
                args?.let { PickerHost(it) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask routes second-tap intents here. Update the args so the
        // Compose tree re-keys produceState onto the new transactionId; the
        // bottom-sheet keeps its surface and just refreshes its content.
        setIntent(intent)
        PickerArgs.from(intent)?.let { currentArgs.value = it }
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

        LaunchedEffect(args.transactionId) {
            transaction = transactionRepository.getTransactionById(args.transactionId)
            transactionLoaded = true
        }
        LaunchedEffect(Unit) {
            categories = categoryRepository.getAllCategories().first()
            categoriesLoaded = true
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
        if (txn != null && categories.isNotEmpty()) {
            // Snapshot the args this composition is targeting so the callback
            // doesn't accidentally see an args update from onNewIntent mid-flight.
            val activeNotificationId = args.notificationId
            QuickCategoryPickerSheet(
                currentCategory = txn.category,
                categories = categories,
                onCategorySelected = { newCategory ->
                    if (newCategory != txn.category) {
                        appScope.launch {
                            transactionRepository.updateCategory(txn.id, newCategory)
                        }
                    }
                    if (activeNotificationId != -1) {
                        NotificationManagerCompat.from(applicationContext)
                            .cancel(activeNotificationId)
                    }
                    finish()
                },
                onDismiss = { finish() }
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
    }
}
