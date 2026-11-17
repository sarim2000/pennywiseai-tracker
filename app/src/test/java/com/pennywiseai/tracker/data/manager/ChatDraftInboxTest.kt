package com.pennywiseai.tracker.data.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A question carried into Chat from the quick add mic. */
class ChatDraftInboxTest {

    @Test
    fun `a question waits until Chat takes it`() {
        val inbox = ChatDraftInbox()
        inbox.put("  how much on food this month  ")
        val draft = inbox.draft.value!!
        assertEquals("how much on food this month", draft.text)
        // Not taken (no model yet): still there.
        assertEquals(draft, inbox.draft.value)
        inbox.take(draft)
        assertNull(inbox.draft.value)
    }

    @Test
    fun `taking an old draft leaves a newer one`() {
        val inbox = ChatDraftInbox()
        inbox.put("first")
        val old = inbox.draft.value!!
        inbox.put("second")
        inbox.take(old)
        assertEquals("second", inbox.draft.value?.text)
    }

    @Test
    fun `the same words twice are two drafts`() {
        val inbox = ChatDraftInbox()
        inbox.put("same")
        val first = inbox.draft.value!!
        inbox.take(first)
        inbox.put("same")
        assertNotEquals(first.id, inbox.draft.value!!.id)
    }

    @Test
    fun `switching to Chat is asked once, apart from the draft`() {
        val inbox = ChatDraftInbox()
        inbox.put("how much on food")
        assertFalse(inbox.openChat.value)
        inbox.requestOpen()
        assertTrue(inbox.openChat.value)
        inbox.opened()
        // No model yet: the words still wait for Chat, but a later launch stays on Home.
        assertFalse(inbox.openChat.value)
        assertEquals("how much on food", inbox.draft.value?.text)
    }

    @Test
    fun `nothing said is no draft`() {
        val inbox = ChatDraftInbox()
        inbox.put("   ")
        assertNull(inbox.draft.value)
    }
}
