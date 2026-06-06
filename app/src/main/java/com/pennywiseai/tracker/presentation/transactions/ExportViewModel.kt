package com.pennywiseai.tracker.presentation.transactions

import androidx.lifecycle.ViewModel
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.export.CsvExporter
import com.pennywiseai.tracker.data.export.ExportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val csvExporter: CsvExporter,
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
}