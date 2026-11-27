package com.pennywiseai.tracker.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.core.entitlement.FeatureAccess
import com.pennywiseai.tracker.core.entitlement.FeatureTrials
import com.pennywiseai.tracker.core.entitlement.FreeTierLimits
import com.pennywiseai.tracker.core.entitlement.TrialFeature
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.export.CsvExporter
import com.pennywiseai.tracker.data.export.ExportResult
import com.pennywiseai.tracker.data.preferences.BillingPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val csvExporter: CsvExporter,
    private val userPreferencesRepository: BillingPreferences,
    private val entitlementGate: EntitlementGate,
    private val featureTrials: FeatureTrials,
) : ViewModel() {

    /**
     * Pro, or the CSV trial running, exports every row. Otherwise an export is capped
     * at [FreeTierLimits.MAX_CSV_EXPORT_ROWS_PER_MONTH] rows; the dialog shows the
     * cap from this, and [rowsToExport] re-checks it when the export starts.
     */
    val csvAccess: StateFlow<FeatureAccess> = featureTrials
        .access(TrialFeature.CSV_EXPORT, entitlementGate.resolvedProEntitlement)
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            FeatureAccess(entitlementGate.resolvedProEntitlement.value, featureTrials.status(TrialFeature.CSV_EXPORT)),
        )

    /** Starts the CSV export's one trial, from the upgrade sheet, if the user is known to be free. */
    fun startCsvTrial() =
        featureTrials.start(TrialFeature.CSV_EXPORT, entitlementGate.resolvedProEntitlement.value)

    /** The rows an export started now writes: all of them while unlocked, else the first ones up to the cap. */
    fun <T> rowsToExport(rows: List<T>): List<T> {
        val unlimited = entitlementGate.isProEntitled.value || featureTrials.isActive(TrialFeature.CSV_EXPORT)
        return if (unlimited) rows else rows.take(FreeTierLimits.MAX_CSV_EXPORT_ROWS_PER_MONTH)
    }

    fun exportTransactions(
        transactions: List<TransactionEntity>,
        fileName: String? = null
    ): Flow<ExportResult> {
        return csvExporter.exportTransactions(transactions, fileName)
    }

    /** @see BillingPreferences.claimSupportNudge — global, frequency-capped. */
    suspend fun claimSupportNudge(): Boolean = userPreferencesRepository.claimSupportNudge()
}
