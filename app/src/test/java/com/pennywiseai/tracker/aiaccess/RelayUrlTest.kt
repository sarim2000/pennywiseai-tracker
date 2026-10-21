package com.pennywiseai.tracker.aiaccess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayUrlTest {
    @Test
    fun `accepts https relays and strips the connector path`() {
        assertEquals("https://relay.example.com", AiRelayClient.normalizeRelayUrl(" https://relay.example.com/mcp/ "))
        assertEquals("https://example.com/pw", AiRelayClient.normalizeRelayUrl("https://example.com/pw/"))
    }

    @Test
    fun `rejects anything that isn't https`() {
        assertNull(AiRelayClient.normalizeRelayUrl("http://relay.example.com"))
        assertNull(AiRelayClient.normalizeRelayUrl("relay.example.com"))
        assertNull(AiRelayClient.normalizeRelayUrl("https://"))
    }
}
