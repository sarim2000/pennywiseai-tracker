package com.pennywiseai.tracker.data.receipt

import android.net.Uri

/**
 * Reads the text on a receipt photo, on the device. The standard build binds ML Kit's
 * bundled Latin model; the F-Droid build can't ship it, binds a stub, and hides
 * "Scan receipt" ([isAvailable] false).
 */
interface ReceiptTextRecognizer {
    val isAvailable: Boolean

    /** The photo's text lines, top to bottom; null when it can't be read or OCR isn't available. */
    suspend fun recognize(uri: Uri): List<String>?
}
