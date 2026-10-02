package com.pennywiseai.tracker.data.webhook

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookDeliveryServiceTest {

    private val sampleEnvelope = WebhookEnvelope(
        generatedAt = "2026-01-01T00:00:00",
        app = WebhookAppInfo(name = "PennyWise", version = "test"),
        profile = WebhookProfileInfo(id = "profile-1", name = "Test"),
        request = WebhookRequestInfo(
            range = "today",
            start = "2026-01-01T00:00:00",
            end = "2026-01-01T23:59:59",
            currency = "INR",
            dataTypes = listOf("transactions")
        ),
        batch = WebhookBatchInfo(id = "batch-1", index = 1, count = 1)
    )

    private fun service(
        recordedRequests: MutableList<HttpRequestData> = mutableListOf(),
        responder: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData
    ): WebhookDeliveryService = WebhookDeliveryService(
        engine = MockEngine { request ->
            recordedRequests += request
            responder(request)
        },
        retryDelay = { /* skip backoff in tests */ }
    )

    private fun MockRequestHandleScope.jsonOk(body: String = "{}"): io.ktor.client.request.HttpResponseData =
        respond(content = body, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun `same origin 301 preserves POST and envelope`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val service = service(requests) { request ->
            if (request.url.encodedPath == "/hook") {
                respond("", HttpStatusCode.MovedPermanently, headersOf(HttpHeaders.Location, "/hook/"))
            } else {
                assertEquals(HttpMethod.Post, request.method)
                val body = (request.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString()
                assertTrue(body.contains("profile-1"))
                jsonOk()
            }
        }
        assertTrue(service.deliver("https://example.com/hook", emptyList(), sampleEnvelope).success)
        assertEquals(2, requests.size)
    }

    @Test
    fun `200 OK is treated as success regardless of body`() = runBlocking {
        val service = service { jsonOk(body = "<html>not json</html>") }

        val result = service.deliver(
            url = "https://example.com/hook",
            headers = emptyList(),
            payload = sampleEnvelope
        )

        assertTrue("expected success on 2xx", result.success)
        assertEquals(200, result.httpStatus)
        assertFalse(result.retryable)
    }

    @Test
    fun `204 No Content is treated as success`() = runBlocking {
        val service = service {
            respond(content = ByteReadChannel.Empty, status = HttpStatusCode.NoContent)
        }

        val result = service.deliver("https://example.com/hook", emptyList(), sampleEnvelope)

        assertTrue(result.success)
        assertEquals(204, result.httpStatus)
    }

    @Test
    fun `body without ok=true no longer fails the delivery`() = runBlocking {
        val service = service { jsonOk(body = """{"received":true}""") }

        val result = service.deliver("https://example.com/hook", emptyList(), sampleEnvelope)

        assertTrue("HTTP-status-only contract: any 2xx counts as success", result.success)
    }

    @Test
    fun `explicit ok=false in response body is still success when status is 2xx`() = runBlocking {
        val service = service { jsonOk(body = """{"ok":false,"reason":"ignored"}""") }

        val result = service.deliver("https://example.com/hook", emptyList(), sampleEnvelope)

        assertTrue(result.success)
        assertEquals(200, result.httpStatus)
    }

    @Test
    fun `400 Bad Request fails and is not retryable`() = runBlocking {
        val attempts = mutableListOf<HttpRequestData>()
        val service = service(attempts) {
            respondError(HttpStatusCode.BadRequest)
        }

        val result = service.deliver("https://example.com/hook", emptyList(), sampleEnvelope)

        assertFalse(result.success)
        assertEquals(400, result.httpStatus)
        assertFalse("4xx (non-429) is not retryable", result.retryable)
        assertEquals(1, attempts.size)
    }

    @Test
    fun `500 server error is retryable and final result reflects last attempt`() = runBlocking {
        val attempts = mutableListOf<HttpRequestData>()
        val service = service(attempts) { respondError(HttpStatusCode.InternalServerError) }

        val result = service.deliver("https://example.com/hook", emptyList(), sampleEnvelope)

        assertEquals("retried 3 times before giving up", 3, attempts.size)
        assertFalse(result.success)
        assertEquals(500, result.httpStatus)
        assertTrue(result.retryable)
    }

    @Test
    fun `429 Too Many Requests is retryable`() = runBlocking {
        val attempts = mutableListOf<HttpRequestData>()
        val service = service(attempts) { respondError(HttpStatusCode.TooManyRequests) }

        val result = service.deliver("https://example.com/hook", emptyList(), sampleEnvelope)

        assertEquals(3, attempts.size)
        assertTrue(result.retryable)
        assertEquals(429, result.httpStatus)
    }

    @Test
    fun `succeeds after transient 503 then 200`() = runBlocking {
        val attempts = mutableListOf<HttpRequestData>()
        var callCount = 0
        val service = service(attempts) {
            callCount += 1
            if (callCount == 1) respondError(HttpStatusCode.ServiceUnavailable) else jsonOk()
        }

        val result = service.deliver("https://example.com/hook", emptyList(), sampleEnvelope)

        assertTrue(result.success)
        assertEquals(200, result.httpStatus)
        assertEquals("recovered on second attempt", 2, attempts.size)
    }

    @Test
    fun `network exception is reported as retryable failure with message`() = runBlocking {
        val service = service { throw java.net.ConnectException("connection refused") }

        val result = service.deliver("https://example.com/hook", emptyList(), sampleEnvelope)

        assertFalse(result.success)
        assertNull("no http response was received", result.httpStatus)
        assertTrue(result.retryable)
        assertNotNull(result.message)
        assertEquals("Network delivery failed", result.message)
    }

    @Test
    fun `custom headers are forwarded to the request`() = runBlocking {
        val attempts = mutableListOf<HttpRequestData>()
        val service = service(attempts) { jsonOk() }

        service.deliver(
            url = "https://example.com/hook",
            headers = listOf(
                WebhookHeader(key = "X-Auth-Token", value = "secret-123"),
                WebhookHeader(key = "  X-Trim  ", value = "padded")
            ),
            payload = sampleEnvelope
        )

        val sent = attempts.single().headers
        assertEquals("secret-123", sent["X-Auth-Token"])
        assertEquals("trims whitespace from header keys", "padded", sent["X-Trim"])
    }

    @Test
    fun `request body is JSON-serialized envelope`() = runBlocking {
        var capturedBody: String? = null
        val service = service {
            val body = it.body as? io.ktor.http.content.OutgoingContent.ByteArrayContent
            assertNotNull("expected ByteArrayContent body, got ${it.body::class}", body)
            capturedBody = body!!.bytes().toString(Charsets.UTF_8)
            jsonOk()
        }

        service.deliver("https://example.com/hook", emptyList(), sampleEnvelope)

        assertNotNull(capturedBody)
        val body = capturedBody!!
        assertTrue("envelope contains profile id", body.contains("\"id\":\"profile-1\""))
        assertTrue("envelope contains app name", body.contains("\"name\":\"PennyWise\""))
        assertTrue("snake_case SerialName aliases are honored", body.contains("\"data_types\""))
        assertTrue("schema version is included", body.contains("\"schema_version\":\"1.0\""))
        assertTrue("empty snapshot sections are included", body.contains("\"accounts\":[]"))
    }

    @Test
    fun `Apps Script style 302 with Location header is followed and counted as success`() = runBlocking {
        val attempts = mutableListOf<HttpRequestData>()
        val redirectTarget = "https://script.googleusercontent.com/macros/echo?key=abc"
        val service = service(attempts) { request ->
            if (request.url.toString().endsWith("/exec")) {
                respond(
                    content = ByteReadChannel.Empty,
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, redirectTarget)
                )
            } else {
                jsonOk()
            }
        }

        val result = service.deliver(
            url = "https://script.google.com/macros/s/X/exec",
            headers = emptyList(),
            payload = sampleEnvelope
        )

        assertTrue("302 with Location should follow and surface the final 200", result.success)
        assertEquals(200, result.httpStatus)
        assertEquals("expected original request + redirect follow", 2, attempts.size)
        assertTrue(
            "first request should target the Apps Script /exec endpoint",
            attempts[0].url.toString().endsWith("/exec")
        )
        assertEquals(
            "second request should target the redirect Location",
            redirectTarget,
            attempts[1].url.toString()
        )
    }

    @Test
    fun `Apps Script 302 redirect target only accepts GET so POST must be downgraded`() = runBlocking {
        val attempts = mutableListOf<HttpRequestData>()
        val service = service(attempts) { request ->
            when {
                request.url.toString().endsWith("/exec") -> respond(
                    content = ByteReadChannel.Empty,
                    status = HttpStatusCode.Found,
                    headers = headersOf(
                        HttpHeaders.Location,
                        "https://script.googleusercontent.com/macros/echo?key=abc"
                    )
                )
                request.method == HttpMethod.Get -> jsonOk(body = """{"ok":true}""")
                else -> respondError(HttpStatusCode.MethodNotAllowed)
            }
        }

        val result = service.deliver(
            url = "https://script.google.com/macros/s/X/exec",
            headers = emptyList(),
            payload = sampleEnvelope
        )

        assertTrue(result.success)
        assertEquals(200, result.httpStatus)
        assertEquals(2, attempts.size)
        assertEquals(HttpMethod.Post, attempts[0].method)
        assertEquals(HttpMethod.Get, attempts[1].method)
    }

    @Test
    fun `307 Temporary Redirect preserves POST method`() = runBlocking {
        val attempts = mutableListOf<HttpRequestData>()
        val service = service(attempts) { request ->
            if (request.url.toString().endsWith("/first")) {
                respond(
                    content = ByteReadChannel.Empty,
                    status = HttpStatusCode.TemporaryRedirect,
                    headers = headersOf(HttpHeaders.Location, "https://example.com/second")
                )
            } else {
                jsonOk()
            }
        }

        val result = service.deliver(
            url = "https://example.com/first",
            headers = emptyList(),
            payload = sampleEnvelope
        )

        assertTrue(result.success)
        assertEquals(2, attempts.size)
        assertEquals(HttpMethod.Post, attempts[0].method)
        assertEquals(HttpMethod.Post, attempts[1].method)
    }

    @Test
    fun `result message never echoes header values across any branch`() = runBlocking {
        val sensitiveHeader = WebhookHeader(
            key = "Authorization",
            value = "Bearer EXTREMELY_PRIVATE_TOKEN_${System.nanoTime()}"
        )
        val sentinel = "EXTREMELY_PRIVATE_TOKEN"
        val targetUrl = "https://example.com/hook"

        val branches: List<suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData> = listOf(
            { jsonOk() },                                       // 2xx
            { respondError(HttpStatusCode.BadRequest) },        // 4xx non-retryable
            { respondError(HttpStatusCode.InternalServerError) }, // 5xx retryable
            { throw java.net.ConnectException("connection refused: $targetUrl") } // network
        )

        for (responder in branches) {
            val service = service(responder = responder)
            val result = service.deliver(targetUrl, listOf(sensitiveHeader), sampleEnvelope)
            assertFalse(
                "result.message must not echo header values (got: \"${result.message}\")",
                result.message.contains(sentinel)
            )
        }
    }
    @Test
    fun `Apps Script response redirect strips credentials`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val service = service(requests) { request ->
            if (request.url.host == "script.google.com") respond("", HttpStatusCode.Found,
                headersOf(HttpHeaders.Location, "https://script.googleusercontent.com/macros/echo?key=synthetic")) else jsonOk()
        }
        assertTrue(service.deliver("https://script.google.com/macros/s/X/exec", listOf(WebhookHeader("Authorization", "synthetic-token")), sampleEnvelope).success)
        assertEquals("synthetic-token", requests[0].headers["Authorization"])
        assertNull(requests[1].headers["Authorization"])
        assertEquals(HttpMethod.Get, requests[1].method)
    }

    @Test
    fun `redirect cannot downgrade encryption or forward payload to another origin`() = runBlocking {
        for (target in listOf("http://example.com/result", "https://other.example/result")) {
            val requests = mutableListOf<HttpRequestData>()
            val service = service(requests) { respond("", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, target)) }
            assertFalse(service.deliver("https://example.com/hook", emptyList(), sampleEnvelope).success)
            assertEquals(1, requests.size)
        }
    }

    @Test
    fun `generic 302 and 303 never acknowledge a bodyless followup`() = runBlocking {
        for (status in listOf(HttpStatusCode.Found, HttpStatusCode.SeeOther)) {
            for (target in listOf("https://example.com/result", "https://other.example/result",
                "https://script.googleusercontent.com/macros/echo?key=synthetic")) {
                val requests = mutableListOf<HttpRequestData>()
                val service = service(requests) { request ->
                    if (request.url.encodedPath == "/hook") respond("", status, headersOf(HttpHeaders.Location, target))
                    else jsonOk()
                }
                val result = service.deliver("https://example.com/hook", emptyList(), sampleEnvelope)
                assertFalse(result.success)
                assertFalse(result.retryable)
                assertEquals(1, requests.size)
            }
        }
    }

    @Test
    fun `Apps Script cannot acknowledge login redirects or a second GET redirect`() = runBlocking {
        for (target in listOf("https://accounts.google.com/login", "https://script.googleusercontent.com/not-content",
            "http://script.googleusercontent.com/macros/echo", "https://script.googleusercontent.com:8443/macros/echo")) {
            val requests = mutableListOf<HttpRequestData>()
            val service = service(requests) { respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, target)) }
            assertFalse(service.deliver("https://script.google.com/macros/s/X/exec", emptyList(), sampleEnvelope).success)
            assertEquals(1, requests.size)
        }
        for (status in listOf(HttpStatusCode.MovedPermanently, HttpStatusCode.Found, HttpStatusCode.SeeOther,
            HttpStatusCode.TemporaryRedirect, HttpStatusCode.PermanentRedirect)) {
            val requests = mutableListOf<HttpRequestData>()
            val service = service(requests) { request ->
                respond("", if (request.method == HttpMethod.Post) HttpStatusCode.Found else status,
                    headersOf(HttpHeaders.Location, if (request.method == HttpMethod.Post)
                        "https://script.googleusercontent.com/macros/echo?key=synthetic" else "https://example.com/login"))
            }
            assertFalse(service.deliver("https://script.google.com/macros/s/X/exec", emptyList(), sampleEnvelope).success)
            assertEquals(2, requests.size)
        }
    }

    @Test
    fun `oversized envelopes fail before any request and are not retried`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val service = service(requests) { jsonOk() }
        val result = service.deliver("https://example.com/hook", emptyList(),
            sampleEnvelope.copy(profile = WebhookProfileInfo("synthetic", "x".repeat(1024 * 1024))))
        assertFalse(result.success)
        assertFalse(result.retryable)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `cancellation propagates without retrying`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val service = service(requests) { throw kotlinx.coroutines.CancellationException("cancelled") }
        try {
            service.deliver("https://example.com/hook", emptyList(), sampleEnvelope)
            org.junit.Assert.fail("Expected cancellation")
        } catch (expected: kotlinx.coroutines.CancellationException) {
            assertEquals(1, requests.size)
        }
    }

}
