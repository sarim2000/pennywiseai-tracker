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
    val lastLiveSmsAt: Long?,
    val notImported: NotImported,
)

/** What the Not imported card says. */
sealed interface NotImported {
    /** Messages in an unknown format the Unrecognized SMS list still shows. */
    data class Waiting(val count: Int) : NotImported

    /**
     * The list is empty, but the last scan did find unknown formats: ones already dealt
     * with, or past the list's 30 days. Saying "every message was read" would be false.
     */
    data object NoneWaiting : NotImported

    /** The list is empty and the last scan found no unknown formats. */
    data object AllRead : NotImported
}

/**
 * [waiting] is what the Unrecognized SMS list shows; [lastScan] is what the latest scan
 * counted, which also includes messages dismissed, added or aged out of that list.
 */
fun notImported(waiting: Int, lastScan: SmsScanRecord?): NotImported = when {
    waiting > 0 -> NotImported.Waiting(waiting)
    (lastScan?.counts?.unrecognised ?: 0) > 0 -> NotImported.NoneWaiting
    else -> NotImported.AllRead
}

@HiltViewModel
class SmsCheckupViewModel @Inject constructor(
    scanPreferences: ScanPreferences,
    unrecognizedSmsRepository: UnrecognizedSmsRepository,
    private val smsScanManager: SmsScanManager,
) : ViewModel() {

    /** Null until every source has answered. */
    val state: StateFlow<SmsCheckupState?> = combine(
        scanPreferences.lastScanRecord,
        scanPreferences.lastLiveSmsAt,
        unrecognizedSmsRepository.getVisibleCount(),
        smsScanManager.isScanRunning(),
    ) { record, lastLiveSmsAt, waiting, running ->
        SmsCheckupState(lastScanStatus(record, running), record, lastLiveSmsAt, notImported(waiting, record))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Re-reads the whole scan window: adds what was missed, keeps every edit. */
    fun rescan() = smsScanManager.startSmsLoggingScan(rescanAll = true)

    /** Access came back: catch up on what arrived while it was off, as Home does. */
    fun onSmsAccessGranted() = smsScanManager.startSmsLoggingScan()
}
