package com.pennywiseai.tracker.presentation.loans

import androidx.lifecycle.SavedStateHandle
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.data.events.MascotMoment
import com.pennywiseai.tracker.data.events.MascotMoments
import com.pennywiseai.tracker.data.repository.LoanRepository
import com.pennywiseai.tracker.data.repository.ManualRepayment
import com.pennywiseai.tracker.domain.loan.LoanMath
import com.pennywiseai.tracker.widget.WidgetRefresher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/** Only a repayment that clears a debt tells the Chat tab it's paid off; settling forgives, so it doesn't. */
@OptIn(ExperimentalCoroutinesApi::class)
class LoanPaidOffMomentTest {
    private val owed = LoanEntity(
        id = 3, personName = "Pal", direction = LoanDirection.LENT,
        originalAmount = BigDecimal("500"), remainingAmount = BigDecimal("200"), currency = "INR"
    )
    private val settled = owed.copy(status = LoanStatus.SETTLED, remainingAmount = BigDecimal.ZERO)

    /** The loan as stored. */
    private var stored = owed

    private val loans = mockk<LoanRepository>(relaxed = true) {
        coEvery { getLoanById(3) } answers { stored }
        every { getTransactionsForLoan(3) } returns flowOf(emptyList())
        coEvery { flippableOverflow(3, any(), any()) } returns null
    }
    private val moments = mockk<MascotMoments>(relaxed = true)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(WidgetRefresher)
        every { WidgetRefresher.refreshTransactionWidgets(any()) } just runs
    }

    @After fun tearDown() {
        unmockkObject(WidgetRefresher)
        Dispatchers.resetMain()
    }

    private fun viewModel() = LoanDetailViewModel(loans, mockk(relaxed = true), moments, SavedStateHandle(mapOf("loanId" to 3L)))

    /** A repayment leaves the loan [after] it; the repository says whether this one [paidOff] it. */
    private fun repayingLeaves(after: LoanEntity, paidOff: Boolean = LoanMath.paidOff(stored, after)) {
        coEvery { loans.recordManualRepayment(3, any(), any(), any(), any()) } answers {
            stored = after
            ManualRepayment(11L, paidOff)
        }
        coEvery { loans.recordRepayment(3, any(), any(), any(), any()) } answers {
            stored = after
            paidOff
        }
    }

    @Test
    fun `a manual repayment that clears the debt is paid off, once`() {
        repayingLeaves(settled)
        viewModel().recordManualRepayment(BigDecimal("200"))
        verify(exactly = 1) { moments.announce(MascotMoment.DebtPaidOff) }
        verify(exactly = 1) { moments.announce(any()) }
    }

    @Test
    fun `a linked transaction that clears the debt is paid off`() {
        repayingLeaves(settled)
        viewModel().linkTransactionAsRepayment(9)
        verify(exactly = 1) { moments.announce(MascotMoment.DebtPaidOff) }
    }

    @Test
    fun `a partial repayment says nothing`() {
        repayingLeaves(owed.copy(remainingAmount = BigDecimal("50")))
        viewModel().recordManualRepayment(BigDecimal("150"))
        verify(exactly = 0) { moments.announce(any()) }
    }

    @Test
    fun `a loan found settled after another payment cleared it says nothing`() {
        // A second link queued behind the one that paid it off: the repository says no.
        repayingLeaves(settled, paidOff = false)
        viewModel().linkTransactionAsRepayment(9)
        verify(exactly = 0) { moments.announce(any()) }
    }

    @Test
    fun `a refused repayment says nothing`() {
        coEvery { loans.recordRepayment(3, any(), any(), any(), any()) } throws IllegalStateException("split")
        viewModel().linkTransactionAsRepayment(9)
        verify(exactly = 0) { moments.announce(any()) }
    }

    @Test
    fun `settling forgives what's left, so it says nothing`() {
        coEvery { loans.settleLoan(3) } answers { stored = settled }
        viewModel().settleLoan()
        coVerify(exactly = 1) { loans.settleLoan(3) }
        verify(exactly = 0) { moments.announce(any()) }
    }
}
