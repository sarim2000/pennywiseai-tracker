package com.pennywiseai.tracker.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The app-wide "hide amounts" switch: the Home eye (and the shake gesture) flip
 * [UserPreferencesRepository.isBalanceHidden][com.pennywiseai.tracker.data.preferences.UserPreferencesRepository.isBalanceHidden],
 * and [com.pennywiseai.tracker.PennyWiseApplication] mirrors it here.
 *
 * [CurrencyFormatter.formatCurrency] reads [hidden], so every screen that formats
 * money shows [MASK] while it's on. It's snapshot state, so any composable that
 * formatted an amount recomposes when it flips: no per-screen plumbing.
 */
object AmountPrivacy {
    const val MASK = "••••"

    var hidden by mutableStateOf(false)

    /** False until the saved value has been read, so the splash can wait and no amount flashes. */
    @Volatile
    var loaded = false
}
