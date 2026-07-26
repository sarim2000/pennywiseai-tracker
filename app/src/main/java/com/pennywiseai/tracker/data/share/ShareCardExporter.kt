package com.pennywiseai.tracker.data.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.pennywiseai.tracker.ui.components.SHARE_CARD_URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Writes a rendered share card to disk and hands it to the system share sheet.
 *
 * The bitmap is produced on-device from Compose and written to the app's own cache —
 * it never touches a network. That is the whole reason the card can honestly say
 * "the details never left my phone".
 *
 * Reuses the FileProvider already declared for CSV export
 * (`${applicationId}.fileprovider`); `share_cards/` is registered in `res/xml/file_paths.xml`.
 */
object ShareCardExporter {

    private const val DIR = "share_cards"
    private const val FILE = "pennywise-share-card.png"

    /**
     * Persists [bitmap] and returns a content:// URI other apps can read.
     *
     * Always overwrites the single file rather than accumulating one per share — the
     * card is disposable, and a cache directory that grows every time someone taps
     * Share is a slow leak nobody would ever notice.
     */
    suspend fun writeCard(context: Context, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val file = File(dir, FILE)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * The message that travels with the image.
     *
     * Deliberately written in the sharer's voice, not the app's — this lands in a
     * WhatsApp thread between friends, and marketing copy in that context gets ignored.
     * The lead sentence follows whichever section the user put first, so the words match
     * the card rather than always talking about subscriptions. The URL closes the loop
     * back to the landing pages.
     */
    fun shareText(config: ShareCardConfig, transactions: Int, subscriptions: Int): String {
        val lead = when {
            config.showTransactions ->
                "PennyWise tracked $transactions transactions for me without me typing in a single one."
            config.showSubscriptions -> {
                val subs = if (subscriptions == 1) "1 subscription" else "$subscriptions subscriptions"
                "PennyWise found $subs I'd forgotten I was paying for."
            }
            else ->
                "PennyWise sorted my spending into categories automatically."
        }
        return "$lead It reads bank SMS on your phone — nothing gets uploaded. " +
            "https://$SHARE_CARD_URL"
    }

    fun shareIntent(
        uri: Uri,
        config: ShareCardConfig,
        transactions: Int,
        subscriptions: Int,
    ): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, shareText(config, transactions, subscriptions))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null)
    }
}
