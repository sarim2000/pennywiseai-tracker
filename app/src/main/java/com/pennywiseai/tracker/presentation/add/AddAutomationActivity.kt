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
        // Checked here, and only the checked values go on, in a new intent: none of the
        // caller's action, data, ClipData, flags or other extras (MainActivity acts on
        // chat and widget extras) reach MainActivity. Each read is caught: on Android 12
        // one malformed extra makes every read of the bundle throw.
        val link = intent.data?.takeIf { it.isHierarchical }
        val add = AddAutomation.prefill { key ->
            @Suppress("DEPRECATION")
            runCatching { intent.extras?.get(key) }.getOrNull()
                ?: runCatching { link?.getQueryParameter(key) }.getOrNull()
        }
        val forward = Intent(this, MainActivity::class.java)
            .setAction(AddAutomation.ACTION_ADD_TRANSACTION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        AddAutomation.extras(add).forEach { (key, value) -> forward.putExtra(key, value) }
        startActivity(forward)
        finish()
    }
}
