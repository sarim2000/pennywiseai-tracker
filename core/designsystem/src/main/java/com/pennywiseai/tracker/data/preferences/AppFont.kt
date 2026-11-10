package com.pennywiseai.tracker.data.preferences

/**
 * The app's typeface. Stored by [name] only once the user picks one, so an
 * absent key means "never chose" and follows [DEFAULT]; a stored SYSTEM or
 * SN_PRO is an explicit choice and is kept.
 *
 * Lives in :core:designsystem under its historical package. Never rename an
 * entry: the name is the stored value.
 */
enum class AppFont {
    SYSTEM,
    SN_PRO,
    GOOGLE_SANS_FLEX;

    companion object {
        /** Google Sans Flex, the M3 Expressive typeface (Latin only; other scripts fall back to the system font). */
        val DEFAULT = GOOGLE_SANS_FLEX
    }
}
