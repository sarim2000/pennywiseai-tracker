package com.pennywiseai.tracker.ui.screens.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class NiceAxisMaxTest {
    @Test
    fun `axis tops out on a round number at or above the peak`() {
        assertEquals(200.0, niceAxisMax(179.0), 0.0)
        assertEquals(40.0, niceAxisMax(30.0), 0.0)
        assertEquals(10_000_000.0, niceAxisMax(9_876_543.0), 0.0)
        assertEquals(0.0, niceAxisMax(0.0), 0.0)
    }
}
