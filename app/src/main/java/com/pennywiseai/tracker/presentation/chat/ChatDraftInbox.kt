package com.pennywiseai.tracker.presentation.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A question asked from outside Chat (the quick add mic), waiting for the message box. Chat
 * takes it only once its box is on screen, so with no model yet it waits rather than being
 * lost; it is never sent by itself. In memory only.
 *
 * Switching to Chat for it is a separate, one-time request ([openChat]): a draft still waiting
 * for the model mustn't send every later launch of the app to Chat.
 */
@Singleton
class ChatDraftInbox @Inject constructor() {
    /** [id] tells two drafts of the same words apart. */
    data class Draft(val text: String, val id: Long)

    private val ids = AtomicLong()
    private val _draft = MutableStateFlow<Draft?>(null)
    val draft: StateFlow<Draft?> = _draft.asStateFlow()

    private val _openChat = MutableStateFlow(false)

    /** Set once the app is unlocked and back on Home for a new draft; Home then shows Chat. */
    val openChat: StateFlow<Boolean> = _openChat.asStateFlow()

    fun requestOpen() {
        _openChat.value = true
    }

    /** Chat was shown (or can't be): later Homes stay where they are. */
    fun opened() {
        _openChat.value = false
    }

    fun put(text: String) {
        text.trim().takeIf { it.isNotEmpty() }?.let { _draft.value = Draft(it, ids.incrementAndGet()) }
    }

    /** Clears [taken] unless a newer draft replaced it meanwhile. */
    fun take(taken: Draft) = _draft.update { if (it?.id == taken.id) null else it }
}
