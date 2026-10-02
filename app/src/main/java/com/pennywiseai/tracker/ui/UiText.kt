package com.pennywiseai.tracker.ui

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * A user-facing message a ViewModel emits without holding a Context. It is
 * resolved in the composable, so it follows the current (per-app) locale.
 * [Plain] carries text that is already final, e.g. an error detail from a
 * lower layer.
 */
sealed interface UiText {
    data class Plain(val value: String) : UiText
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText

    @Composable
    fun asString(): String = when (this) {
        is Plain -> value
        is Res -> stringResource(id, *args.toTypedArray())
        is Plural -> pluralStringResource(id, count, *args.toTypedArray())
    }
}
