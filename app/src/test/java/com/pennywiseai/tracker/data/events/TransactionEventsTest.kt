package com.pennywiseai.tracker.data.events

import com.lemonappdev.konsist.api.Konsist
import com.pennywiseai.tracker.core.AppForegroundState
import com.pennywiseai.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionEventsTest {
    private var foreground = true
    private val events = TransactionEvents(AppForegroundState { foreground })

    private fun listen(): Pair<List<TransactionEvent>, () -> Unit> {
        val heard = mutableListOf<TransactionEvent>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch { events.saved.collect { heard += it } }
        return heard to { job.cancel() }
    }

    @Test
    fun `a listener hears a save made in the foreground`() {
        val (heard, stop) = listen()
        events.onSaved(TransactionType.INCOME, TransactionOrigin.MANUAL)
        stop()
        assertEquals(listOf(TransactionEvent(TransactionType.INCOME, 1, TransactionOrigin.MANUAL)), heard)
    }

    @Test
    fun `a save made while backgrounded is dropped, not replayed on return`() {
        val (heard, stop) = listen()
        foreground = false
        events.onSaved(TransactionType.EXPENSE, TransactionOrigin.BANK_MESSAGE)
        foreground = true
        stop()
        val (later, stopLater) = listen()
        stopLater()
        assertTrue(heard.isEmpty())
        assertTrue(later.isEmpty())
    }

    @Test
    fun `a save with no one listening is gone`() {
        events.onSaved(TransactionType.EXPENSE, TransactionOrigin.MANUAL)
        val (heard, stop) = listen()
        stop()
        assertTrue(heard.isEmpty())
    }

    @Test
    fun `only the live save paths announce saves, never imports, restores, recurring rows or loans`() {
        val announcers = Konsist.scopeFromProduction(moduleName = "app").files
            .filter { it.name != "TransactionEvents" && "TransactionEvents" in it.text && ".onSaved(" in it.text }
            .map { it.name }
            .toSet()
        // The Developer mode playground announces too, to play the reactions; it saves nothing.
        assertEquals(
            setOf("SmsTransactionProcessor", "AddTransactionUseCase", "ChatViewModel", "MascotPlaygroundScreen"),
            announcers,
        )
    }

    @Test
    fun `only the manual add screens hold the announcing use case`() {
        // AddTransactionUseCase.execute and executeTransfer announce MANUAL saves, so an
        // importer, restore or recurring path must not reach them.
        val holder = Regex(""":\s*(?:[\w.]+\.)?AddTransactionUseCase\b""")
        val files = Konsist.scopeFromProduction(moduleName = "app").files
        val holders = files.filter { holder.containsMatchIn(it.text) }.map { it.name }.toSet()
        assertEquals(setOf("AddViewModel", "QuickAddActivity", "ChatViewModel"), holders)
        // The chat card goes through executeAll, which announces nothing; its caller does.
        val chat = files.single { it.name == "ChatViewModel" }.text
        assertTrue(Regex("""addTransactionUseCase\.execute(Transfer)?\(""").find(chat) == null)
    }
}
