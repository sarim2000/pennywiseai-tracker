package com.pennywiseai.tracker.presentation.add

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Add save contract: only a real row closes the form, and one tap saves one row. */
class SaveGateTest {

    @Test
    fun `a new row id is a save`() = runTest {
        assertEquals(SaveOutcome.Saved, SaveGate().save { 42L })
    }

    @Test
    fun `a refused insert is not a save and the draft can be saved again`() = runTest {
        val gate = SaveGate()
        assertEquals(SaveOutcome.Refused, gate.save { -1L })
        assertEquals(SaveOutcome.Saved, gate.save { 7L })
    }

    @Test
    fun `a failed insert is not a save and frees the gate`() = runTest {
        val gate = SaveGate()
        val outcome = gate.save { error("disk full") }
        assertTrue(outcome is SaveOutcome.Failed)
        assertEquals(SaveOutcome.Saved, gate.save { 3L })
    }

    @Test
    fun `a second tap while saving is ignored`() = runTest {
        val gate = SaveGate()
        val release = CompletableDeferred<Long>()
        var inserts = 0
        val first = async { gate.save { inserts++; release.await() } }
        testScheduler.runCurrent()
        assertEquals(SaveOutcome.Busy, gate.save { inserts++; 2L })
        release.complete(1L)
        assertEquals(SaveOutcome.Saved, first.await())
        assertEquals(1, inserts)
    }

    @Test
    fun `nothing saves twice after a success`() = runTest {
        val gate = SaveGate()
        assertEquals(SaveOutcome.Saved, gate.save { 1L })
        assertEquals(SaveOutcome.Busy, gate.save { 2L })
    }
}
