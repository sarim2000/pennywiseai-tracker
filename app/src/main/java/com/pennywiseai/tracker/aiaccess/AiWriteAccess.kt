package com.pennywiseai.tracker.aiaccess

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether connected AI apps may change data (categories, aliases, rules). Off by default:
 * apps paired before write tools existed were connected on a read-only promise.
 */
@Singleton
class AiWriteAccess @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("ai_access", Context.MODE_PRIVATE)
    private val _allowed = MutableStateFlow(prefs.getBoolean(KEY, false))
    val allowed: StateFlow<Boolean> = _allowed.asStateFlow()

    fun set(allowed: Boolean) {
        prefs.edit { putBoolean(KEY, allowed) }
        _allowed.value = allowed
    }

    private companion object { const val KEY = "allow_changes" }
}
