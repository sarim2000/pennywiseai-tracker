package com.pennywiseai.tracker.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a 10-digit phone number to a contact display name by querying
 * `ContactsContract` off the main thread and caching the result for the
 * app session.
 *
 * Used by the "Replace UPI VPAs with contact names" feature: when the UI is
 * about to render a merchant that looks like `9876543210@paytm`, it asks
 * this resolver for `9876543210`. Misses are cached so we never re-query a
 * number that isn't in the user's contacts.
 *
 * Threading: [resolve] is called from Compose composition (main thread).
 * The first call for a given phone schedules a background query on
 * Dispatchers.IO and immediately returns null; once the query completes,
 * the result is written into a SnapshotStateMap which triggers
 * recomposition for any composable that read it, and the contact name
 * appears on the next frame.
 *
 * Permission is checked on every scheduled query rather than at
 * construction so a mid-session revocation degrades gracefully.
 * [clearCache] should be invoked whenever the caller knows the contacts
 * set may have changed (toggle flip, manual refresh).
 */
@Singleton
class ContactsResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // Owned by this singleton; outlives any individual screen but is tied
    // to the application lifetime. SupervisorJob so one failed query
    // doesn't cancel the rest.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // mutableStateMapOf so Compose readers re-render when an async query
    // writes back. Value is null for "queried and no contact". Absence of
    // a key means "haven't queried yet".
    private val cache = mutableStateMapOf<String, String?>()

    // Tracks queries already in flight so we don't fan out duplicates
    // when many list rows for the same phone render at once.
    private val pending = ConcurrentHashMap.newKeySet<String>()

    /**
     * Returns the resolved contact name for [phoneLast10] if it's already
     * cached, else null while scheduling a background query. The first
     * render after this call paints the original merchant; the second
     * (triggered by Compose when the cache map updates) paints the contact.
     *
     * Permission is rechecked on every call AHEAD of the cache lookup so
     * that revoking READ_CONTACTS via OS Settings stops surfacing cached
     * contact names on the next render. On Android 10 and below the
     * process survives permission revocation, so a check that only ran on
     * new queries would leave the cache leaking names.
     */
    fun resolve(phoneLast10: String): String? {
        if (phoneLast10.length != 10 || !phoneLast10.all { it.isDigit() }) return null
        if (!hasPermission()) return null
        if (cache.containsKey(phoneLast10)) return cache[phoneLast10]
        scheduleQuery(phoneLast10)
        return null
    }

    /** Drops every cached lookup. Call after a toggle change or manual refresh. */
    fun clearCache() {
        cache.clear()
        pending.clear()
    }

    private fun scheduleQuery(phoneLast10: String) {
        // pending.add returns true only the first time, so concurrent
        // calls for the same number don't all spin up a query.
        if (!pending.add(phoneLast10)) return
        ioScope.launch {
            val name = queryContactName(phoneLast10)
            // Cache the result (even if null = "queried, no contact") so a
            // subsequent resolve() returns immediately instead of polling
            // ContactsContract again.
            cache[phoneLast10] = name
            pending.remove(phoneLast10)
        }
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    private fun queryContactName(phoneLast10: String): String? {
        // PhoneLookup matches against normalised phone numbers, so we can
        // pass just the last 10 digits and it will match contacts saved
        // with or without a country-code prefix.
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon()
            .appendPath(phoneLast10)
            .build()
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: SecurityException) {
            // Permission was revoked between the check above and the query.
            null
        }
    }
}
