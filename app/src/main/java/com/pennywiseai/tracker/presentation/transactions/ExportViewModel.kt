package com.pennywiseai.tracker.presentation.transactions

import androidx.lifecycle.ViewModel
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.export.CsvExporter
import com.pennywiseai.tracker.data.export.ExportResult
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val csvExporter: CsvExporter,
    private val userPreferencesRepository: UserPreferencesRepository,
    entitlementGate: EntitlementGate,
) : ViewModel() {

    /**
     * Pro entitlement. Free users are capped at
     * [com.pennywiseai.tracker.billing.FreeTierLimits.MAX_CSV_EXPORT_ROWS_PER_MONTH]
     * rows per export; the dialog checks this before calling
     * [exportTransactions].
     */
    val isProEntitled: StateFlow<Boolean> = entitlementGate.isProEntitled

    fun exportTransactions(
        transactions: List<TransactionEntity>,
        fileName: String? = null
    ): Flow<ExportResult> {
        return csvExporter.exportTransactions(transactions, fileName)
    }

    /**
     * Atomically decides whether to show the contextual "Support development"
     * nudge and, if so, records it as shown before returning — so back-to-back
     * exports can't slip through and show it twice. Returns true at most once
     * per [NUDGE_COOLDOWN_DAYS] days. The caller additionally gates this on the
     * F-Droid flavor — Play builds sell Pro instead of asking for tips.
     */
    suspend fun claimSupportNudge(): Boolean {
        val today = LocalDate.now().toEpochDay()
        val last = userPreferencesRepository.supportNudgeLastShownDay.first()
        if (today - last < NUDGE_COOLDOWN_DAYS) return false
        // Await the write before returning true so a subsequent claim reads the
        // updated timestamp rather than the stale one.
        userPreferencesRepository.setSupportNudgeLastShownDay(today)
        return true
    }

    private companion object {
        const val NUDGE_COOLDOWN_DAYS = 30L
    }
}
