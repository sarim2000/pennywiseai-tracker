package com.pennywiseai.tracker.receiver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.core.app.NotificationManagerCompat
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.ui.components.QuickCategoryPickerSheet
import com.pennywiseai.tracker.ui.theme.PennyWiseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Translucent activity launched from the txn-alert notification's
 * "More categories…" action (#303). Hosts the shared
 * [QuickCategoryPickerSheet] so the user can pick any category — not just
 * the 3 quick-picks rendered as inline notification buttons — without
 * navigating into the full transaction-detail screen.
 *
 * On pick: update the transaction's category, dismiss the originating
 * notification, finish. On dismiss without picking: finish.
 */
@AndroidEntryPoint
class QuickCategoryPickerActivity : ComponentActivity() {

    @Inject lateinit var transactionRepository: TransactionRepository
    @Inject lateinit var categoryRepository: CategoryRepository

    // App-scoped scope for the category update — we don't want the write to
    // be cancelled if the activity finishes (it does, immediately after).
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (transactionId == -1L) {
            finish()
            return
        }

        setContent {
            PennyWiseTheme {
                // Load the transaction + categories asynchronously. Sheet
                // doesn't render until both are available, which keeps the
                // activity instant — the user just sees the system scrim
                // first, then the sheet slides up.
                val transaction by produceState<com.pennywiseai.tracker.data.database.entity.TransactionEntity?>(
                    initialValue = null,
                    key1 = transactionId
                ) {
                    value = transactionRepository.getTransactionById(transactionId)
                }
                val categories by produceState(
                    initialValue = emptyList<com.pennywiseai.tracker.data.database.entity.CategoryEntity>()
                ) {
                    value = categoryRepository.getAllCategories().first()
                }

                val txn = transaction
                if (txn != null && categories.isNotEmpty()) {
                    QuickCategoryPickerSheet(
                        currentCategory = txn.category,
                        categories = categories,
                        onCategorySelected = { newCategory ->
                            if (newCategory != txn.category) {
                                ioScope.launch {
                                    transactionRepository.updateCategory(txn.id, newCategory)
                                }
                            }
                            if (notificationId != -1) {
                                NotificationManagerCompat.from(applicationContext)
                                    .cancel(notificationId)
                            }
                            finish()
                        },
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
