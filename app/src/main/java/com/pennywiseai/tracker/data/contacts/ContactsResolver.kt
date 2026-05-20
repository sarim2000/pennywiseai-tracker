package com.pennywiseai.tracker.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a 10-digit phone number to a contact display name by querying
 * `ContactsContract` and caching the result for the app session.
 *
 * Used by the "Replace UPI VPAs with contact names" feature: when the UI is
 * about to render a merchant that looks like `9876543210@paytm`, it asks
 * this resolver for `9876543210`. Misses are cached as null so we never
 * re-query a number that isn't in the user's contacts.
 *
 * Permission is checked on every call rather than at construction so a
 * mid-session revocation degrades gracefully (subsequent calls return
 * null instead of throwing). [clearCache] should be invoked whenever the
 * caller knows the contacts set may have changed (toggle on, manual
 * refresh button).
 */
@Singleton
class ContactsResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // Both hits and misses are cached. Value is null when the lookup ran
    // and found no contact for that number; absence-of-key means we haven't
    // queried yet. Using ConcurrentHashMap lets render-time lookups happen
    // from any thread without an explicit lock.
    private val cache = ConcurrentHashMap<String, Optional>()

    /**
     * Returns the contact's display name for [phoneLast10], or null if no
     * matching contact, no permission, or the input doesn't look like a
     * 10-digit number.
     */
    fun resolve(phoneLast10: String): String? {
        if (phoneLast10.length != 10 || !phoneLast10.all { it.isDigit() }) return null
        cache[phoneLast10]?.let { return it.value }

        if (!hasPermission()) {
            // Don't cache the miss when it's caused by missing permission —
            // the user may grant it later in the same session and we want
            // a subsequent call to actually query.
            return null
        }

        val resolved = queryContactName(phoneLast10)
        cache[phoneLast10] = Optional(resolved)
        return resolved
    }

    /** Drops every cached lookup. Call after a toggle change or manual refresh. */
    fun clearCache() {
        cache.clear()
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

    /** Wrapper so we can distinguish "cached miss" from "not yet queried". */
    private data class Optional(val value: String?)
}
