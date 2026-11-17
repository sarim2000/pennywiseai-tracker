package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.core.AppForegroundState
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.data.events.TransactionEvent
import com.pennywiseai.tracker.data.events.TransactionEvents
import com.pennywiseai.tracker.data.events.TransactionOrigin
import com.pennywiseai.tracker.data.model.TransactionDraft
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.utils.Money
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

private const val ROOM_COMPAT = "com.pennywiseai.tracker.data.database.RoomCompatKt"

/** What the manual save paths tell the Chat tab. */
class AddTransactionUseCaseEventsTest {
    private val database = mockk<PennyWiseDatabase>()
    private val balances = mockk<AccountBalanceRepository>()
    private val events = TransactionEvents(AppForegroundState { true })
    private val useCase = AddTransactionUseCase(database, mockk(relaxed = true), balances, mockk(relaxed = true), events)
    private val heard = mutableListOf<TransactionEvent>()
    private val listener = CoroutineScope(Dispatchers.Unconfined).launch { events.saved.collect { heard += it } }

    @Before fun setUp() {
        mockkStatic(ROOM_COMPAT)
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    @After fun tearDown() {
        listener.cancel()
        unmockkStatic(ROOM_COMPAT)
    }

    private fun insertReturns(id: Long) {
        coEvery { balances.insertTransactionWithBalance(any(), any(), any()) } returns id
        coEvery { balances.insertTransferWithBalance(any(), any(), any(), any(), any()) } returns id
    }

    /** What [add] and [transfer] save: the use case's own default currency. */
    private val ten = Money(BigDecimal.TEN, "INR")

    private fun add(type: TransactionType) = runBlocking {
        useCase.execute(amount = BigDecimal.TEN, merchant = "Shop", category = "Food", type = type, date = LocalDateTime.now())
    }

    private fun transfer() = runBlocking {
        useCase.executeTransfer(
            amount = BigDecimal.TEN,
            date = LocalDateTime.now(),
            fromBankName = "Bank A",
            fromLast4 = "1111",
            toBankName = "Bank B",
            toLast4 = "2222",
        )
    }

    @Test
    fun `a manual add announces one save of its type`() {
        insertReturns(7)
        add(TransactionType.INCOME)
        assertEquals(listOf(TransactionEvent(TransactionType.INCOME, 1, TransactionOrigin.MANUAL, ten)), heard)
    }

    @Test
    fun `a manual transfer announces one transfer`() {
        insertReturns(7)
        assertEquals(7L, transfer())
        assertEquals(listOf(TransactionEvent(TransactionType.TRANSFER, 1, TransactionOrigin.MANUAL, ten)), heard)
    }

    @Test
    fun `an insert the database ignored announces nothing`() {
        insertReturns(-1)
        add(TransactionType.EXPENSE)
        assertEquals(-1L, transfer())
        assertEquals(emptyList<TransactionEvent>(), heard)
    }

    @Test
    fun `a chat card's batch announces nothing itself, its caller does once it commits`() {
        insertReturns(7)
        val draft = TransactionDraft(
            amount = BigDecimal.TEN,
            currency = "INR",
            date = LocalDate.now(),
            merchant = "Shop",
            category = "Food",
            type = TransactionType.EXPENSE,
            bankName = null,
            accountLast4 = null,
            accountLabel = "Cash",
            sourceText = "spent 10 on food",
            amountInMessage = true,
        )
        runBlocking { useCase.executeAll(listOf(draft, draft.copy(type = TransactionType.INCOME))) }
        assertEquals(emptyList<TransactionEvent>(), heard)
    }
}
