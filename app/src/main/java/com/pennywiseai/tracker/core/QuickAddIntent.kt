package com.pennywiseai.tracker.core

import android.content.Context
import android.content.Intent

/**
 * Launch contract for the Quick Add sheet, so entry points (worker, widget) can
 * start it without importing screen code. Targets the activity by class name.
 */
object QuickAddIntent {
    /** Must match the manifest declaration of the Quick Add activity. */
    const val ACTIVITY_CLASS_NAME = "com.pennywiseai.tracker.presentation.add.QuickAddActivity"

    fun create(context: Context): Intent = Intent().setClassName(context, ACTIVITY_CLASS_NAME)
}
