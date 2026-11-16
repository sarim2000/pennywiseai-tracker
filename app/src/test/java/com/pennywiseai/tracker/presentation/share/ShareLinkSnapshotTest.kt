package com.pennywiseai.tracker.presentation.share

import com.pennywiseai.tracker.data.share.ShareLinkExpiry
import com.pennywiseai.tracker.data.share.ShareSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class ShareLinkSnapshotTest {

    private val en = Locale.Builder().setLanguage("en").setRegion("IN").build()
    private val ar = Locale.Builder().setLanguage("ar").setRegion("SA").build()

    private fun encoded(content: ShareCardContent, expiry: ShareLinkExpiry = ShareLinkExpiry.DEFAULT, locale: Locale = en): JsonObject =
        Json.parseToJsonElement(ShareSnapshot.encode(content.toSnapshot(ShareTheme.AURORA, locale, expiry))).jsonObject

    @Test
    fun `a card becomes the v1 contract with lowercase names`() {
        val json = encoded(previewSpending)
        assertEquals(1, json["v"]!!.jsonPrimitive.int)
        assertEquals("spending", json["kind"]!!.jsonPrimitive.content)
        assertEquals("aurora", json["theme"]!!.jsonPrimitive.content)
        assertEquals("en-IN", json["locale"]!!.jsonPrimitive.content)
        assertEquals("My spending", json["title"]!!.jsonPrimitive.content)
        assertEquals("OCTOBER 2026", json["periodLabel"]!!.jsonPrimitive.content)
        val hero = json["hero"]!!.jsonObject
        assertEquals("+12%", hero["value"]!!.jsonPrimitive.content)
        assertEquals("+₹4,120", hero["delta"]!!.jsonPrimitive.content)
        assertEquals("neutral", hero["sentiment"]!!.jsonPrimitive.content)
        assertEquals(2, json["stats"]!!.jsonArray.size)
        val chart = json["chart"]!!.jsonObject
        assertEquals("diverging", chart["type"]!!.jsonPrimitive.content)
        assertEquals("−₹900", chart["points"]!!.jsonArray[2].jsonObject["display"]!!.jsonPrimitive.content)
        assertEquals(30, json["expiresInDays"]!!.jsonPrimitive.int)
        // Every key is present, nulls included, so the server never guesses.
        assertEquals(
            setOf("v", "kind", "locale", "theme", "title", "subtitle", "periodLabel", "hero", "stats", "chart", "achievements", "expiresInDays"),
            json.keys,
        )
    }

    @Test
    fun `never expiring is sent as an explicit null`() {
        val json = encoded(previewSpending, ShareLinkExpiry.NEVER)
        assertEquals(JsonNull, json["expiresInDays"])
        assertEquals(7, encoded(previewSpending, ShareLinkExpiry.WEEK)["expiresInDays"]!!.jsonPrimitive.int)
        assertEquals(90, encoded(previewSpending, ShareLinkExpiry.QUARTER)["expiresInDays"]!!.jsonPrimitive.int)
    }

    @Test
    fun `the emphasised point is spelled out as the card draws it`() {
        // Bars fall back to the first, a line to its latest point.
        val bars = previewRecap.copy(chart = previewRecap.chart!!.copy(highlightIndex = null))
        assertEquals(0, bars.toSnapshot(ShareTheme.MIDNIGHT, en, ShareLinkExpiry.DEFAULT).chart!!.highlightIndex)
        val line = previewSavings.copy(chart = previewSavings.chart!!.copy(highlightIndex = null))
        assertEquals(5, line.toSnapshot(ShareTheme.MIDNIGHT, en, ShareLinkExpiry.DEFAULT).chart!!.highlightIndex)
        assertNull(previewSpending.toSnapshot(ShareTheme.MIDNIGHT, en, ShareLinkExpiry.DEFAULT).chart!!.highlightIndex)
    }

    @Test
    fun `a ring stays within 0 to 1 and values stay finite`() {
        val ring = previewGoal.copy(chart = ShareChart(ShareChartType.RING, listOf(ShareChartPoint("", 1.4, "140%"))))
        assertEquals(1.0, ring.toSnapshot(ShareTheme.PAPER, en, ShareLinkExpiry.DEFAULT).chart!!.points.single().value, 0.0)
        val nan = previewRecap.copy(chart = ShareChart(ShareChartType.BARS, listOf(ShareChartPoint("Food", Double.NaN, null))))
        assertEquals(0.0, nan.toSnapshot(ShareTheme.PAPER, en, ShareLinkExpiry.DEFAULT).chart!!.points.single().value, 0.0)
    }

    @Test
    fun `achievements carry their key, tier and ISO date`() {
        val dated = previewAchievement.copy(
            achievements = previewAchievement.achievements.map { it.copy(unlockedOn = LocalDate.parse("2026-10-14")) },
        )
        val a = encoded(dated)["achievements"]!!.jsonArray.single().jsonObject
        assertEquals("transactions_tracked", a["id"]!!.jsonPrimitive.content)
        assertEquals(3, a["tier"]!!.jsonPrimitive.int)
        assertEquals("2026-10-14", a["unlockedOn"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, encoded(previewAchievement)["achievements"]!!.jsonArray.single().jsonObject["unlockedOn"])
    }

    @Test
    fun `long text is cut to the server's limit in code points`() {
        val name = "🏠".repeat(100) // 100 emoji: 200 chars, 100 code points
        val snapshot = previewGoal.copy(title = name).toSnapshot(ShareTheme.MIDNIGHT, en, ShareLinkExpiry.DEFAULT)
        assertEquals(80, snapshot.title.codePointCount(0, snapshot.title.length))
        assertTrue(snapshot.title.endsWith("…"))
        // Exactly at the limit is left alone.
        val exact = "a".repeat(80)
        assertEquals(exact, exact.fit(80))
    }

    @Test
    fun `line breaks become spaces and control characters go`() {
        assertEquals("Trip to the coast", "Trip\nto the\u0007 coast".fit(80).replace("  ", " "))
        assertTrue("a\tb\r\nc".fit(80).none { it.code < 0x20 })
        // Bidi isolates are formatting, not control characters, and must survive.
        assertEquals("⁦+₹1,200⁩", "⁦+₹1,200⁩".fit(30))
    }

    @Test
    fun `a hero or stat without words is dropped rather than sent blank`() {
        val card = previewGoal.copy(
            hero = ShareCardHero(label = " ", value = "68%"),
            stats = listOf(ShareCardStat("", "On track"), ShareCardStat("Pace", "On track")),
        )
        val snapshot = card.toSnapshot(ShareTheme.MIDNIGHT, en, ShareLinkExpiry.DEFAULT)
        assertNull(snapshot.hero)
        assertEquals(listOf("Pace"), snapshot.stats.map { it.label })
    }

    @Test
    fun `the locale is language and region only`() {
        assertEquals("ar-SA", encoded(previewRecap, locale = ar)["locale"]!!.jsonPrimitive.content)
        assertEquals("ar-SA", shareLocaleTag(Locale.forLanguageTag("ar-SA-u-nu-arab")))
        assertEquals("fr", shareLocaleTag(Locale.forLanguageTag("fr")))
        assertEquals("en", shareLocaleTag(Locale.ROOT))
    }
}
