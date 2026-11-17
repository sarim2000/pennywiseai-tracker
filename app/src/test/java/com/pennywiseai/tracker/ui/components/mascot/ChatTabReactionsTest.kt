package com.pennywiseai.tracker.ui.components.mascot

import com.pennywiseai.tracker.core.AppForegroundState
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.events.TransactionEvent
import com.pennywiseai.tracker.data.events.TransactionEvents
import com.pennywiseai.tracker.data.events.TransactionOrigin
import com.pennywiseai.tracker.utils.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/** Driven on a test dispatcher's clock rather than runTest, like the ViewModel tests. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatTabReactionsTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(dispatcher)
    private val reactions = ChatTabReactions(scope)
    private val shown get() = reactions.shown.value

    private fun save(type: TransactionType, money: Money? = null) {
        reactions.onSave(TransactionEvent(type, 1, TransactionOrigin.MANUAL, money))
    }

    private fun runCurrent() = dispatcher.scheduler.runCurrent()

    private fun advanceTimeBy(millis: Long) = dispatcher.scheduler.advanceTimeBy(millis)

    /** The tab on screen, following [scan] (like WorkManager's, it never completes). */
    private fun start(scan: Flow<SmsScanStatus> = MutableStateFlow(SmsScanStatus.Idle)) =
        scope.launch { reactions.watchScan(scan) }.also { runCurrent() }

    @Test
    fun `each type maps to its shape and mood`() {
        assertEquals(MascotReaction(MascotShape.Coin, MascotMood.Happy), reactionFor(TransactionType.INCOME))
        assertEquals(MascotReaction(MascotShape.Receipt, MascotMood.Calm), reactionFor(TransactionType.EXPENSE))
        assertEquals(MascotReaction(MascotShape.Receipt, MascotMood.Calm), reactionFor(TransactionType.CREDIT))
        assertEquals(MascotReaction(MascotShape.Wallet, MascotMood.Calm), reactionFor(TransactionType.TRANSFER))
        assertEquals(MascotReaction(MascotShape.Jar, MascotMood.Calm), reactionFor(TransactionType.INVESTMENT))
        // Every type reacts, and only income is happy.
        assertEquals(
            listOf(TransactionType.INCOME),
            TransactionType.entries.filter { reactionFor(it).mood == MascotMood.Happy },
        )
    }

    @Test
    fun `a save shows its shape for the hold, then the owl`() {
        val job = start()
        save(TransactionType.TRANSFER)
        runCurrent()
        assertEquals(reactionFor(TransactionType.TRANSFER), shown)
        advanceTimeBy(REACTION_HOLD_MS - 1)
        assertEquals(reactionFor(TransactionType.TRANSFER), shown)
        advanceTimeBy(2)
        assertNull(shown)
        job.cancel()
    }

    @Test
    fun `three saves in a burst make one reaction that never extends`() {
        val job = start()
        val seen = mutableListOf<MascotReaction?>()
        save(TransactionType.EXPENSE)
        runCurrent()
        seen += shown
        advanceTimeBy(1_000)
        save(TransactionType.INCOME)
        save(TransactionType.INVESTMENT)
        runCurrent()
        seen += shown
        // The hold still ends where the first save started it.
        advanceTimeBy(REACTION_HOLD_MS - 1_000 + 1)
        seen += shown
        // And nothing was queued behind it.
        advanceTimeBy(REACTION_HOLD_MS * 3)
        seen += shown
        assertEquals(listOf(reactionFor(TransactionType.EXPENSE), reactionFor(TransactionType.EXPENSE), null, null), seen)
        job.cancel()
    }

    @Test
    fun `a save's amount rides its reaction, and a burst keeps the first`() {
        val job = start()
        val spent = Money(BigDecimal("12.50"), "EUR")
        save(TransactionType.EXPENSE, spent)
        runCurrent()
        val expected = reactionFor(TransactionType.EXPENSE).copy(amount = ReactionAmount(spent, TransactionType.EXPENSE))
        assertEquals(expected, shown)

        save(TransactionType.INCOME, Money(BigDecimal.TEN, "EUR"))
        runCurrent()
        assertEquals(expected, shown)
        advanceTimeBy(REACTION_HOLD_MS + 1)
        assertNull(shown)
        job.cancel()
    }

    @Test
    fun `a save with no amount, or a scan, shows no amount`() {
        val scan = MutableStateFlow<SmsScanStatus>(SmsScanStatus.Idle)
        val job = start(scan)
        save(TransactionType.INCOME)
        runCurrent()
        assertNull(shown?.amount)
        advanceTimeBy(REACTION_HOLD_MS + 1)

        scan.value = SmsScanStatus.Running
        runCurrent()
        save(TransactionType.INCOME, Money(BigDecimal.TEN, "EUR"))
        runCurrent()
        assertEquals(SCANNING, shown)
        assertNull(shown?.amount)
        scan.value = SmsScanStatus.Finished(newRows = 1)
        runCurrent()
        assertEquals(SCANNED, shown)
        assertNull(shown?.amount)
        job.cancel()
    }

    @Test
    fun `stopping clears the reaction, and a save made in the background is not replayed`() {
        var foreground = true
        val events = TransactionEvents(AppForegroundState { foreground })
        scope.launch { events.saved.collect(reactions::onSave) }
        var job = start()
        events.onSaved(TransactionType.INCOME, TransactionOrigin.MANUAL)
        runCurrent()
        assertEquals(reactionFor(TransactionType.INCOME), shown)

        // Backgrounded: the screen stops, and the save made then is dropped.
        foreground = false
        job.cancel()
        runCurrent()
        assertNull(shown)
        events.onSaved(TransactionType.EXPENSE, TransactionOrigin.BANK_MESSAGE)
        runCurrent()

        foreground = true
        job = start()
        assertNull(shown)
        job.cancel()
    }

    @Test
    fun `a save made while a pushed screen covers the tabs shows when they come back`() {
        // The tab leaves composition when Add opens over it...
        start().cancel()
        runCurrent()
        save(TransactionType.EXPENSE)
        runCurrent()
        advanceTimeBy(500)
        // ...and comes back as Add closes, inside the save's hold.
        val job = start()
        assertEquals(reactionFor(TransactionType.EXPENSE), shown)
        advanceTimeBy(REACTION_HOLD_MS - 500 + 1)
        assertNull(shown)
        job.cancel()
    }

    @Test
    fun `the hold ends on time while no one watches`() {
        save(TransactionType.INCOME)
        advanceTimeBy(REACTION_HOLD_MS + 1)
        val job = start()
        assertNull(shown)
        job.cancel()
    }

    @Test
    fun `a scan shows the bubble, then one calm beat when it saved new rows`() {
        val scan = MutableStateFlow<SmsScanStatus>(SmsScanStatus.Idle)
        val job = start(scan)
        scan.value = SmsScanStatus.Running
        runCurrent()
        assertEquals(SCANNING, shown)
        // A save during the scan doesn't interrupt it or wait behind it.
        save(TransactionType.INCOME)
        runCurrent()
        assertEquals(SCANNING, shown)

        scan.value = SmsScanStatus.Finished(newRows = 4)
        runCurrent()
        assertEquals(SCANNED, shown)
        advanceTimeBy(REACTION_HOLD_MS + 1)
        assertNull(shown)
        job.cancel()
    }

    @Test
    fun `a scan with nothing new, or one that finished unseen, gets no beat`() {
        val scan = MutableStateFlow<SmsScanStatus>(SmsScanStatus.Finished(newRows = 9))
        val job = start(scan)
        assertNull(shown)

        scan.value = SmsScanStatus.Running
        runCurrent()
        scan.value = SmsScanStatus.Finished(newRows = 0)
        runCurrent()
        assertNull(shown)
        job.cancel()
    }
}
