package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class TransferPairsTest {
    private val base = LocalDateTime.of(2026, 1, 1, 12, 0)

    private fun leg(
        id: Long, account: String?, amount: String = "100", minutes: Long = 0, from: String? = "1111", to: String? = "2222",
        pairId: String? = "pair", currency: String = "JOD", updatedAt: LocalDateTime = base.plusMinutes(id)
    ) = TransactionEntity(
        id = id, amount = BigDecimal(amount), merchantName = "Synthetic", category = "Others",
        transactionType = TransactionType.TRANSFER, dateTime = base.plusMinutes(minutes), transactionHash = "h$id",
        bankName = account?.let { "Bank $it" }, accountNumber = account, currency = currency, updatedAt = updatedAt,
        fromAccount = from, toAccount = to, fromBankName = from?.let { "Bank $it" }, toBankName = to?.let { "Bank $it" },
        transferPairId = pairId
    )

    @Test
    fun `a paired leg moves its account as the debit or credit it was`() {
        assertEquals(TransactionType.EXPENSE, leg(1, "1111").ledgerType())
        assertEquals(TransactionType.INCOME, leg(2, "2222").ledgerType())
    }

    @Test
    fun `an account-less source leg is still the source`() {
        assertEquals(TransactionType.EXPENSE, leg(1, null, from = null).ledgerType())
    }

    @Test
    fun `the same suffix at two banks is told apart by bank`() {
        val row = leg(1, "1111", to = "1111").copy(toBankName = "Other bank")
        assertEquals(TransactionType.EXPENSE, row.ledgerType())
        assertEquals(TransactionType.INCOME, row.copy(bankName = "Other bank").ledgerType())
    }

    @Test
    fun `a standalone transfer stays a transfer, and so does a leg with no direction`() {
        assertEquals(TransactionType.TRANSFER, leg(1, "1111", pairId = null).ledgerType())
        assertEquals(TransactionType.TRANSFER, leg(2, "1111", to = "1111").ledgerType())
    }

    @Test
    fun `a leg turned back into an expense is no longer a leg`() {
        val row = leg(1, "1111").copy(transactionType = TransactionType.EXPENSE)
        assertEquals(TransactionType.EXPENSE, row.ledgerType())
        assertEquals(listOf(row, row), listOf(row, row).countingTransferPairsOnce { it })
    }

    @Test
    fun `a pair counts once as its source leg, whichever comes first`() {
        val source = leg(1, "1111", amount = "100")
        val destination = leg(2, "2222", amount = "99")
        val standalone = leg(3, "1111", pairId = null)
        assertEquals(listOf(source, standalone), listOf(destination, source, standalone).countingTransferPairsOnce { it })
    }

    @Test
    fun `a filter showing one leg still counts it`() {
        val destination = leg(2, "2222")
        assertEquals(listOf(destination), listOf(destination).countingTransferPairsOnce { it })
    }

    @Test
    fun `repeated equal transfers pair by closest time`() {
        val rows = listOf(
            leg(1, "1111", pairId = null), leg(2, "2222", minutes = 1, pairId = null),
            leg(3, "1111", minutes = 30, pairId = null), leg(4, "2222", minutes = 31, pairId = null)
        )
        assertEquals(listOf(1L to 2L, 3L to 4L), findUnlinkedTransferPairs(rows))
    }

    @Test
    fun `a pair with a fee or long gap is found by being updated together`() {
        val marked = base.plusDays(1)
        val rows = listOf(
            leg(1, "1111", amount = "100", pairId = null, updatedAt = marked),
            leg(2, "2222", amount = "99", minutes = 300, pairId = null, updatedAt = marked.plusNanos(6_000_000))
        )
        assertEquals(listOf(1L to 2L), findUnlinkedTransferPairs(rows))
    }

    @Test
    fun `being updated together beats another pair's equal amount`() {
        // Sent 100, got 99; then sent 101, got 100. The 100s are not a pair.
        val first = base.plusDays(1)
        val second = first.plusMinutes(1)
        val rows = listOf(
            leg(1, "1111", amount = "100", pairId = null, updatedAt = first),
            leg(2, "2222", amount = "99", minutes = 1, pairId = null, updatedAt = first),
            leg(3, "1111", amount = "101", minutes = 10, pairId = null, updatedAt = second),
            leg(4, "2222", amount = "100", minutes = 11, pairId = null, updatedAt = second)
        )
        assertEquals(listOf(1L to 2L, 3L to 4L), findUnlinkedTransferPairs(rows))
    }

    @Test
    fun `a row edited since still pairs by amount and time`() {
        val rows = listOf(
            leg(1, "1111", pairId = null, updatedAt = base.plusDays(2)),
            leg(2, "2222", minutes = 1, pairId = null, updatedAt = base.plusDays(1))
        )
        assertEquals(listOf(1L to 2L), findUnlinkedTransferPairs(rows))
    }

    @Test
    fun `an older standalone transfer edited with a pair doesn't take its destination`() {
        // A bulk edit (category by merchant) stamps all three at once.
        val edited = base.plusDays(1)
        val rows = listOf(
            leg(1, "1111", minutes = -60, pairId = null, updatedAt = edited), // standalone, three hours before
            leg(2, "1111", minutes = 120, pairId = null, updatedAt = edited),
            leg(3, "2222", minutes = 121, pairId = null, updatedAt = edited)
        )
        assertEquals(listOf(2L to 3L), findUnlinkedTransferPairs(rows))
    }

    @Test
    fun `an edit time alone doesn't link rows days apart or a destination larger than sent`() {
        val edited = base.plusDays(40)
        val daysApart = listOf(
            leg(1, "1111", pairId = null, updatedAt = edited),
            leg(2, "2222", minutes = 8 * 24 * 60, pairId = null, updatedAt = edited)
        )
        assertEquals(emptyList<Pair<Long, Long>>(), findUnlinkedTransferPairs(daysApart))

        val moreThanSent = listOf(
            leg(1, "1111", amount = "100", pairId = null, updatedAt = edited),
            leg(2, "2222", amount = "150", minutes = 90, pairId = null, updatedAt = edited)
        )
        assertEquals(emptyList<Pair<Long, Long>>(), findUnlinkedTransferPairs(moreThanSent))
    }

    @Test
    fun `the same account suffixes at other banks are another route`() {
        val edited = base.plusDays(1)
        val rows = listOf(
            leg(1, "1111", pairId = null, updatedAt = edited),
            leg(2, "2222", minutes = 1, pairId = null, updatedAt = edited).copy(fromBankName = "Other bank")
        )
        assertEquals(emptyList<Pair<Long, Long>>(), findUnlinkedTransferPairs(rows))
    }

    @Test
    fun `a deleted leg is not linked, so the row left still moves both accounts`() {
        val rows = listOf(
            leg(1, "1111", pairId = null),
            leg(2, "2222", minutes = 1, pairId = null).copy(isDeleted = true)
        )
        assertEquals(emptyList<Pair<Long, Long>>(), findUnlinkedTransferPairs(rows))
    }

    @Test
    fun `standalone transfers and other routes are not paired`() {
        val rows = listOf(
            leg(1, "1111", pairId = null), // standalone: owned by its source, no destination row
            leg(2, "1111", minutes = 1, pairId = null),
            leg(3, "3333", minutes = 1, from = "4444", to = "3333", pairId = null),
            leg(4, "2222", amount = "50", minutes = 2, pairId = null), // other amount, no shared stamp
            leg(5, "2222", minutes = 2, pairId = "already linked")
        )
        assertEquals(emptyList<Pair<Long, Long>>(), findUnlinkedTransferPairs(rows))
    }
}
