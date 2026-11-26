package com.pennywiseai.tracker.presentation.add

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.pennywiseai.tracker.MainActivity

/**
 * Receives [AddAutomation.ACTION_ADD_TRANSACTION] and outside `pennywise://add`
 * links, and passes each one on to [MainActivity], which opens the pre-filled form.
 *
 * Automations fire the same intent again and again. Sent straight to MainActivity,
 * a repeat of the intent that started the app's task only brings that task to the
 * front: Android creates no activity and calls no onNewIntent, so the values were
 * lost. Forwarded with CLEAR_TOP and SINGLE_TOP, the running MainActivity always
 * gets the intent in onNewIntent, and a new one starts if the app isn't running.
 */
class AddAutomationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The copy keeps the action, data and extras, still unread: MainActivity reads
        // them defensively. Flags are replaced, so a caller's flags can't grant anything.
        val forward = Intent(intent).apply {
            setClass(this@AddAutomationActivity, MainActivity::class.java)
            selector = null
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(forward)
        finish()
    }
}
