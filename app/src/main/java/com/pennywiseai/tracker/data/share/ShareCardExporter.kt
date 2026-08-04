package com.pennywiseai.tracker.data.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.pennywiseai.tracker.data.share.ShareHero
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

    /**
     * How long an exported card is kept. Long enough that one still sitting in a chat
     * draft remains readable; short enough that the directory can't grow without bound.
     */
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    /**
     * Persists [bitmap] under a fresh name and returns a content:// URI other apps can read.
     *
     * Each export gets its own file, deliberately. Reusing one fixed path looks tidier and
     * bounds the cache for free, which is why the first version did it — but the read
     * permission granted with a share outlives the share itself. Someone who picks a chat
     * and leaves the image in a draft still holds that URI, so writing the next card over
     * the same path swaps the image under them and sends a later recap to an earlier
     * recipient. Deleting the old file instead would only turn that into a broken
     * attachment.
     *
     * Growth is therefore bounded by age, not by overwriting, so the file just handed to
     * another app is never the one reclaimed.
     */
    suspend fun writeCard(context: Context, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val now = System.currentTimeMillis()

        dir.listFiles()
            ?.filter { now - it.lastModified() > MAX_AGE_MS }
            ?.forEach { it.delete() }

        // createTempFile rather than a timestamped name: two exports racing (a double-tap
        // on Share) can land in the same millisecond, and a name collision would put the
        // second card under the first share's URI — the exact overwrite documented above.
        val file = File.createTempFile("pennywise-recap-", ".png", dir)
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
        // Follows whichever figure the card leads with, so the words and the image agree.
        val heroIsSubscriptions =
            config.hero == ShareHero.SUBSCRIPTIONS && subscriptions > 0
        val lead = if (heroIsSubscriptions) {
            val subs = if (subscriptions == 1) "1 subscription" else "$subscriptions subscriptions"
            "PennyWise found $subs I'd forgotten I was paying for."
        } else {
            "PennyWise tracked $transactions transactions for me without me typing in a single one."
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
