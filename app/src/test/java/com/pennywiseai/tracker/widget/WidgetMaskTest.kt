package com.pennywiseai.tracker.widget

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
