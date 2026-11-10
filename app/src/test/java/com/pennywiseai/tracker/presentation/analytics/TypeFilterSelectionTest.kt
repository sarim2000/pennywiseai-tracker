package com.pennywiseai.tracker.presentation.analytics

import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeFilterSelectionTest {
    @Test
    fun `type filter follows the setting until picked, and again after Clear`() {
        val f = TypeFilterSelection()
        assertEquals(TransactionTypeFilter.EXPENSE, f.value.value)

        f.onDefaultChanged(TransactionTypeFilter.SPENDING) // setting turned on
        assertEquals(TransactionTypeFilter.SPENDING, f.value.value)

        f.pick(TransactionTypeFilter.EXPENSE) // user picks a type
        f.onDefaultChanged(TransactionTypeFilter.EXPENSE)
        f.onDefaultChanged(TransactionTypeFilter.SPENDING)
        assertEquals("a manual pick survives setting changes", TransactionTypeFilter.EXPENSE, f.value.value)

        f.reset() // Clear
        assertEquals(TransactionTypeFilter.SPENDING, f.value.value)
        f.onDefaultChanged(TransactionTypeFilter.EXPENSE) // setting turned off after Clear
        assertEquals("follows the setting again after Clear", TransactionTypeFilter.EXPENSE, f.value.value)
    }
}
