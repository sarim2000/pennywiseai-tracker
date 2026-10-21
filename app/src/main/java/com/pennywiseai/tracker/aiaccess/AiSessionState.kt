package com.pennywiseai.tracker.aiaccess

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The running AI session, shared by the service, the AI access screen and the tile. In memory only. */
@Singleton
class AiSessionState @Inject constructor() {
    data class Session(val endsAtMillis: Long, val lineUp: Boolean)

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    internal fun set(value: Session?) {
        _session.value = value
    }
}
