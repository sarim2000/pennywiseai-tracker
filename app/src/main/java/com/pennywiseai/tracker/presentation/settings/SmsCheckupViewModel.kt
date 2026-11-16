package com.pennywiseai.tracker.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.preferences.ScanPreferences
import com.pennywiseai.tracker.data.repository.UnrecognizedSmsRepository
import com.pennywiseai.tracker.domain.smsimport.LastScanStatus
import com.pennywiseai.tracker.domain.smsimport.SmsScanRecord
import com.pennywiseai.tracker.domain.smsimport.lastScanStatus
import com.pennywiseai.tracker.worker.SmsScanManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The scan side of the SMS check-up; permissions and background state are read on screen. */
data class SmsCheckupState(
    val status: LastScanStatus,
    val record: SmsScanRecord?,
    /** The incremental-scan cursor: when a scan last finished successfully. */
    val lastSuccessAt: Long?,
    val lastLiveSmsAt: Long?,
    val notImported: Int,
)

@HiltViewModel
class SmsCheckupViewModel @Inject constructor(
    scanPreferences: ScanPreferences,
    unrecognizedSmsRepository: UnrecognizedSmsRepository,
    private val smsScanManager: SmsScanManager,
) : ViewModel() {

    /** Null until every source has answered. */
    val state: StateFlow<SmsCheckupState?> = combine(
        scanPreferences.lastScanRecord,
        scanPreferences.getLastScanTimestamp(),
        scanPreferences.lastLiveSmsAt,
        unrecognizedSmsRepository.getVisibleCount(),
        smsScanManager.isScanRunning(),
    ) { record, lastSuccessAt, lastLiveSmsAt, notImported, running ->
        SmsCheckupState(lastScanStatus(record, running), record, lastSuccessAt, lastLiveSmsAt, notImported)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Re-reads the whole scan window: adds what was missed, keeps every edit. */
    fun rescan() = smsScanManager.startSmsLoggingScan(rescanAll = true)
}
