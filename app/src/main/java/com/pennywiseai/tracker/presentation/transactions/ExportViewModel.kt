package com.pennywiseai.tracker.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.export.CsvExporter
import com.pennywiseai.tracker.data.export.ExportResult
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
     * True when the contextual "Support development" nudge hasn't been shown in
     * the last [NUDGE_COOLDOWN_DAYS] days. The caller additionally gates this on
     * the F-Droid flavor — Play builds sell Pro instead of asking for tips.
     */
    suspend fun isSupportNudgeDue(): Boolean {
        val last = userPreferencesRepository.supportNudgeLastShownDay.first()
        return LocalDate.now().toEpochDay() - last >= NUDGE_COOLDOWN_DAYS
    }

    fun recordSupportNudgeShown() {
        viewModelScope.launch {
            userPreferencesRepository.setSupportNudgeLastShownDay(LocalDate.now().toEpochDay())
        }
    }

    private companion object {
        const val NUDGE_COOLDOWN_DAYS = 30L
    }
}
