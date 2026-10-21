package com.pennywiseai.tracker.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pennywiseai.tracker.receiver.SmsBroadcastReceiver
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug-only (never in release builds): runs a made-up bank SMS through the
 * real receive path — parse, save, alert — so the transaction notification
 * can be checked on a physical phone, where adb can't inject an SMS. Guarded
 * by android.permission.DUMP like LlmProbeReceiver:
 *   adb shell am broadcast -n <pkg>/com.pennywiseai.tracker.debug.SmsProbeReceiver \
 *       --es sender <bank sender id> --es body '<sms text>'
 * The alert only shows while the app is in the background, as for a real SMS.
 */
class SmsProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sender = intent.getStringExtra("sender") ?: return
        val body = intent.getStringExtra("body") ?: return
        val processor = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SmsBroadcastReceiver.SmsBroadcastReceiverEntryPoint::class.java
        ).smsTransactionProcessor()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SmsBroadcastReceiver().processIncomingSms(context, processor, sender, body, System.currentTimeMillis())
            } finally {
                pending.finish()
            }
        }
    }
}
