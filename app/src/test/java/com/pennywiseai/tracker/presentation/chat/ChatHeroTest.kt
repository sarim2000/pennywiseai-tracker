package com.pennywiseai.tracker.presentation.chat

import com.pennywiseai.tracker.data.database.entity.ChatMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHeroTest {
    private val welcome = ChatMessage(message = "welcome", isUser = false)
    private val asked = ChatMessage(message = "question", isUser = true)
    private val answer = ChatMessage(message = "answer", isUser = false)

    @Test
    fun `an empty chat shows the hero above its welcome`() {
        assertTrue(showsHero(listOf(welcome), isLoading = false))
    }

    @Test
    fun `the hero goes as soon as the first request starts`() {
        // A quick action or a send flags loading before anything is saved.
        assertFalse(showsHero(listOf(welcome), isLoading = true))
        assertFalse(showsHero(listOf(asked), isLoading = true))
        assertFalse(showsHero(listOf(asked, answer), isLoading = false))
    }

    @Test
    fun `nothing loaded yet shows no hero, so history coming in can't flash it`() {
        assertFalse(showsHero(emptyList(), isLoading = false))
    }
}
