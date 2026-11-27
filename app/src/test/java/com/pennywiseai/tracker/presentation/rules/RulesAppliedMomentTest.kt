package com.pennywiseai.tracker.presentation.rules

import com.pennywiseai.tracker.data.events.MascotMoment
import com.pennywiseai.tracker.domain.usecase.BatchApplyResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RulesAppliedMomentTest {

    @Test
    fun `rules that changed rows say how many`() {
        assertEquals(
            MascotMoment.RulesApplied(4),
            rulesAppliedMoment(BatchApplyResult(totalProcessed = 40, totalUpdated = 4, totalDeleted = 2)),
        )
    }

    @Test
    fun `rules that changed nothing stay quiet`() {
        assertNull(rulesAppliedMoment(BatchApplyResult(totalProcessed = 40, totalUpdated = 0)))
        // Rows a blocking rule removed aren't "updated".
        assertNull(rulesAppliedMoment(BatchApplyResult(totalProcessed = 40, totalUpdated = 0, totalDeleted = 3)))
    }

    @Test
    fun `a run with an error stays quiet, even if some rows changed`() {
        assertNull(rulesAppliedMoment(BatchApplyResult(totalProcessed = 40, totalUpdated = 4, errors = listOf("Error"))))
    }
}
