package com.pennywiseai.tracker.widget

import com.pennywiseai.tracker.data.preferences.PrivacyPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class WidgetMaskTest {

    private val shown = WidgetMask(totals = false, amounts = false)
    private val totalsOnly = WidgetMask(totals = true, amounts = false)

    @Test
    fun `without App Lock the eyes decide`() {
        assertEquals(shown, widgetMask(homeHidden = false, hiddenEverywhere = false, appLockOn = false, hideWhileLocked = true))
        // A fresh install: Home hides its totals, rows still show, as in the app.
        assertEquals(totalsOnly, widgetMask(homeHidden = true, hiddenEverywhere = false, appLockOn = false, hideWhileLocked = true))
        assertEquals(WidgetMask.ALL, widgetMask(homeHidden = true, hiddenEverywhere = true, appLockOn = false, hideWhileLocked = true))
    }

    @Test
    fun `App Lock masks every amount whatever the eyes say`() {
        assertEquals(WidgetMask.ALL, widgetMask(homeHidden = false, hiddenEverywhere = false, appLockOn = true, hideWhileLocked = true))
        assertEquals(WidgetMask.ALL, widgetMask(homeHidden = true, hiddenEverywhere = false, appLockOn = true, hideWhileLocked = true))
        assertEquals(WidgetMask.ALL, widgetMask(homeHidden = true, hiddenEverywhere = true, appLockOn = true, hideWhileLocked = true))
    }

    @Test
    fun `with the widget switch off App Lock leaves the eyes to decide`() {
        assertEquals(shown, widgetMask(homeHidden = false, hiddenEverywhere = false, appLockOn = true, hideWhileLocked = false))
        assertEquals(totalsOnly, widgetMask(homeHidden = true, hiddenEverywhere = false, appLockOn = true, hideWhileLocked = false))
        assertEquals(WidgetMask.ALL, widgetMask(homeHidden = true, hiddenEverywhere = true, appLockOn = true, hideWhileLocked = false))
    }

    private fun prefs(appLockOn: Flow<Boolean>) = mockk<PrivacyPreferences> {
        every { isBalanceHidden } returns flowOf(false)
        every { isAmountHiddenEverywhere } returns flowOf(false)
        every { isAppLockEnabled } returns appLockOn
        every { hideWidgetAmountsWhileLocked } returns flowOf(true)
    }

    @Test
    fun `a failing first read hides everything`() = runBlocking {
        val mask = widgetMaskFlow(prefs(flow { throw IOException("unreadable") })).first()

        assertEquals(WidgetMask.ALL, mask)
    }

    @Test
    fun `a read that fails later hides everything instead of ending the widget`() = runBlocking {
        // The read fails only once the widget has drawn its amounts, so it's a later failure.
        val drawn = CompletableDeferred<Unit>()
        val masks = withTimeout(5_000) {
            widgetMaskFlow(
                prefs(
                    flow {
                        emit(false)
                        drawn.await()
                        throw IOException("unreadable")
                    }
                )
            ).onEach { if (it == shown) drawn.complete(Unit) }.toList()
        }

        assertEquals(listOf(shown, WidgetMask.ALL), masks)
    }

    @Test
    fun `a bar or ring is empty while its figure is hidden`() {
        assertEquals(0f, shownFraction(0.6f, hidden = true))
        assertEquals(0.6f, shownFraction(0.6f, hidden = false))
    }
}
