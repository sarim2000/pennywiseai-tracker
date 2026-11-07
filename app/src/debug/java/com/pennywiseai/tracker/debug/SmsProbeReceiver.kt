package com.pennywiseai.tracker.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import com.pennywiseai.tracker.receiver.NotificationActionReceiver
import com.pennywiseai.tracker.receiver.SmsBroadcastReceiver
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Debug-only (never in release builds): runs a made-up bank SMS through the
 * real receive path — parse, save, alert — so the transaction notification
 * can be checked on a physical phone, where adb can't inject an SMS. Guarded
 * by android.permission.DUMP like LlmProbeReceiver:
 *   adb shell am broadcast -n <pkg>/com.pennywiseai.tracker.debug.SmsProbeReceiver \
 *       --es sender <bank sender id> --es body '<sms text>' [--el timestamp <epoch ms>]
 * The optional timestamp backdates the SMS, e.g. to seed last month's charge.
 * The alert only shows while the app is in the background, as for a real SMS.
 * With --ez waiting true it instead files the SMS under Unrecognized SMS, as a scan would.
 * With only --el add_taught <unrecognized sms id> it fires the taught alert's Add action.
 */
class SmsProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val addTaught = intent.getLongExtra("add_taught", -1L)
        if (addTaught > 0) {
            NotificationActionReceiver.addTaughtIntent(context, addTaught.toInt(), addTaught).send()
            return
        }
        val sender = intent.getStringExtra("sender") ?: return
        val body = intent.getStringExtra("body") ?: return
        val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SmsBroadcastReceiver.SmsBroadcastReceiverEntryPoint::class.java
        )
        val processor = entryPoint.smsTransactionProcessor()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.getBooleanExtra("waiting", false)) {
                    entryPoint.unrecognizedSmsRepository().insert(
                        UnrecognizedSmsEntity(
                            sender = sender,
                            smsBody = body,
                            receivedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
                        )
                    )
                } else {
                    SmsBroadcastReceiver().processIncomingSms(context, processor, sender, body, timestamp)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
