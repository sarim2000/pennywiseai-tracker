package com.pennywiseai.tracker.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicReference

/** A rebuild's saved account fields read back as written, so a later scan can restore them. */
class RebuildAccountFieldsStoreTest {

    private val card = AccountBalanceEntity(
        bankName = "Test Bank", accountLast4 = "0000", balance = BigDecimal("900"),
        timestamp = LocalDateTime.of(2026, 3, 20, 10, 0), isCreditCard = true, smsSource = "Sample message",
        statementDay = 12, cardDueDay = 2, cardBillAmount = BigDecimal("650.25"),
        cardBillAsOf = LocalDateTime.of(2026, 3, 14, 9, 0),
    )
    private val plain = card.copy(
        accountLast4 = "1111", isCreditCard = false, statementDay = null, cardDueDay = null, cardBillAmount = null,
        cardBillAsOf = null,
    )

    @Test
    fun `a card's bill survives the trip to disk`() {
        val back = RebuildAccountFieldsStore.decode(RebuildAccountFieldsStore.encode(listOf(card))).single()

        assertEquals(card.copy(smsSource = null), back)
    }

    @Test
    fun `only accounts with something set are kept`() {
        assertEquals(listOf("0000"), RebuildAccountFieldsStore.decode(RebuildAccountFieldsStore.encode(listOf(card, plain))).map { it.accountLast4 })
        assertNull(RebuildAccountFieldsStore.encode(listOf(plain)))
    }

    @Test
    fun `nothing saved, or unreadable, is nothing to restore`() {
        assertTrue(RebuildAccountFieldsStore.decode(null).isEmpty())
        assertTrue(RebuildAccountFieldsStore.decode("not json").isEmpty())
    }

    @Test
    fun `a bill cleared after an interrupted rebuild stays cleared at the next scan`() = runTest {
        val store = RebuildAccountFieldsStore(contextOverOneFile())
        // The rebuild saved the card, then stopped before any scan put it back.
        store.saveBeforeRebuild { listOf(card, card.copy(accountLast4 = "2222")) }

        // The user clears the recreated card's bill, then sets its statement day afresh.
        store.editCardBill("Test Bank", "0000") {}
        store.editStatementDay("Test Bank", "0000") {}

        // What the next scan would put back: nothing for this card, the other one untouched.
        val pending = store.pending().associateBy { it.accountLast4 }
        assertEquals(setOf("2222"), pending.keys)
        assertEquals(card.cardBillAmount, pending.getValue("2222").cardBillAmount)
    }

    @Test
    fun `an account with nothing left to put back is dropped`() = runTest {
        val store = RebuildAccountFieldsStore(contextOverOneFile())
        store.saveBeforeRebuild { listOf(card.copy(statementDay = null)) }

        store.editCardBill("Test Bank", "0000") {}

        assertTrue(store.pending().isEmpty())
    }

    @Test
    fun `an edit that lands while a scan puts the bill back stays as the user set it`() = runTest {
        val store = RebuildAccountFieldsStore(contextOverOneFile())
        store.saveBeforeRebuild { listOf(card) }
        // The card's rows, recreated by the scan without the bill.
        var rowBill: BigDecimal? = null
        val restoring = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()

        val scan = launch {
            store.restore { saved ->
                // The scan has read the saved bill and is about to write it back.
                restoring.complete(Unit)
                finish.await()
                if (rowBill == null) rowBill = saved.cardBillAmount
                1
            }
        }
        restoring.await()
        // The user clears the bill in the middle of that.
        val edit = launch { store.editCardBill("Test Bank", "0000") { rowBill = null } }
        runCurrent()
        finish.complete(Unit)
        joinAll(scan, edit)

        assertNull(rowBill)
        assertTrue(store.pending().isEmpty())
    }

    private fun contextOverOneFile(): Context {
        val saved = AtomicReference<String?>(null)
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putString(any(), any()) } answers { saved.set(secondArg()); editor }
        every { editor.remove(any()) } answers { saved.set(null); editor }
        every { editor.commit() } returns true
        val prefs = mockk<SharedPreferences>()
        every { prefs.getString(any(), any()) } answers { saved.get() }
        every { prefs.edit() } returns editor
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return context
    }
}
