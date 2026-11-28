package com.pennywiseai.tracker.data.receipt

import android.net.Uri
import javax.inject.Inject

/** F-Droid: no OCR model (ML Kit is proprietary), so the Add form offers no "Scan receipt". */
class NoReceiptTextRecognizer @Inject constructor() : ReceiptTextRecognizer {
    override val isAvailable: Boolean = false

    override suspend fun recognize(uri: Uri): List<String>? = null
}
