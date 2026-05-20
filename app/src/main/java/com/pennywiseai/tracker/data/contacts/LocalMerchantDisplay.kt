package com.pennywiseai.tracker.data.contacts

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Renders a merchant string for display. Default is identity (return the
 * raw merchant) so any composable rendered outside the app's main tree
 * (e.g. previews, dialogs without our wrapper) stays correct.
 *
 * Set this at the screen / nav root from a value that closes over the
 * current [useContactsForVpa] preference and the singleton
 * [ContactsResolver]. Consumers then read it without needing the
 * preference or resolver wired through each composable's props.
 *
 * staticCompositionLocalOf because the value only flips on a settings
 * toggle, so we don't need per-read recomposition tracking.
 */
val LocalMerchantDisplay = staticCompositionLocalOf<(String?) -> String?> { { it } }
