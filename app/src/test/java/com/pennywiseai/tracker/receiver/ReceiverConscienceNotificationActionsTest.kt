package com.pennywiseai.tracker.receiver

import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.pennywiseai.tracker.MainActivity
import io.mockk.EqMatcher
import io.mockk.MockKMatcherScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Pins what a conscience remark's taps carry. A posted notification keeps its
 * PendingIntents, so the component, action, extras, request code and flags must
 * stay what SmsBroadcastReceiver and NotificationActionReceiver built before the
 * contract existed.
 */
class ReceiverConscienceNotificationActionsTest {

    // A real (stub) Context, not a mock: mockk would record a mock's equals/hashCode inside the matchers.
    private val context: Context = ContextWrapper(null)
    private val pendingIntent = mockk<PendingIntent>()
    private val requestCode = slot<Int>()
    private val flags = slot<Int>()

    @Before
    fun setUp() {
        mockkStatic(PendingIntent::class)
        mockkConstructor(Intent::class)
        every { PendingIntent.getActivity(any(), capture(requestCode), any(), capture(flags)) } returns pendingIntent
        every { PendingIntent.getBroadcast(any(), capture(requestCode), any(), capture(flags)) } returns pendingIntent
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `openTransaction opens MainActivity on the transaction`() {
        recordIntentsFor(MainActivity::class.java)
        val result = ReceiverConscienceNotificationActions(context).openTransaction(TRANSACTION_ID)

        assertSame(pendingIntent, result)
        verify(exactly = 1) { PendingIntent.getActivity(context, any(), any(), any()) }
        assertEquals(TRANSACTION_ID.toInt(), requestCode.captured)
        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, flags.captured)
        verify {
            intentTo(MainActivity::class.java)
                .setAction("com.pennywiseai.tracker.ACTION_EDIT_TRANSACTION")
        }
        verify {
            intentTo(MainActivity::class.java)
                .putExtra("transaction_id", TRANSACTION_ID)
        }
        verify {
            intentTo(MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        assertNothingOverwritten(MainActivity::class.java, flags = 1, longExtras = 1, stringExtras = 0)
    }

    @Test
    fun `retireLine broadcasts the retire action with the line key on request code 0`() {
        recordIntentsFor(NotificationActionReceiver::class.java)
        val result = ReceiverConscienceNotificationActions(context).retireLine(LINE_KEY)

        assertSame(pendingIntent, result)
        verify(exactly = 1) { PendingIntent.getBroadcast(context, any(), any(), any()) }
        assertEquals(0, requestCode.captured)
        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, flags.captured)
        verify {
            intentTo(NotificationActionReceiver::class.java)
                .setAction("com.pennywiseai.tracker.ACTION_CONSCIENCE_RETIRE")
        }
        verify {
            intentTo(NotificationActionReceiver::class.java)
                .putExtra("conscience_line_key", LINE_KEY)
        }
        assertNothingOverwritten(NotificationActionReceiver::class.java, flags = 0, longExtras = 0, stringExtras = 1)
    }

    @Test
    fun `the retire action is the one NotificationActionReceiver handles`() {
        assertEquals("com.pennywiseai.tracker.ACTION_CONSCIENCE_RETIRE", NotificationActionReceiver.ACTION_CONSCIENCE_RETIRE)
        assertEquals("com.pennywiseai.tracker.ACTION_EDIT_TRANSACTION", SmsBroadcastReceiver.ACTION_EDIT_TRANSACTION)
        assertEquals("transaction_id", SmsBroadcastReceiver.EXTRA_TRANSACTION_ID)
    }

    /**
     * The stub Intent keeps no state, so the setters are checked by call: each one
     * as often as the builder calls it (a second call would overwrite the first),
     * and nothing that would retarget the intent or drop an extra.
     */
    private fun assertNothingOverwritten(component: Class<*>, flags: Int, longExtras: Int, stringExtras: Int) {
        verify(exactly = 1) { intentTo(component).setAction(any()) }
        verify(exactly = flags) { intentTo(component).setFlags(any()) }
        verify(exactly = longExtras) { intentTo(component).putExtra(any<String>(), any<Long>()) }
        verify(exactly = stringExtras) { intentTo(component).putExtra(any<String>(), any<String>()) }
        verify(exactly = 0) { intentTo(component).addFlags(any()) }
        verify(exactly = 0) { intentTo(component).removeExtra(any()) }
        verify(exactly = 0) { intentTo(component).setComponent(any()) }
        verify(exactly = 0) { intentTo(component).setClassName(any<Context>(), any<String>()) }
        verify(exactly = 0) { intentTo(component).setClass(any(), any()) }
        verify(exactly = 0) { intentTo(component).setPackage(any()) }
    }

    /** The Intent built as `Intent(context, component)`. */
    private fun MockKMatcherScope.intentTo(component: Class<*>): Intent =
        constructedWith(EqMatcher(context), EqMatcher(component))

    /** mockk matches constructor arguments only for an Intent registered before it is built. */
    private fun recordIntentsFor(component: Class<*>) {
        every { intentTo(component).setAction(any()) } answers { callOriginal() }
        every { intentTo(component).setFlags(any()) } answers { callOriginal() }
        every {
            intentTo(component).putExtra(any<String>(), any<Long>())
        } answers { callOriginal() }
        every {
            intentTo(component).putExtra(any<String>(), any<String>())
        } answers { callOriginal() }
    }

    private companion object {
        const val TRANSACTION_ID = 4242L
        const val LINE_KEY = "late_night.1"
    }
}
