package com.pennywiseai.tracker.data.webhook

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.webhookSettings by preferencesDataStore(name = "webhook_settings")

@Singleton
class WebhookPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val intervalKey = intPreferencesKey("interval_hours")
    val intervalHours = context.webhookSettings.data.map { (it[intervalKey] ?: 6).coerceIn(1, 24) }

    suspend fun setInterval(hours: Int) {
        require(hours in 1..24)
        context.webhookSettings.edit { it[intervalKey] = hours }
    }
}
