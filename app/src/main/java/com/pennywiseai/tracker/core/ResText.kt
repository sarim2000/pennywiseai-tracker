package com.pennywiseai.tracker.core

import androidx.annotation.StringRes

/**
 * A string resource plus its format args, for non-UI layers that report a
 * user-facing message. A ViewModel turns it into `UiText` with `UiText.of`.
 */
data class ResText(@StringRes val id: Int, val args: List<Any> = emptyList())
