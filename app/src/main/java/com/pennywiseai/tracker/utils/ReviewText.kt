package com.pennywiseai.tracker.utils

import android.content.Context
import com.pennywiseai.tracker.R

/** "12% less than the week before". Shared by the screen and the weekly recap. */
internal fun changeText(context: Context, change: Int, target: String): String = when {
    change == 0 -> context.getString(R.string.review_change_same, target)
    change < 0 -> context.getString(R.string.review_change_less, -change, target)
    else -> context.getString(R.string.review_change_more, change, target)
}
