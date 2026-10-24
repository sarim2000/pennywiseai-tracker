package com.pennywiseai.tracker.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryPieWidgetTest {

    @Test
    fun `only colors a theme background would swallow follow the text color`() {
        assertTrue(isLowContrast(0xFF000000.toInt()))
        assertTrue(isLowContrast(0xFF1C1B1F.toInt()))
        assertTrue(isLowContrast(0xFFFFFFFF.toInt()))
        // The usual category colors and the grey "Other" keep their own.
        listOf(0xFFFF7043, 0xFF42A5F5, 0xFFAB47BC, 0xFF26A69A, 0xFF9E9E9E, 0xFF424242).forEach {
            assertFalse(it.toString(16), isLowContrast(it.toInt()))
        }
    }
}
