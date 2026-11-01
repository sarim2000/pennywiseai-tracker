package com.pennywiseai.tracker.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Apps the user chose to track notifications from (#994 step 4). Their
 * notifications go through the generic extractor into Unrecognized SMS as
 * suggestions; nothing reaches totals until the user taps Add. Apps with a
 * dedicated parser (BankNotificationConfig) don't need to be picked.
 */
@Singleton
class TrackedNotificationAppsStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): Set<String> = prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty().toSet()

    val allFlow: Flow<Set<String>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(all()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(all())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun isTracked(packageName: String): Boolean = packageName in all()

    fun setTracked(packageName: String, tracked: Boolean) {
        val updated = if (tracked) all() + packageName else all() - packageName
        prefs.edit().putStringSet(KEY_PACKAGES, updated).apply()
    }

    private companion object {
        const val PREFS_NAME = "tracked_notification_apps"
        const val KEY_PACKAGES = "packages"
    }
}
