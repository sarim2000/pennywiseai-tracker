package com.pennywiseai.tracker.data.database

import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.trip.withTripGroup
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDateTime

/** #1463: removing a row from a trip keeps it out by hash; adding it back clears that. */
@RunWith(AndroidJUnit4::class)
class TripDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, PennyWiseDatabase::class.java
    ).build()
    private val groups = db.transactionGroupDao()
    private val transactions = db.transactionDao()
    private val at = LocalDateTime.of(2026, 12, 2, 10, 0)

    @After fun close() = db.close()

    private fun txn(hash: String) = TransactionEntity(
        amount = BigDecimal("10"), merchantName = "Synthetic shop", category = "Others",
        transactionType = TransactionType.EXPENSE, dateTime = at, transactionHash = hash
    )

    @Test fun removedRowStaysOutUntilAddedBackByHand() = runBlocking {
        val tripId = groups.insertGroup(
            TransactionGroupEntity(name = "Synthetic trip", tripStart = at.minusDays(1), tripEnd = null)
        )
        assertEquals(tripId, transactions.getRunningTrip(at)?.id)

        val joined = transactions.withTripGroup(listOf(txn("h1")), at).single()
        assertEquals(tripId, joined.groupId)
        val id = transactions.insertTransaction(joined)

        groups.removeFromGroup(id)
        assertNull(transactions.getTransactionById(id)!!.groupId)
        assertNull(transactions.withTripGroup(listOf(txn("h1")), at).single().groupId)

        groups.addToGroup(id, tripId)
        assertEquals(tripId, transactions.withTripGroup(listOf(txn("h1")), at).single().groupId)
    }

    @Test fun deletingTheTripDropsItsExclusions() = runBlocking {
        val group = TransactionGroupEntity(name = "Synthetic trip", tripStart = at.minusDays(1))
        val tripId = groups.insertGroup(group)
        val id = transactions.insertTransaction(txn("h1").copy(groupId = tripId))
        groups.removeFromGroup(id)
        assertEquals(1, groups.getAllTripExclusions().size)

        groups.unlinkAndDeleteGroup(group.copy(id = tripId))
        assertEquals(0, groups.getAllTripExclusions().size)
    }

    @Test fun movingToAnotherGroupExcludesFromTheTripItLeft() = runBlocking {
        val tripId = groups.insertGroup(TransactionGroupEntity(name = "Synthetic trip", tripStart = at.minusDays(1)))
        val otherId = groups.insertGroup(TransactionGroupEntity(name = "Synthetic other"))
        val id = transactions.insertTransaction(txn("h1").copy(groupId = tripId))

        groups.addToGroup(id, otherId)

        assertEquals(otherId, transactions.getTransactionById(id)!!.groupId)
        assertEquals(listOf("h1"), transactions.getTripExclusions(tripId))
    }

    @Test fun movingIntoANewGroupExcludesFromTheTripItLeft() = runBlocking {
        val tripId = groups.insertGroup(TransactionGroupEntity(name = "Synthetic trip", tripStart = at.minusDays(1)))
        val id = transactions.insertTransaction(txn("h1").copy(groupId = tripId))

        val newId = groups.createGroupAndLink(TransactionGroupEntity(name = "Synthetic new"), id)

        assertEquals(newId, transactions.getTransactionById(id)!!.groupId)
        assertEquals(listOf("h1"), transactions.getTripExclusions(tripId))
    }

    @Test fun aRowCarryingADeletedTripIsRepairedAndOneWithALiveTripKeepsIt() = runBlocking {
        val tripId = groups.insertGroup(TransactionGroupEntity(name = "Synthetic trip", tripStart = at.minusDays(1)))
        val live = transactions.insertTransaction(txn("live").copy(groupId = tripId))
        val orphan = transactions.insertTransaction(txn("orphan").copy(groupId = tripId + 100))
        transactions.insertTransactions(listOf(txn("batch-live").copy(groupId = tripId), txn("batch-orphan").copy(groupId = tripId + 100)))

        transactions.clearDanglingGroup(live)
        transactions.clearDanglingGroup(orphan)
        transactions.clearDanglingGroups(listOf("batch-live", "batch-orphan"))

        assertEquals(tripId, transactions.getTransactionById(live)!!.groupId)
        assertNull(transactions.getTransactionById(orphan)!!.groupId)
        assertEquals(tripId, transactions.getTransactionByHash("batch-live")!!.groupId)
        assertNull(transactions.getTransactionByHash("batch-orphan")!!.groupId)
    }
}
