package com.pennywiseai.tracker.ui.components

import androidx.compose.runtime.Composable

/**
 * No-op on the Play build. Pro is not sold from inside this app, and Play's
 * Payments policy forbids it from carrying a link to where it is — so the
 * real implementation, its copy and its URL live only in the fdroid source
 * set and are never packaged here.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun SupportWebOption(onOpened: () -> Unit) = Unit
