package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.TransactionGroupDao
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class TransactionGroupRepositoryTest {

    private fun tx(id: Long, groupId: Long, day: Int) = TransactionEntity(
        id = id,
        amount = BigDecimal.TEN,
        merchantName = "Shop",
        category = "Others",
        transactionType = TransactionType.EXPENSE,
        dateTime = LocalDateTime.of(2026, 11, day, 12, 0),
        transactionHash = "h$id",
        groupId = groupId,
    )

    @Test
    fun `one query gives each group its own transactions, newest first`() = runBlocking {
        val trip = TransactionGroupEntity(id = 1, name = "Trip")
        val party = TransactionGroupEntity(id = 2, name = "Party")
        val empty = TransactionGroupEntity(id = 3, name = "Empty")
        val dao = mockk<TransactionGroupDao> {
            every { getAllGroups() } returns flowOf(listOf(trip, party, empty))
            every { getAllGroupedTransactions() } returns flowOf(
                listOf(tx(10, 1, 3), tx(11, 2, 5), tx(12, 1, 7), tx(13, 1, 1))
            )
        }

        val result = TransactionGroupRepository(dao).observeGroupsWithTransactions().first()

        assertEquals(listOf(trip, party, empty), result.map { it.first })
        assertEquals(listOf(12L, 10L, 13L), result[0].second.map { it.id })
        assertEquals(listOf(11L), result[1].second.map { it.id })
        assertEquals(emptyList<Long>(), result[2].second.map { it.id })
    }
}
