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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
     * cap from this, and [exportTransactions] re-checks it when the export runs.
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

    /** Re-reads [csvAccess] now, for the dialog coming back to the foreground. */
    fun recheckTrial() = featureTrials.recheck()

    /**
     * Exports [transactions]: all of them while unlocked, else the first ones up to the
     * cap. The cap is read when the export starts running, not from [csvAccess] or when
     * this flow was made: a trial may have ended while the dialog was open.
     */
    fun exportTransactions(
        transactions: List<TransactionEntity>,
        fileName: String? = null
    ): Flow<ExportResult> = flow {
        val unlimited = entitlementGate.isProEntitled.value || featureTrials.isActive(TrialFeature.CSV_EXPORT)
        val rows = if (unlimited) transactions else transactions.take(FreeTierLimits.MAX_CSV_EXPORT_ROWS_PER_MONTH)
        emitAll(csvExporter.exportTransactions(rows, fileName))
    }

    /** @see BillingPreferences.claimSupportNudge — global, frequency-capped. */
    suspend fun claimSupportNudge(): Boolean = userPreferencesRepository.claimSupportNudge()
}
