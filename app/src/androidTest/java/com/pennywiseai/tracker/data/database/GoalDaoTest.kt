package com.pennywiseai.tracker.data.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.receiver.BankNotificationConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDateTime

/** #914: which contributions count, and what deletes cascade. */
@RunWith(AndroidJUnit4::class)
class GoalDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, PennyWiseDatabase::class.java
    ).build()
    private val goals = db.goalDao()
    private val transactions = db.transactionDao()

    @After fun close() = db.close()

    private fun txn(id: Long, sender: String? = null) = TransactionEntity(
        id = id, amount = BigDecimal("100"), merchantName = "M$id", category = "Others",
        transactionType = TransactionType.INCOME, dateTime = LocalDateTime.of(2026, 10, 1, 12, 0),
        transactionHash = "h$id", smsSender = sender
    )

    private suspend fun seed(): Long {
        transactions.insertTransaction(txn(1, sender = "AD-KOTAKB"))
        val goalId = goals.insertGoal(GoalEntity(name = "Trip", targetAmount = BigDecimal("1000")))
        goals.insertContribution(GoalContributionEntity(goalId = goalId, amount = BigDecimal("50")))
        goals.insertContribution(GoalContributionEntity(goalId = goalId, amount = BigDecimal("100"), transactionId = 1))
        return goalId
    }

    @Test fun softDeletedTransactionStopsCountingAndUndoBringsItBack() = runBlocking {
        val goalId = seed()
        assertEquals(2, goals.observeEffectiveContributionsForGoal(goalId).first().size)

        transactions.updateTransaction(transactions.getTransactionById(1)!!.copy(isDeleted = true))
        assertEquals(listOf("50"), goals.observeEffectiveContributionsForGoal(goalId).first().map { it.amount.toPlainString() })
        assertEquals(1, goals.observeContributionsForTransaction(1).first().size) // row kept

        transactions.updateTransaction(transactions.getTransactionById(1)!!.copy(isDeleted = false))
        assertEquals(2, goals.observeEffectiveContributions().first().size)
    }

    @Test fun hardDeletingTheTransactionCascadesOnlyItsContribution() = runBlocking {
        val goalId = seed()
        transactions.deleteTransactionById(1)
        assertEquals(1, goals.countContributions(goalId))
    }

    @Test fun deletingTheGoalCascadesContributionsButKeepsTransactions() = runBlocking {
        val goalId = seed()
        goals.deleteGoal(goalId)
        assertEquals(0, goals.getAllContributions().size)
        assertNotNull(transactions.getTransactionById(1))
    }

    @Test fun rebuildKeepsSmsRowsPutTowardAGoal() = runBlocking {
        seed()
        transactions.insertTransaction(txn(2, sender = "AD-KOTAKB"))
        transactions.deleteRebuildableSmsTransactions(
            BankNotificationConfig.notificationAliases.toList(), from = LocalDateTime.of(2026, 1, 1, 0, 0)
        )
        assertNotNull(transactions.getTransactionById(1))
        assertEquals(null, transactions.getTransactionById(2))
        assertEquals(2, goals.getAllContributions().size)
    }
}
