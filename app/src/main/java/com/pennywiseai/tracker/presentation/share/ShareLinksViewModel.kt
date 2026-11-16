package com.pennywiseai.tracker.presentation.share

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.share.ShareLinkClient
import com.pennywiseai.tracker.data.share.ShareLinkError
import com.pennywiseai.tracker.data.share.ShareLinkExpiry
import com.pennywiseai.tracker.data.share.ShareLinkStore
import com.pennywiseai.tracker.data.share.ShareSnapshot
import com.pennywiseai.tracker.data.share.SharedLink
import com.pennywiseai.tracker.data.share.live
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

sealed interface PublishState {
    data object Idle : PublishState

    data object Publishing : PublishState

    /** Uploaded and saved; the screen hands [link] to the share sheet, then calls [ShareLinksViewModel.publishHandled]. */
    data class Published(val link: SharedLink) : PublishState

    data class Failed(val error: ShareLinkError) : PublishState
}

/** The outcome of a delete, said once in a snackbar. */
sealed interface DeleteOutcome {
    data object Deleted : DeleteOutcome

    data class Failed(val error: ShareLinkError) : DeleteOutcome
}

/** Publishing a card as a link, and the list of links this phone made. */
@HiltViewModel
class ShareLinksViewModel @Inject constructor(
    private val client: ShareLinkClient,
    private val store: ShareLinkStore,
) : ViewModel() {

    /** Links still up, newest first; null until read. Expired ones are gone from the server already. */
    val links: StateFlow<List<SharedLink>?> = store.links
        .map { it.live(Instant.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _publish = MutableStateFlow<PublishState>(PublishState.Idle)
    val publish: StateFlow<PublishState> = _publish.asStateFlow()

    private val _deleting = MutableStateFlow<Set<String>>(emptySet())
    val deleting: StateFlow<Set<String>> = _deleting.asStateFlow()

    private val _deleteOutcomes = Channel<DeleteOutcome>(Channel.BUFFERED)
    val deleteOutcomes: Flow<DeleteOutcome> = _deleteOutcomes.receiveAsFlow()

    /**
     * Uploads [content] and its link-preview image [og] (1200×630). Runs only from the
     * user's tap on the sheet that shows both; a second tap while one is on its way is
     * ignored.
     */
    fun publish(content: ShareCardContent, theme: ShareTheme, locale: Locale, expiry: ShareLinkExpiry, og: Bitmap) {
        if (_publish.value == PublishState.Publishing) return
        _publish.value = PublishState.Publishing
        viewModelScope.launch {
            _publish.value = try {
                val png = withContext(Dispatchers.Default) { og.toOgPng() }
                val snapshot = ShareSnapshot.encode(content.toSnapshot(theme, locale, expiry))
                // Once sent, finished even if the screen is left: a page the server made
                // but whose delete key was never saved could not be taken down from here.
                val link = withContext(NonCancellable) {
                    val created = client.publish(snapshot, png)
                    val link = SharedLink(
                        id = created.id,
                        url = created.url,
                        deleteToken = created.deleteToken,
                        expiresAt = created.expiresAt,
                        title = content.title,
                        kind = content.kind.name,
                    )
                    try {
                        store.add(link)
                    } catch (e: IOException) {
                        // Unsaved, its delete key would be gone with this screen: take the page
                        // down while the key is still in hand. Should that fail too, the page
                        // stays unseen: nobody has its address.
                        try {
                            client.delete(created.id, created.deleteToken)
                        } catch (rollback: ShareLinkError) {
                            // Reported as the storage failure below.
                        }
                        throw ShareLinkError.Storage
                    }
                    link
                }
                PublishState.Published(link)
            } catch (e: ShareLinkError) {
                PublishState.Failed(e)
            }
        }
    }

    /** Back to idle once the screen has shared the link, or the user has read an error. */
    fun publishHandled() {
        _publish.update { if (it == PublishState.Publishing) it else PublishState.Idle }
    }

    fun delete(link: SharedLink) {
        if (link.id in _deleting.value) return
        _deleting.update { it + link.id }
        viewModelScope.launch {
            val outcome = try {
                client.delete(link.id, link.deleteToken)
                store.remove(link.id)
                DeleteOutcome.Deleted
            } catch (e: ShareLinkError) {
                DeleteOutcome.Failed(e)
            } catch (e: IOException) {
                // The page is down; the row stays, and a second delete clears it (a missing page counts as deleted).
                DeleteOutcome.Failed(ShareLinkError.Storage)
            } finally {
                _deleting.update { it - link.id }
            }
            _deleteOutcomes.send(outcome)
        }
    }
}

/**
 * The PNG the server expects, exactly [OG_WIDTH]×[OG_HEIGHT]. The card is rendered at
 * that size already; a device that rounds differently is scaled the last pixel.
 */
internal fun Bitmap.toOgPng(): ByteArray {
    // A hardware bitmap can't be drawn into the software canvas that scaling uses.
    val readable = if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this
    val sized = if (readable.width == OG_WIDTH && readable.height == OG_HEIGHT) readable
    else Bitmap.createScaledBitmap(readable, OG_WIDTH, OG_HEIGHT, true)
    return ByteArrayOutputStream().use { out ->
        sized.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }
}

const val OG_WIDTH = 1200
const val OG_HEIGHT = 630
