package com.example

import com.example.share.ShareClientKey
import com.example.share.ShareService
import com.example.share.configureShares
import com.example.ui.BankViews
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import io.ktor.server.testing.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.sql.DriverManager
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.zip.CRC32
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShareRoutesTest {

    private class MutableClock(var now: Instant = Instant.parse("2026-11-01T10:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    private val clock = MutableClock()

    private fun shareApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        // Postgres mode, because that is what the DDL and queries are written for.
        val connection = DriverManager.getConnection(
            "jdbc:h2:mem:shares-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "",
        )
        application {
            configureShares(ShareService(connection, clock))
            routing { staticResources("/static", "static") }
        }
        block()
    }

    private fun png(width: Int = 1200, height: Int = 630, comment: String? = null): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { color = Color(0x0F1626); fillRect(0, 0, width, height); dispose() }
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        return if (comment == null) bytes else withTextChunk(bytes, comment)
    }

    /** Splices a tEXt chunk in after IHDR — standing in for metadata a phone encoder might add. */
    private fun withTextChunk(png: ByteArray, text: String): ByteArray {
        val data = "Comment\u0000$text".toByteArray(Charsets.ISO_8859_1)
        val type = "tEXt".toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(type); update(data) }.value.toInt()
        val chunk = ByteBuffer.allocate(12 + data.size).putInt(data.size).put(type).put(data).putInt(crc).array()
        val ihdrEnd = 8 + 25
        return png.copyOfRange(0, ihdrEnd) + chunk + png.copyOfRange(ihdrEnd, png.size)
    }

    private fun snapshot(
        title: String = "October recap",
        locale: String = "en",
        kind: String = "recap",
        extra: String = "",
        expires: String = "30",
        chart: String = """{"type":"bars","points":[{"label":"Aug","value":410.5,"display":"$410.50"},{"label":"Sep","value":380,"display":"$380"},{"label":"Oct","value":295,"display":"$295"}],"highlightIndex":2}""",
    ) = """
        {"v":1,"kind":"$kind","locale":"$locale","theme":"midnight","title":${Json.encodeToString(String.serializer(), title)},
         "subtitle":"Spent less than last month","periodLabel":"Oct 2026",
         "hero":{"label":"Saved this month","value":"$1,240","delta":"+18%","sentiment":"good"},
         "stats":[{"label":"Income","value":"$4,000","sentiment":"neutral"},{"label":"Savings rate","value":"31%","sentiment":"good"}],
         "chart":$chart,
         "achievements":[{"id":"first_budget","title":"First budget","description":"Set up a budget","tier":1,"unlockedOn":"2026-10-03"}],
         "expiresInDays":$expires $extra}
    """.trimIndent()

    private suspend fun ApplicationTestBuilder.publish(
        snapshotJson: String = snapshot(),
        og: ByteArray? = png(),
        userAgent: String = "PennyWise-Test",
        clientIp: String? = null,
    ): HttpResponse = client.post("/api/shares") {
        header(HttpHeaders.UserAgent, userAgent)
        if (clientIp != null) header("CF-Connecting-IP", clientIp)
        setBody(
            MultiPartFormDataContent(
                formData {
                    append("snapshot", snapshotJson, Headers.build { append(HttpHeaders.ContentType, "application/json") })
                    if (og != null) {
                        append(
                            "og", og,
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/png")
                                append(HttpHeaders.ContentDisposition, "filename=\"og.png\"")
                            },
                        )
                    }
                },
            ),
        )
    }

    private suspend fun ApplicationTestBuilder.publishOk(snapshotJson: String = snapshot()): Pair<String, String> {
        val res = publish(snapshotJson)
        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        return body["id"]!!.jsonPrimitive.content to body["deleteToken"]!!.jsonPrimitive.content
    }

    @Test
    fun `publish returns an unguessable id, a delete token and the expiry`() = shareApp {
        val res = publish()
        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        val id = body["id"]!!.jsonPrimitive.content
        assertTrue(Regex("^[A-Za-z0-9_-]{22}$").matches(id), id)
        assertEquals("https://pennywise.zynth.dev/s/$id", body["url"]!!.jsonPrimitive.content)
        assertTrue(body["deleteToken"]!!.jsonPrimitive.content.length >= 43)
        assertEquals("2026-12-01T10:00:00Z", body["expiresAt"]!!.jsonPrimitive.content)
    }

    @Test
    fun `never-expiring share reports a null expiry`() = shareApp {
        val res = publish(snapshot(expires = "null"))
        assertEquals(HttpStatusCode.Created, res.status)
        assertEquals(JsonNull, Json.parseToJsonElement(res.bodyAsText()).jsonObject["expiresAt"])
    }

    @Test
    fun `share page renders the snapshot with unlisted preview tags`() = shareApp {
        val (id, _) = publishOk()
        val res = client.get("/s/$id")
        assertEquals(HttpStatusCode.OK, res.status)
        val html = res.bodyAsText()
        assertTrue(html.contains("""<meta name="robots" content="noindex">"""))
        assertTrue(html.contains("""property="og:image" content="https://pennywise.zynth.dev/s/$id/og.png""""))
        assertTrue(html.contains("""property="og:image:width" content="1200""""))
        assertTrue(html.contains("""property="og:image:height" content="630""""))
        assertTrue(html.contains("""name="twitter:card" content="summary_large_image""""))
        assertTrue(html.contains("""property="og:title" content="October recap""""))
        assertTrue(html.contains("$1,240"))
        assertTrue(html.contains("Savings rate"))
        assertTrue(html.contains("First budget"))
        assertTrue(html.contains("<svg class=\"plot\""))
        assertTrue(html.contains("""action="/s/$id/report""""))
        assertTrue(html.contains("dir=\"ltr\""))
        // The OG image is for the chat preview only, never the page's content.
        assertFalse(html.contains("<img src=\"/s/$id/og.png\""))
        assertEquals("public, max-age=300", res.headers[HttpHeaders.CacheControl])
        assertEquals("noindex", res.headers["X-Robots-Tag"])
        assertNotNull(res.headers["Content-Security-Policy"])
        assertFalse(res.headers.contains(HttpHeaders.SetCookie))
    }

    @Test
    fun `og image is re-encoded without metadata and cached briefly`() = shareApp {
        val res0 = publish(og = png(comment = "device-model-xyz"))
        assertEquals(HttpStatusCode.Created, res0.status)
        val id = Json.parseToJsonElement(res0.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content
        val res = client.get("/s/$id/og.png")
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(ContentType.Image.PNG, res.contentType()?.withoutParameters())
        assertEquals("public, max-age=300", res.headers[HttpHeaders.CacheControl])
        val bytes = res.readRawBytes()
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("device-model-xyz"))
        val image = ImageIO.read(bytes.inputStream())
        assertEquals(1200, image.width)
        assertEquals(630, image.height)
    }

    @Test
    fun `delete needs the right token and leaves a 410`() = shareApp {
        val (id, token) = publishOk()
        assertEquals(HttpStatusCode.Forbidden, client.delete("/api/shares/$id").status)
        assertEquals(HttpStatusCode.Forbidden, client.delete("/api/shares/$id") { bearerAuth("not-the-token") }.status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/shares/AAAAAAAAAAAAAAAAAAAAAA") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/s/$id").status)

        assertEquals(HttpStatusCode.NoContent, client.delete("/api/shares/$id") { bearerAuth(token) }.status)
        val page = client.get("/s/$id")
        assertEquals(HttpStatusCode.Gone, page.status)
        assertTrue(page.bodyAsText().contains("no longer available"))
        assertEquals(HttpStatusCode.Gone, client.get("/s/$id/og.png").status)
    }

    @Test
    fun `unknown ids are a friendly 404`() = shareApp {
        val res = client.get("/s/AAAAAAAAAAAAAAAAAAAAAA")
        assertEquals(HttpStatusCode.NotFound, res.status)
        assertTrue(res.bodyAsText().contains("doesn't exist"))
        assertEquals(HttpStatusCode.NotFound, client.get("/s/not-an-id").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/s/AAAAAAAAAAAAAAAAAAAAAA/og.png").status)
        assertEquals(HttpStatusCode.NotFound, client.post("/s/AAAAAAAAAAAAAAAAAAAAAA/report").status)
    }

    @Test
    fun `three distinct reporters hide a share, one reporter cannot`() = shareApp {
        val (id, _) = publishOk()
        // One visitor can't buy new identities by varying headers they control.
        listOf("a", "b", "c").forEachIndexed { i, ua ->
            val res = client.post("/s/$id/report") {
                header("CF-Connecting-IP", "203.0.113.1")
                header(HttpHeaders.UserAgent, ua)
                header(HttpHeaders.XForwardedFor, "198.51.100.$i")
            }
            assertEquals(HttpStatusCode.NoContent, res.status)
        }
        assertEquals(HttpStatusCode.OK, client.get("/s/$id").status)
        client.post("/s/$id/report") { header("CF-Connecting-IP", "203.0.113.2") }
        assertEquals(HttpStatusCode.OK, client.get("/s/$id").status)
        client.post("/s/$id/report") { header("CF-Connecting-IP", "203.0.113.3") }
        assertEquals(HttpStatusCode.Gone, client.get("/s/$id").status)
        assertEquals(HttpStatusCode.Gone, client.get("/s/$id/og.png").status)
    }

    @Test
    fun `shares expire after the chosen number of days`() = shareApp {
        val (id, _) = publishOk(snapshot(expires = "7"))
        clock.now = clock.now.plus(Duration.ofDays(6))
        assertEquals(HttpStatusCode.OK, client.get("/s/$id").status)
        clock.now = clock.now.plus(Duration.ofDays(2))
        assertEquals(HttpStatusCode.Gone, client.get("/s/$id").status)
        assertEquals(HttpStatusCode.Gone, client.get("/s/$id/og.png").status)
        // Publishing anything purges expired content; the tombstone still answers 410.
        publishOk()
        assertEquals(HttpStatusCode.Gone, client.get("/s/$id").status)
    }

    @Test
    fun `cache lifetime never runs past the share's expiry`() = shareApp {
        val (id, _) = publishOk(snapshot(expires = "7"))
        clock.now = clock.now.plus(Duration.ofDays(7)).minusSeconds(120)
        assertEquals("public, max-age=120", client.get("/s/$id").headers[HttpHeaders.CacheControl])
        assertEquals("public, max-age=120", client.get("/s/$id/og.png").headers[HttpHeaders.CacheControl])
        // A never-expiring share keeps the full five minutes.
        val (forever, _) = publishOk(snapshot(expires = "null"))
        assertEquals("public, max-age=300", client.get("/s/$forever").headers[HttpHeaders.CacheControl])
        assertEquals("public, max-age=300", client.get("/s/$forever/og.png").headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `invalid uploads are rejected with a reason`() = shareApp {
        fun reason(res: String) = Json.parseToJsonElement(res).jsonObject["error"]!!.jsonPrimitive.content
        val cases = listOf(
            snapshot().replace("\"v\":1", "\"v\":2") to "version",
            snapshot(kind = "net-worth") to "kind",
            snapshot(locale = "en_US!") to "locale",
            snapshot(title = "x".repeat(81)) to "title",
            snapshot(title = " ") to "title",
            snapshot(title = "line\u0001break") to "control",
            snapshot(expires = "14") to "expiresInDays",
            snapshot(chart = """{"type":"ring","points":[{"label":"Goal","value":1.5}]}""") to "ring",
            snapshot(chart = """{"type":"pie","points":[{"label":"a","value":1}]}""") to "chart.type",
            snapshot(chart = """{"type":"bars","points":[{"label":"a","value":1}],"highlightIndex":3}""") to "highlightIndex",
            snapshot(chart = """{"type":"bars","points":[${(1..13).joinToString(",") { """{"label":"$it","value":$it}""" }}]}""") to "points",
            snapshot().replace("\"stats\":[", "\"stats\":[" + (1..5).joinToString("") { """{"label":"s$it","value":"$it","sentiment":"good"},""" }) to "stats",
            snapshot().replace("\"tier\":1", "\"tier\":6") to "tier",
            snapshot().replace("2026-10-03", "2026-13-40") to "unlockedOn",
            snapshot().replace("\"sentiment\":\"good\"}", "\"sentiment\":\"great\"}") to "sentiment",
            "{not json" to "JSON",
        )
        cases.forEach { (json, expected) ->
            val res = publish(json)
            assertEquals(HttpStatusCode.BadRequest, res.status, "expected 400 for '$expected'")
            assertTrue(reason(res.bodyAsText()).contains(expected), "'$expected' not in: ${res.bodyAsText()}")
        }

        assertTrue(reason(publish(og = png(100, 100)).bodyAsText()).contains("1200x630"))
        assertTrue(reason(publish(og = "GIF89a not a png".toByteArray()).bodyAsText()).contains("PNG"))
        assertTrue(reason(publish(og = null).bodyAsText()).contains("og"))
        assertTrue(reason(publish(og = ByteArray(1_600_000)).bodyAsText()).contains("larger"))
        val notMultipart = client.post("/api/shares") { contentType(ContentType.Application.Json); setBody(snapshot()) }
        assertEquals(HttpStatusCode.BadRequest, notMultipart.status)
    }

    @Test
    fun `unknown theme falls back to midnight`() = shareApp {
        val (id, _) = publishOk(snapshot().replace("\"theme\":\"midnight\"", "\"theme\":\"neon\""))
        assertTrue(client.get("/s/$id").bodyAsText().contains("share-page theme-midnight"))
    }

    @Test
    fun `text fields are escaped, never executed`() = shareApp {
        val evil = """<script>alert(1)</script>"><img src=x onerror=alert(2)>"""
        val json = snapshot(title = evil.take(80)).replace("\"label\":\"Income\"", "\"label\":${Json.encodeToString(String.serializer(), evil.take(40))}")
        val (id, _) = publishOk(json)
        val html = client.get("/s/$id").bodyAsText()
        assertFalse(html.contains("<script>alert"), "raw script tag in page")
        assertFalse(html.contains("<img src=x"), "raw img tag in page")
        assertFalse(Regex("""content="[^"]*"><img""").containsMatchIn(html), "attribute breakout in meta tags")
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
    }

    @Test
    fun `arabic snapshot renders right to left with chrome in arabic`() = shareApp {
        val (id, _) = publishOk(snapshot(title = "ملخص أكتوبر", locale = "ar-SA"))
        val html = client.get("/s/$id").bodyAsText()
        assertTrue(html.contains("""lang="ar-SA""""))
        assertTrue(html.contains("""dir="rtl""""))
        assertTrue(html.contains("ملخص أكتوبر"))
        assertTrue(html.contains("صُنع بواسطة PennyWise"))
        assertEquals(HttpStatusCode.OK, client.get("/static/fonts/ibm-plex-sans-arabic-400-arabic.woff2").status)
    }

    @Test
    fun `publishing is rate limited per client ip`() = shareApp {
        val ip = "2001:db8:1:2:aaaa::1"
        repeat(ShareService.MAX_SHARES_PER_HOUR) { assertEquals(HttpStatusCode.Created, publish(clientIp = ip).status) }
        assertEquals(HttpStatusCode.TooManyRequests, publish(clientIp = ip).status)
        // Turned away before the upload is read: a bad image gets 429, not the 400 its decode would give.
        assertEquals(HttpStatusCode.TooManyRequests, publish(clientIp = ip, og = png(100, 100)).status)
        // A new User-Agent or another address in the same IPv6 /64 is the same client.
        assertEquals(HttpStatusCode.TooManyRequests, publish(clientIp = ip, userAgent = "another-device").status)
        assertEquals(HttpStatusCode.TooManyRequests, publish(clientIp = "2001:db8:1:2:bbbb::9").status)
        assertEquals(HttpStatusCode.Created, publish(clientIp = "2001:db8:1:3::1").status)
        clock.now = clock.now.plus(Duration.ofMinutes(61))
        assertEquals(HttpStatusCode.Created, publish(clientIp = ip).status)
    }

    @Test
    fun `parallel uploads can't get past the hourly cap`() = runBlocking {
        val connection = DriverManager.getConnection("jdbc:h2:mem:shares-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")
        val service = ShareService(connection, clock)
        val snapshot = com.example.share.ShareSnapshotValidator.parse(snapshot()).getOrThrow()
        val image = png()
        val results = (1..60).map { async(Dispatchers.Default) { service.create(snapshot, image, "one-client") } }.awaitAll()
        assertEquals(ShareService.MAX_SHARES_PER_HOUR, results.count { it is ShareService.CreateResult.Ok })
    }

    @Test
    fun `client key cuts ipv6 to its 64 and never parses hostnames`() {
        assertEquals("203.0.113.7", ShareClientKey.normalize("203.0.113.7"))
        assertEquals(ShareClientKey.normalize("2001:db8::1"), ShareClientKey.normalize("2001:db8::ffff:1"))
        assertEquals("example.com", ShareClientKey.normalize("example.com"))
    }

    @Test
    fun `share pages stay out of the sitemap but crawlable for previews`() {
        assertFalse(BankViews.buildSitemap().contains("/s/"))
        assertFalse(BankViews.buildRobots().contains("Disallow"))
    }
}
