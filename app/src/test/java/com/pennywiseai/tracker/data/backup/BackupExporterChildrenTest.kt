package com.pennywiseai.tracker.data.backup

import com.pennywiseai.tracker.data.database.entity.TransactionTagCrossRef
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupExporterChildrenTest {
    @Test
    fun keepsLinksToExportedTransactionsAndDropsTheRest() {
        val refs = listOf(
            TransactionTagCrossRef(transactionId = 1, tagId = 7),
            TransactionTagCrossRef(transactionId = 2, tagId = 7) // soft-deleted: not exported
        )
        assertEquals(refs.take(1), refs.forTransactions(setOf(1L)) { it.transactionId })
    }

    @Test
    fun unparsableParentIdIsDropped() {
        assertEquals(emptyList<String>(), listOf("abc").forTransactions(setOf(1L)) { it.toLongOrNull() })
    }
}
