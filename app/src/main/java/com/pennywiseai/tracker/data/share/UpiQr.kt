package com.pennywiseai.tracker.data.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A split-bill pay request (#346) as a QR image plus text, for the system
 * share sheet. The QR is drawn on the phone (zxing core); nothing goes online.
 */
object UpiQr {

    private const val SIZE_PX = 768

    /** Black on white whatever the app theme: scanners need the contrast. */
    fun encode(text: String, sizePx: Int = SIZE_PX): Bitmap {
        val matrix = QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, sizePx, sizePx,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 2)
        )
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix[i % matrix.width, i / matrix.width]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }

    /** Share sheet with the QR for [payUri] and [text]. Reuses the share-card cache and FileProvider. */
    suspend fun shareIntent(context: Context, payUri: String, text: String, title: String): Intent {
        val bitmap = withContext(Dispatchers.Default) { encode(payUri) }
        val uri = ShareCardExporter.writeCard(context, bitmap)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title)
    }
}
