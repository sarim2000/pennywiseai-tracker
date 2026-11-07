package com.pennywiseai.tracker.utils

import android.content.Context
import android.view.View

/**
 * [name] (a merchant, bank or bill, as the SMS spelled it) in a first-strong
 * isolate (FSI…PDI) when [this] context's language reads right to left. A line
 * takes its direction from its first letter, so an Arabic notification opening
 * with "Emirates NBD · قد تكون…" was laid out left to right and the bank landed
 * mid-sentence. Inside the isolate the name keeps its own direction and the
 * line takes the sentence's. No-op otherwise, like CurrencyFormatter's amounts,
 * so English text is unchanged.
 */
fun Context.bidiIsolate(name: String): String =
    if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) "\u2068$name\u2069" else name
