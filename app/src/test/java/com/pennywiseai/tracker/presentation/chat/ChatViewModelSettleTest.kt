package com.pennywiseai.tracker.presentation.chat

import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.manager.ModelDownloader
import com.pennywiseai.tracker.data.model.PendingChatAction
import com.pennywiseai.tracker.data.model.ReviewRow
import com.pennywiseai.tracker.data.model.TransactionDraft
import com.pennywiseai.tracker.data.repository.LlmRepository
import com.pennywiseai.tracker.data.repository.ModelRepository
import com.pennywiseai.tracker.data.repository.ModelState
import com.pennywiseai.tracker.domain.usecase.AddTransactionUseCase
import com.pennywiseai.tracker.worker.MoneyAlertWorker
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The chat's Add card shows its "saved" check only after the rows are written. Driven on the
 * Main test dispatcher's clock rather than runTest, like the other ViewModel tests here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelSettleTest {
    private val main = UnconfinedTestDispatcher()
    private val pending = MutableStateFlow<PendingChatAction?>(null)
    private val llm = mockk<LlmRepository>(relaxed = true) {
        every { pendingAction } returns pending
        every { isConfirming } returns MutableStateFlow(false)
    }
    private val addTransaction = mockk<AddTransactionUseCase>(relaxed = true)
    private val draft = TransactionDraft(
        amount = BigDecimal.TEN,
        currency = "INR",
        date = LocalDate.of(2026, 1, 1),
        merchant = "Test merchant",
        category = "Food",
        type = TransactionType.EXPENSE,
        bankName = null,
        accountLast4 = null,
        accountLabel = "Cash",
        sourceText = "",
        amountInMessage = true,
    )

    @Before fun setUp() {
        Dispatchers.setMain(main)
        mockkObject(MoneyAlertWorker.Companion)
        every { MoneyAlertWorker.enqueue(any()) } just Runs
    }

    @After fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun viewModel() = ChatViewModel(
        context = mockk(relaxed = true),
        llmRepository = llm,
        modelRepository = mockk<ModelRepository>(relaxed = true) {
            every { modelState } returns MutableStateFlow(ModelState.NOT_DOWNLOADED)
        },
        userPreferencesRepository = mockk(relaxed = true),
        addTransactionUseCase = addTransaction,
        deleteTransactionUseCase = mockk(relaxed = true),
        categoryRepository = mockk(relaxed = true),
        transactionRepository = mockk(relaxed = true),
        modelDownloader = mockk<ModelDownloader>(relaxed = true) {
            every { progress } returns MutableStateFlow(ModelDownloader.Progress())
        },
        appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
    )

    @Test
    fun `a saved Add writes its line, then holds its check for a beat`() {
        val viewModel = viewModel().apply { animationsEnabled = { true } }
        var checkShownWhenLineJoined: Boolean? = null
        coEvery { llm.completePendingAction(any()) } coAnswers {
            checkShownWhenLineJoined = viewModel.uiState.value.cardSaved
            pending.value = null
        }
        pending.value = PendingChatAction.Add(listOf(ReviewRow(id = 0, draft = draft)))

        viewModel.confirmPendingAction()

        // The line is written before the beat, while the settled card is still drawn.
        assertTrue(viewModel.uiState.value.cardSaved)
        coVerify(exactly = 1) { llm.completePendingAction(any()) }
        main.scheduler.advanceUntilIdle()
        assertEquals(true, checkShownWhenLineJoined)
        assertFalse(viewModel.uiState.value.cardSaved)
        assertFalse(viewModel.isConfirming.value)
    }

    @Test
    fun `with animations removed a saved Add lets go of the card at once`() {
        val viewModel = viewModel().apply { animationsEnabled = { false } }
        coEvery { llm.completePendingAction(any()) } coAnswers { pending.value = null }
        pending.value = PendingChatAction.Add(listOf(ReviewRow(id = 0, draft = draft)))

        viewModel.confirmPendingAction()

        coVerify(exactly = 1) { llm.completePendingAction(any()) }
        assertFalse(viewModel.uiState.value.cardSaved)
        assertFalse(viewModel.isConfirming.value)
        assertEquals(1, viewModel.uiState.value.adds)
    }

    @Test
    fun `a failed save never shows the check and keeps the card`() {
        coEvery { addTransaction.executeAll(any()) } throws IllegalStateException()
        val viewModel = viewModel()
        val checkStates = mutableListOf<Boolean>()
        val watching = CoroutineScope(main).launch {
            viewModel.uiState.collect { checkStates += it.cardSaved }
        }
        pending.value = PendingChatAction.Add(listOf(ReviewRow(id = 0, draft = draft)))

        viewModel.confirmPendingAction()
        main.scheduler.advanceUntilIdle()
        watching.cancel()

        assertFalse(true in checkStates)
        assertNotNull(viewModel.uiState.value.error)
        assertNotNull(pending.value)
        coVerify(exactly = 0) { llm.completePendingAction(any()) }
    }
}
