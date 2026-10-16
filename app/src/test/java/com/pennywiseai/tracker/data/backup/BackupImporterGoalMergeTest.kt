package com.pennywiseai.tracker.data.backup

import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.GoalDao
import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/** #914 (Greptile): a merge never moves money into a goal of another currency. */
class BackupImporterGoalMergeTest {
    private val goalDao = mockk<GoalDao>(relaxed = true)
    private val database = mockk<PennyWiseDatabase> { every { goalDao() } returns goalDao }
    private val importer = BackupImporter(mockk(relaxed = true), database, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))

    private fun goal(id: Long, uid: String, currency: String) =
        GoalEntity(id = id, uid = uid, name = "Goal $uid", targetAmount = BigDecimal("100"), currency = currency)

    private fun contribution(uid: String, goalId: Long) =
        GoalContributionEntity(uid = uid, goalId = goalId, amount = BigDecimal("10"))

    @Test
    fun contributionsOfAGoalWhoseLocalCurrencyChangedAreSkippedAndCounted() = runBlocking {
        // Same goals by uid; "a" changed currency here while it was still empty.
        coEvery { goalDao.getAllGoals() } returns listOf(goal(21, "a", "USD"), goal(22, "b", "INR"))
        coEvery { goalDao.getAllContributions() } returns emptyList()
        var skipped = 0

        importer.importGoalsWithMerge(
            goals = listOf(goal(1, "a", "INR"), goal(2, "b", "INR")),
            contributions = listOf(contribution("c1", 1), contribution("c2", 1), contribution("c3", 2)),
            transactionIdMap = emptyMap()
        ) { skipped++ }

        assertEquals(2, skipped)
        coVerify(exactly = 0) { goalDao.insertGoal(any()) }
        coVerify(exactly = 1) { goalDao.insertContribution(any()) }
        coVerify { goalDao.insertContribution(match { it.uid == "c3" && it.goalId == 22L }) }
    }
}
