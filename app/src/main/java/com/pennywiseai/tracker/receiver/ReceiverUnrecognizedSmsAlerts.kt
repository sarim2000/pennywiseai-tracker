package com.pennywiseai.tracker.receiver

import android.content.Context
import com.pennywiseai.tracker.data.repository.UnrecognizedSmsAlerts
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** [UnrecognizedSmsAlerts] for the suggestion alerts [SmsBroadcastReceiver] posts. */
class ReceiverUnrecognizedSmsAlerts @Inject constructor(
    @ApplicationContext private val context: Context
) : UnrecognizedSmsAlerts {

    override fun cancel(unrecognizedSmsId: Long) = SmsBroadcastReceiver.cancelSuggestion(context, unrecognizedSmsId)

    override fun cancelAll() = SmsBroadcastReceiver.cancelAllSuggestions(context)
}
