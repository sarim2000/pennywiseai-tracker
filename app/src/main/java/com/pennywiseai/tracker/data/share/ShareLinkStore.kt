package com.pennywiseai.tracker.data.share

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/** A link this phone published, with the token that takes it down. */
@Serializable
data class SharedLink(
    val id: String,
    val url: String,
    val deleteToken: String,
    /** ISO-8601 instant, or null for a link that never expires. */
    val expiresAt: String? = null,
    /** The card's title, as it appears on the page. */
    val title: String = "",
    /** The card's ShareKind name. */
    val kind: String = "",
) {
    /** Unreadable dates count as never, so a link is never hidden by mistake. */
    val expiry: Instant? get() = expiresAt?.let {
        try {
            Instant.parse(it)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    fun isExpired(now: Instant): Boolean = expiry?.let { !it.isAfter(now) } ?: false
}

private val Context.shareLinks by preferencesDataStore(name = "share_links")

/**
 * The links this phone published, newest first.
 *
 * A DataStore of its own, deliberately outside Room and the settings in
 * UserPreferencesRepository: PennyWise backups export those, and a delete token is
 * device-local — restored on another phone it would let that phone take the page down.
 * Android's own backup is off for the app (`allowBackup="false"`), so nothing else
 * copies this file either.
 */
@Singleton
class ShareLinkStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val key = stringPreferencesKey("links")

    /** An unreadable file shows an empty list instead of crashing the screen that collects it. */
    val links: Flow<List<SharedLink>> = context.shareLinks.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { decodeLinks(it[key]) }

    /** Saves [link] first and drops any that have expired: their pages are gone already. */
    suspend fun add(link: SharedLink, now: Instant = Instant.now()) {
        context.shareLinks.edit { prefs ->
            val kept = decodeLinks(prefs[key]).filterNot { it.id == link.id || it.isExpired(now) }
            prefs[key] = encodeLinks(listOf(link) + kept)
        }
    }

    suspend fun remove(id: String) {
        context.shareLinks.edit { prefs ->
            prefs[key] = encodeLinks(decodeLinks(prefs[key]).filterNot { it.id == id })
        }
    }
}

private val linksJson = Json { ignoreUnknownKeys = true }
private val linksSerializer = ListSerializer(SharedLink.serializer())

internal fun encodeLinks(links: List<SharedLink>): String = linksJson.encodeToString(linksSerializer, links)

/** A corrupt entry loses the list rather than crashing the screen; the pages still expire on their own. */
internal fun decodeLinks(raw: String?): List<SharedLink> =
    raw?.let { runCatching { linksJson.decodeFromString(linksSerializer, it) }.getOrNull() }.orEmpty()

/** The links worth listing: the ones still up. */
fun List<SharedLink>.live(now: Instant): List<SharedLink> = filterNot { it.isExpired(now) }
