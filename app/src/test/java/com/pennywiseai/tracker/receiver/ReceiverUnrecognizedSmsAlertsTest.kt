package com.pennywiseai.tracker.receiver

import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/** Suggestion alerts are posted under the "sms_suggestion" tag with the unrecognized row's id; cancelling must match both. */
class ReceiverUnrecognizedSmsAlertsTest {

    private val notificationManager = mockk<NotificationManager>()
    private val context = mockk<Context> {
        every { getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
    }

    @Test
    fun `cancel takes down the suggestion alert for that row`() {
        justRun { notificationManager.cancel(any<String>(), any()) }

        ReceiverUnrecognizedSmsAlerts(context).cancel(42L)

        verify(exactly = 1) { notificationManager.cancel("sms_suggestion", 42) }
    }

    @Test
    fun `cancelAll takes down only suggestion alerts`() {
        justRun { notificationManager.cancel(any<String>(), any()) }
        every { notificationManager.activeNotifications } returns arrayOf(
            posted("sms_suggestion", 7),
            posted(null, 7),
            posted("balance_discrepancy", 8),
            posted("sms_suggestion", 9),
        )

        ReceiverUnrecognizedSmsAlerts(context).cancelAll()

        verify(exactly = 1) { notificationManager.cancel("sms_suggestion", 7) }
        verify(exactly = 1) { notificationManager.cancel("sms_suggestion", 9) }
        verify(exactly = 2) { notificationManager.cancel(any<String>(), any()) }
    }

    private fun posted(tag: String?, id: Int) = mockk<StatusBarNotification> {
        every { this@mockk.tag } returns tag
        every { this@mockk.id } returns id
    }
}
