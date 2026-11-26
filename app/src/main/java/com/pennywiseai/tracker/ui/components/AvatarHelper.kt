package com.pennywiseai.tracker.ui.components

import com.pennywiseai.tracker.R

object AvatarHelper {
    val avatarDrawables = listOf(
        R.drawable.avatar_1,
        R.drawable.avatar_2,
        R.drawable.avatar_3,
        R.drawable.avatar_4,
        R.drawable.avatar_5,
        R.drawable.avatar_6,
        R.drawable.avatar_7,
        R.drawable.avatar_8,
        R.drawable.avatar_9,
        R.drawable.avatar_10
    )

    /** The colours behind an avatar or initials; onboarding and Edit profile offer the same set. */
    val backgroundColors = listOf(
        0xFFDC8A78.toInt(), // Rosewater
        0xFFDD7878.toInt(), // Flamingo
        0xFFEA76CB.toInt(), // Pink
        0xFF8839EF.toInt(), // Mauve
        0xFFD20F39.toInt(), // Red
        0xFFFE640B.toInt(), // Peach
        0xFFDF8E1D.toInt(), // Yellow
        0xFF40A02B.toInt(), // Green
        0xFF179299.toInt(), // Teal
        0xFF04A5E5.toInt(), // Sky
        0xFF209FB5.toInt(), // Sapphire
        0xFF1E66F5.toInt(), // Blue
        0xFF7287FD.toInt(), // Lavender
        0xFF6C6F85.toInt(), // Subtext0
        0xFF8C8FA1.toInt(), // Overlay1
        0xFFACB0BE.toInt()  // Overlay2
    )

    /**
     * Resolves an "avatar://INDEX" URI to a drawable resource ID.
     * Returns null if the URI is not an avatar URI or index is out of range.
     */
    fun resolveAvatarDrawable(uri: String): Int? {
        if (!uri.startsWith("avatar://")) return null
        val index = uri.removePrefix("avatar://").toIntOrNull() ?: return null
        return avatarDrawables.getOrNull(index)
    }
}
