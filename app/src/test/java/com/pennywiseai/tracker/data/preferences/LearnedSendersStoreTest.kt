package com.pennywiseai.tracker.data.preferences

import com.pennywiseai.tracker.data.preferences.LearnedSendersStore.Account
import com.pennywiseai.tracker.data.preferences.LearnedSendersStore.Companion.accountToUse
import com.pennywiseai.tracker.data.preferences.LearnedSendersStore.Companion.normalize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #994 step 3: the sender the user added from, and the account its next SMS is offered with. */
class LearnedSendersStoreTest {

    @Test
    fun `one sender through any operator prefix and DLT suffix`() {
        assertEquals("KBLBNK", normalize("JD-KBLBNK-S"))
        assertEquals("KBLBNK", normalize("VM-KBLBNK"))
        assertEquals("KBLBNK", normalize("kblbnk"))
        assertEquals("REVOLUT", normalize("Revolut"))
    }

    private val card = Account("Test Bank", "1234")

    @Test
    fun `the learned account fills in when the SMS names none or the same digits`() {
        assertEquals(card, accountToUse(card, null))
        assertEquals(card, accountToUse(card, "1234"))
    }

    @Test
    fun `a second card at the same bank is not booked to the first`() {
        assertNull(accountToUse(card, "9876"))
    }
}
