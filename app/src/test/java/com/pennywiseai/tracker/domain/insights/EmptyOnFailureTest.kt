package com.pennywiseai.tracker.domain.insights

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EmptyOnFailureTest {
    @Test
    fun `a failed load shows nothing, then the flow keeps going`() = runTest {
        var loads = 0
        val budgets = flow {
            loads++
            if (loads == 1) error("database closed")
            emit(listOf("Groceries"))
        }

        assertEquals(listOf(emptyList(), listOf("Groceries")), budgets.emptyOnFailure().take(2).toList())
    }
}
