package com.pennywiseai.tracker.presentation.transactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NormalizeReferenceTest {

    @Test
    fun `a typed reference is saved trimmed`() {
        assertEquals("412345678901", normalizeReference("  412345678901 "))
    }

    @Test
    fun `a blank or missing reference saves as null`() {
        assertNull(normalizeReference("   "))
        assertNull(normalizeReference(""))
        assertNull(normalizeReference(null))
    }
}
