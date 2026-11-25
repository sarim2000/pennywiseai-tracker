package com.pennywiseai.tracker.widget

import com.pennywiseai.tracker.domain.smsimport.LastScanStatus
import com.pennywiseai.tracker.domain.smsimport.SmsScanRecord
import com.pennywiseai.tracker.domain.smsimport.lastScanStatus
import com.pennywiseai.tracker.utils.BackgroundAccessState
import com.pennywiseai.tracker.utils.TrackingProblem
import com.pennywiseai.tracker.utils.trackingProblem

/** What the tracking check-up widget leads with, worst first. */
enum class TrackingHealth {
    SMS_ACCESS_OFF,
    BACKGROUND_RESTRICTED,

    /** The last scan ended early or couldn't save everything it read. */
    MESSAGES_MAY_BE_MISSING,
    SCANNING,
    ON;

    val needsFix: Boolean get() = this != SCANNING && this != ON
}

/** [lastReadAt]: when PennyWise last read SMS, by a clean scan or a live message. */
data class TrackingCheckup(val health: TrackingHealth, val lastReadAt: Long?)

/**
 * The SMS check-up's verdict in one line, by the same rules: only what stops tracking
 * counts ([trackingProblem]; battery optimisation doesn't), then the last scan
 * ([lastScanStatus]). [scanRunning] is WorkManager's word, as for the check-up.
 */
fun trackingCheckup(
    smsGranted: Boolean,
    backgroundRestricted: Boolean?,
    record: SmsScanRecord?,
    scanRunning: Boolean,
    lastLiveSmsAt: Long?,
): TrackingCheckup {
    val scan = lastScanStatus(record, scanRunning)
    val health = when (trackingProblem(smsGranted, BackgroundAccessState(backgroundRestricted = backgroundRestricted))) {
        TrackingProblem.SMS_ACCESS -> TrackingHealth.SMS_ACCESS_OFF
        TrackingProblem.BACKGROUND_RESTRICTED -> TrackingHealth.BACKGROUND_RESTRICTED
        null -> when {
            scan == LastScanStatus.RUNNING -> TrackingHealth.SCANNING
            scan.needsAttention -> TrackingHealth.MESSAGES_MAY_BE_MISSING
            else -> TrackingHealth.ON
        }
    }
    // Scans run when the app opens; between them, live SMS are what show tracking works.
    return TrackingCheckup(health, listOfNotNull(record?.lastSucceededAt, lastLiveSmsAt).maxOrNull())
}
