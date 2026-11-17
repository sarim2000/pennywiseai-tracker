package com.pennywiseai.tracker.presentation.goals

import androidx.lifecycle.SavedStateHandle
import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.events.MascotMoment
import com.pennywiseai.tracker.data.events.MascotMoments
import com.pennywiseai.tracker.data.repository.GoalDeposit
import com.pennywiseai.tracker.data.repository.GoalDetail
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GoalRepository
import com.pennywiseai.tracker.data.repository.GoalRules
import com.pennywiseai.tracker.utils.Money
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/** Money put into a goal by the user tells the Chat tab once, after it saved; nothing else does. */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalMomentsViewModelTest {
    private val today = LocalDate.now()
    private val goal = GoalEntity(
        id = 1, name = "Goal", targetAmount = BigDecimal("1000"), currency = "INR",
        dueDate = today.plusMonths(6), createdAt = today.minusMonths(1).atStartOfDay()
    )

    private fun progress(saved: String): GoalProgress =
        GoalRules.progress(goal, listOf(GoalContributionEntity(id = 1, goalId = 1, amount = BigDecimal(saved))), today)

    /** What the goal holds as stored: each deposit adds to it. */
    private var saved = BigDecimal("700")

    private val goals = mockk<GoalRepository>(relaxed = true) {
        every { observeGoalDetail(1, any()) } answers { flowOf(GoalDetail(progress(saved.toPlainString()), emptyList())) }
        coEvery { addContribution(1, any(), any(), any(), any(), any(), any(), any()) } answers {
            val before = progress(saved.toPlainString())
            saved += secondArg<BigDecimal>()
            GoalDeposit(7L, before, progress(saved.toPlainString()))
        }
    }
    private val moments = mockk<MascotMoments>(relaxed = true)

    // The screen's collector, so the detail's state is loaded as it is on screen.
    private val screen = CoroutineScope(Dispatchers.Unconfined)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        screen.cancel()
        Dispatchers.resetMain()
    }

    private fun detailViewModel() = GoalDetailViewModel(
        goals, mockk(relaxed = true), mockk(relaxed = true), moments, SavedStateHandle(mapOf("goalId" to 1L))
    ).also { vm -> screen.launch { vm.state.collect {} } }

    private fun deposit(vm: GoalDetailViewModel, amount: String, withdrawal: Boolean = false) =
        vm.saveMovement(withdrawal, BigDecimal(amount), "INR", today, "", null)

    @Test
    fun `a deposit that reaches the target is the milestone, once`() {
        deposit(detailViewModel(), "300")
        verify(exactly = 1) { moments.announce(MascotMoment.GoalReached) }
        verify(exactly = 1) { moments.announce(any()) }
    }

    @Test
    fun `a deposit short of the target carries the amount typed`() {
        deposit(detailViewModel(), "100")
        verify(exactly = 1) { moments.announce(MascotMoment.GoalContributed(Money(BigDecimal("100"), "INR"))) }
    }

    @Test
    fun `taking money out says nothing`() {
        deposit(detailViewModel(), "100", withdrawal = true)
        coVerify { goals.addWithdrawal(1, any(), any(), any(), any(), any()) }
        verify(exactly = 0) { moments.announce(any()) }
    }

    @Test
    fun `a save that failed says nothing`() {
        coEvery { goals.addContribution(1, any(), any(), any(), any(), any(), any(), any()) } throws IllegalStateException("db")
        deposit(detailViewModel(), "300")
        verify(exactly = 0) { moments.announce(any()) }
    }

    @Test
    fun `editing a contribution says nothing, even past the target`() {
        val vm = detailViewModel()
        vm.saveContributionEdit(GoalContributionEntity(id = 1, goalId = 1, amount = BigDecimal("700")), BigDecimal("1000"), today, "")
        coVerify { goals.updateContribution(any()) }
        verify(exactly = 0) { moments.announce(any()) }
    }

    @Test
    fun `a share of a transaction put toward a goal carries the share in its currency`() {
        val txn = TransactionEntity(
            id = 9, amount = BigDecimal("500"), merchantName = "Shop", category = "Income",
            transactionType = TransactionType.INCOME, dateTime = LocalDateTime.of(2026, 11, 1, 9, 0),
            transactionHash = "h", currency = "INR"
        )
        coEvery { goals.unallocatedAmount(9, any()) } returns BigDecimal("500")
        val vm = TransactionGoalsViewModel(goals, mockk(relaxed = true), moments)
        vm.setTransaction(txn)
        vm.openSheet()
        vm.add(goal, BigDecimal("200"), "", null)
        verify(exactly = 1) { moments.announce(MascotMoment.GoalContributed(Money(BigDecimal("200"), "INR"))) }
        vm.openSheet()
        vm.add(goal, BigDecimal("100"), "", null)
        verify(exactly = 1) { moments.announce(MascotMoment.GoalReached) }
    }
}
