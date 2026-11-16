package com.pennywiseai.tracker.data.share

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class ShareLinkClientTest {

    private val created = """{"id":"AbCdEfGhIjKlMnOpQrStUv","url":"https://pennywise.zynth.dev/s/AbCdEfGhIjKlMnOpQrStUv",""" +
        """"deleteToken":"tok","expiresAt":"2026-12-16T05:16:35Z"}"""

    private fun client(
        server: String = SHARE_SERVER,
        requests: MutableList<HttpRequestData> = mutableListOf(),
        respond: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = ShareLinkClient(
        MockEngine { request ->
            requests += request
            respond(request)
        },
        server,
    )

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private inline fun <reified T : ShareLinkError> assertFails(block: () -> Unit): T {
        try {
            block()
        } catch (e: ShareLinkError) {
            if (e is T) return e
            fail("expected ${T::class.simpleName}, got $e")
        }
        fail("expected ${T::class.simpleName}")
        throw AssertionError()
    }

    @Test
    fun `publish posts the snapshot and the image as the server's two parts`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val share = client(requests = requests) { json(created, HttpStatusCode.Created) }
            .publish("""{"v":1}""", byteArrayOf(1, 2, 3))

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://pennywise.zynth.dev/api/shares", request.url.toString())
        assertTrue(request.body.contentType.toString().startsWith("multipart/form-data"))
        val body = request.body.toByteArray().decodeToString()
        assertTrue(body, body.contains("Content-Disposition: form-data; name=\"snapshot\"\r\nContent-Type: application/json"))
        assertTrue(body, body.contains("""{"v":1}"""))
        // A file part: the server reads only those as raw bytes.
        assertTrue(body, body.contains("Content-Disposition: form-data; name=\"og\"; filename=\"og.png\"\r\nContent-Type: image/png"))

        assertEquals("AbCdEfGhIjKlMnOpQrStUv", share.id)
        assertEquals("https://pennywise.zynth.dev/s/AbCdEfGhIjKlMnOpQrStUv", share.url)
        assertEquals("tok", share.deleteToken)
        assertEquals("2026-12-16T05:16:35Z", share.expiresAt)
    }

    @Test
    fun `a local server's link points at the local server`() = runBlocking {
        val share = client(server = "http://localhost:8080") { json(created, HttpStatusCode.Created) }
            .publish("{}", byteArrayOf(1))
        assertEquals("http://localhost:8080/s/AbCdEfGhIjKlMnOpQrStUv", share.url)
    }

    @Test
    fun `a never-expiring link comes back without a date`() = runBlocking {
        val body = """{"id":"x","url":"https://pennywise.zynth.dev/s/x","deleteToken":"t","expiresAt":null}"""
        val share = client { json(body, HttpStatusCode.Created) }.publish("{}", byteArrayOf(1))
        assertEquals(null, share.expiresAt)
    }

    @Test
    fun `an image over the upload limit is refused before sending`() {
        val requests = mutableListOf<HttpRequestData>()
        assertFails<ShareLinkError.TooLarge> {
            runBlocking {
                client(requests = requests) { json(created, HttpStatusCode.Created) }
                    .publish("{}", ByteArray(ShareLinkClient.MAX_BODY_BYTES.toInt()))
            }
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `server answers map to their own errors`() {
        val rejected = assertFails<ShareLinkError.Rejected> {
            runBlocking { client { json("""{"error":"title is required"}""", HttpStatusCode.BadRequest) }.publish("{}", byteArrayOf(1)) }
        }
        assertEquals("title is required", rejected.reason)
        assertFails<ShareLinkError.RateLimited> {
            runBlocking { client { json("""{"error":"slow down"}""", HttpStatusCode.TooManyRequests) }.publish("{}", byteArrayOf(1)) }
        }
        val server = assertFails<ShareLinkError.Server> {
            runBlocking { client { respond("", HttpStatusCode.BadGateway) }.publish("{}", byteArrayOf(1)) }
        }
        assertEquals(502, server.status)
        // A 201 that isn't the expected JSON is the server's fault, not a success.
        assertFails<ShareLinkError.Server> {
            runBlocking { client { json("<html>", HttpStatusCode.Created) }.publish("{}", byteArrayOf(1)) }
        }
    }

    @Test
    fun `no connection is a network error`() {
        assertFails<ShareLinkError.Network> {
            runBlocking { client { throw IOException("offline") }.publish("{}", byteArrayOf(1)) }
        }
    }

    @Test
    fun `delete sends the token and treats a link the server no longer has as deleted`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        client(requests = requests) { respond("", HttpStatusCode.NoContent) }.delete("abc", "tok")
        val request = requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("https://pennywise.zynth.dev/api/shares/abc", request.url.toString())
        assertEquals("Bearer tok", request.headers[HttpHeaders.Authorization])

        client { respond("", HttpStatusCode.NotFound) }.delete("abc", "tok")
        client { respond("", HttpStatusCode.Gone) }.delete("abc", "tok")
    }

    @Test
    fun `a wrong token is forbidden`() {
        assertFails<ShareLinkError.Forbidden> {
            runBlocking { client { json("""{"error":"Wrong delete token"}""", HttpStatusCode.Forbidden) }.delete("abc", "bad") }
        }
    }
}
