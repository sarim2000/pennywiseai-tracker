package com.pennywiseai.tracker.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The app-wide "hide amounts" switch: the Home eye (and the shake gesture) flip
 * [UserPreferencesRepository.isAmountHiddenEverywhere][com.pennywiseai.tracker.data.preferences.UserPreferencesRepository.isAmountHiddenEverywhere],
 * and [com.pennywiseai.tracker.PennyWiseApplication] mirrors it here. It's off
 * until the user chooses to hide. Home starts hidden before any choice and sets
 * [hiddenOnHome] while it's on screen, so all of Home masks together rather than a
 * crossed-out eye sitting above a transaction list in the clear.
 *
 * [CurrencyFormatter.formatCurrency] reads [hidden], so every screen that formats
 * money shows [MASK] while it's on. It's snapshot state, so any composable that
 * formatted an amount recomposes when it flips: no per-screen plumbing.
 */
object AmountPrivacy {
    const val MASK = "••••"

    private var hiddenEverywhere by mutableStateOf(false)

    var hiddenOnHome by mutableStateOf(false)

    /** What [CurrencyFormatter] reads. Setting it sets the app-wide choice. */
    var hidden: Boolean
        get() = hiddenEverywhere || hiddenOnHome
        set(value) { hiddenEverywhere = value }

    /** False until the saved value has been read, so the splash can wait and no amount flashes. */
    @Volatile
    var loaded = false
}
