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

    /** Opens listening: a spend fills the sheet, a question goes to Chat. */
    const val EXTRA_VOICE = "com.pennywiseai.tracker.QUICK_ADD_VOICE"

    fun create(context: Context): Intent = Intent().setClassName(context, ACTIVITY_CLASS_NAME)

    /** The mic on Home and on the cash widget. */
    fun voice(context: Context): Intent = create(context).putExtra(EXTRA_VOICE, true)
}
