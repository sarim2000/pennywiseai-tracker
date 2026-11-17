package com.pennywiseai.tracker.presentation.statement

import com.pennywiseai.tracker.data.events.MascotMoment
import com.pennywiseai.tracker.data.statement.StatementImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatementImportedMomentTest {

    private fun result(imported: Int, enriched: Int, duplicates: Int = 0) = StatementImportResult.Success(
        imported = imported,
        skippedDuplicates = duplicates,
        skippedByReference = 0,
        skippedByAmountDate = duplicates,
        skippedByHash = 0,
        totalParsed = imported + enriched + duplicates,
        enriched = enriched,
    )

    @Test
    fun `an import counts the rows it added and the ones it filled in`() {
        assertEquals(MascotMoment.StatementImported(7), statementImportedMoment(result(imported = 5, enriched = 2, duplicates = 9)))
        assertEquals(MascotMoment.StatementImported(3), statementImportedMoment(result(imported = 0, enriched = 3)))
    }

    @Test
    fun `a statement already in PennyWise stays quiet`() {
        assertNull(statementImportedMoment(result(imported = 0, enriched = 0, duplicates = 12)))
    }
}
