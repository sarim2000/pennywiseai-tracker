package com.pennywiseai.tracker.data.webhook

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CancellationException
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

@Singleton
class WebhookDeliveryService internal constructor(
    engine: HttpClientEngine,
    private val retryDelay: suspend (attempt: Int) -> Unit
) {
    @Inject constructor() : this(
        engine = Android.create(),
        retryDelay = { attempt -> delay(1_000L shl attempt) }
    )

    private val client = HttpClient(engine) {
        followRedirects = false
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
    }

    suspend fun deliver(
        url: String,
        headers: List<WebhookHeader>,
        payload: WebhookEnvelope
    ): WebhookAttemptResult {
        if (WebhookValidation.validateUrl(url) != null) {
            return WebhookAttemptResult(false, message = "Invalid endpoint URL")
        }
        val body = WebhookPayloadEncoding.encode(payload)
        if (body.size > WebhookPayloadEncoding.MAX_BYTES) {
            return WebhookAttemptResult(false, message = "Webhook payload exceeds the 1 MiB limit")
        }
        val trimmedHeaders = headers.map { it.copy(key = it.key.trim()) }
        var lastError: WebhookAttemptResult? = null
        repeat(3) { attempt ->
            try {
                val result = sendWithRedirects(url, trimmedHeaders, body)
                if (result.success) return result
                lastError = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = WebhookAttemptResult(
                    success = false,
                    message = "Network delivery failed",
                    retryable = true
                )
            }

            if (!lastError.retryable) return lastError
            if (attempt < 2 && lastError.retryable) {
                retryDelay(attempt)
            }
        }
        return lastError ?: WebhookAttemptResult(success = false, message = "Unknown delivery error", retryable = true)
    }

    /**
     * Follows body-preserving redirects. Only Apps Script Content Service may redirect
     * a processed POST to a GET response URL; generic GET redirects cannot acknowledge delivery.
     */
    private suspend fun sendWithRedirects(
        initialUrl: String,
        headers: List<WebhookHeader>,
        body: ByteArray
    ): WebhookAttemptResult {
        val originAuthority = authorityOf(initialUrl)
        var currentUrl = initialUrl
        var method = HttpMethod.Post
        var includeBody = true

        repeat(MAX_REDIRECTS + 1) {
            val response: HttpResponse = client.request(currentUrl) {
                this.method = method
                header(HttpHeaders.ContentType, "application/json")
                if (sameOrigin(currentUrl, originAuthority)) {
                    headers.forEach { header(it.key, it.value) }
                }
                if (includeBody) setBody(body)
            }
            val status = response.status.value
            when {
                status in 200..299 -> return WebhookAttemptResult(
                    success = true,
                    httpStatus = status,
                    message = "Delivered (HTTP $status)"
                )
                status in 300..399 -> {
                    if (!includeBody) {
                        return WebhookAttemptResult(false, status, "Response redirect did not acknowledge webhook delivery")
                    }
                    val location = response.headers[HttpHeaders.Location]
                        ?: return WebhookAttemptResult(
                            success = false,
                            httpStatus = status,
                            message = "HTTP $status without Location header",
                            retryable = false
                        )
                    val nextUrl = java.net.URI(currentUrl).resolve(location).toString()
                    when (status) {
                        HttpStatusCode.Found.value,
                        HttpStatusCode.SeeOther.value -> {
                            if (!isAppsScriptResponseRedirect(currentUrl, nextUrl)) {
                                return WebhookAttemptResult(false, status, "Redirect did not acknowledge webhook delivery")
                            }
                            method = HttpMethod.Get
                            includeBody = false
                        }
                        HttpStatusCode.MovedPermanently.value,
                        HttpStatusCode.TemporaryRedirect.value,
                        HttpStatusCode.PermanentRedirect.value -> Unit // preserve method + body
                        else -> return WebhookAttemptResult(
                            success = false,
                            httpStatus = status,
                            message = "Unsupported redirect HTTP $status",
                            retryable = false
                        )
                    }
                    if (WebhookValidation.validateUrl(nextUrl) != null ||
                        (Url(currentUrl).protocol.name == "https" && Url(nextUrl).protocol.name != "https")) {
                        return WebhookAttemptResult(false, status, "Unsafe redirect rejected")
                    }
                    if (!sameOrigin(nextUrl, originAuthority) && includeBody) {
                        return WebhookAttemptResult(false, status, "Cross-origin payload redirect rejected")
                    }
                    currentUrl = nextUrl
                }
                else -> return WebhookAttemptResult(
                    success = false,
                    httpStatus = status,
                    message = "HTTP $status",
                    retryable = status >= HttpStatusCode.InternalServerError.value ||
                        status == HttpStatusCode.TooManyRequests.value
                )
            }
        }
        return WebhookAttemptResult(
            success = false,
            message = "Too many redirects (>$MAX_REDIRECTS)",
            retryable = false
        )
    }

    private fun sameOrigin(currentUrl: String, originAuthority: String?): Boolean {
        if (originAuthority == null) return true
        val currentAuthority = authorityOf(currentUrl) ?: return false
        return currentAuthority.equals(originAuthority, ignoreCase = true)
    }

    private fun isAppsScriptResponseRedirect(from: String, to: String): Boolean {
        val source = Url(from)
        val target = Url(to)
        return source.protocol.name == "https" && source.port == 443 && source.host == "script.google.com" &&
            source.encodedPath.matches(Regex("/macros/s/[^/]+/exec")) &&
            target.protocol.name == "https" && target.port == 443 && target.host == "script.googleusercontent.com" &&
            target.encodedPath == "/macros/echo"
    }

    private fun authorityOf(url: String): String? =
        runCatching { Url(url).let { "${it.protocol.name}://${it.host}:${it.port}" } }.getOrNull()

    private companion object {
        const val MAX_REDIRECTS = 5
    }
}
