package com.pennywiseai.tracker.widget

import android.content.Context
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

/** The contract refreshes the same widgets WidgetRefresher always did, with the application context. */
class WorkManagerTransactionWidgetsTest {

    private val context = mockk<Context>()

    @Before
    fun setUp() {
        mockkObject(WidgetRefresher)
        justRun { WidgetRefresher.refreshTransactionWidgets(any()) }
        justRun { WidgetRefresher.redrawAll(any()) }
    }

    @After
    fun tearDown() = unmockkObject(WidgetRefresher)

    @Test
    fun `refresh delegates to WidgetRefresher once`() {
        WorkManagerTransactionWidgets(context).refresh()

        verify(exactly = 1) { WidgetRefresher.refreshTransactionWidgets(context) }
    }

    @Test
    fun `redraw redraws every widget without rebuilding their data`() {
        WorkManagerTransactionWidgets(context).redraw()

        verify(exactly = 1) { WidgetRefresher.redrawAll(context) }
        verify(exactly = 0) { WidgetRefresher.refreshTransactionWidgets(any()) }
    }
}
