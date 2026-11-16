package com.pennywiseai.tracker.data.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ShareLinkStoreTest {

    private val now = Instant.parse("2026-11-16T12:00:00Z")

    private fun link(id: String, expiresAt: String?) =
        SharedLink(id = id, url = "https://pennywise.zynth.dev/s/$id", deleteToken = "t-$id", expiresAt = expiresAt, title = "My recap", kind = "RECAP")

    @Test
    fun `links survive a round trip`() {
        val links = listOf(link("a", "2026-12-16T05:16:35Z"), link("b", null))
        assertEquals(links, decodeLinks(encodeLinks(links)))
    }

    @Test
    fun `nothing stored and a corrupt list both read as no links`() {
        assertEquals(emptyList<SharedLink>(), decodeLinks(null))
        assertEquals(emptyList<SharedLink>(), decodeLinks("not json"))
    }

    @Test
    fun `expired links are hidden and never-expiring ones kept`() {
        val links = listOf(
            link("past", "2026-11-16T11:59:59Z"),
            link("now", "2026-11-16T12:00:00Z"),
            link("future", "2026-11-16T12:00:01Z"),
            link("never", null),
        )
        assertEquals(listOf("future", "never"), links.live(now).map { it.id })
    }

    @Test
    fun `an unreadable date never hides a link`() {
        val odd = link("odd", "next tuesday")
        assertFalse(odd.isExpired(now))
        assertTrue(listOf(odd).live(now).isNotEmpty())
    }
}
