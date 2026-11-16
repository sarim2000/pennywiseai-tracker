package com.pennywiseai.tracker.data.share

import com.pennywiseai.tracker.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Where share links live. Debug builds can swap it for a local server (app/build.gradle.kts). */
const val SHARE_SERVER = "https://$SHARE_CARD_URL"

/** Why a share-link call failed, each with its own message to the user. */
sealed class ShareLinkError : Exception() {
    /** No answer: offline, DNS, timeout. */
    data object Network : ShareLinkError()

    data object RateLimited : ShareLinkError()

    /** Over the server's 1.5 MiB upload limit. */
    data object TooLarge : ShareLinkError()

    /** The server refused the snapshot; [reason] is its own words. */
    data class Rejected(val reason: String) : ShareLinkError()

    /** A delete whose token the server didn't accept. */
    data object Forbidden : ShareLinkError()

    data class Server(val status: Int) : ShareLinkError()

    /** This phone couldn't write its list of links (disk full, unreadable file). */
    data object Storage : ShareLinkError()
}

/**
 * Publishes a share card to the web site and deletes it again (pennywise-web
 * `share/ShareRouting.kt`). Called only from an explicit tap in the share studio or the
 * Shared links screen; nothing here runs in the background.
 */
@Singleton
class ShareLinkClient internal constructor(engine: HttpClientEngine, private val server: String) {

    @Inject constructor() : this(OkHttp.create(), BuildConfig.SHARE_SERVER_OVERRIDE.ifEmpty { SHARE_SERVER })

    private val http by lazy {
        HttpClient(engine) {
            followRedirects = false
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 30_000
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ErrorBody(val error: String)

    /** Uploads the card's [snapshot] JSON and its 1200×630 [ogPng]; returns the new link with a public [CreatedShare.url]. */
    suspend fun publish(snapshot: String, ogPng: ByteArray): CreatedShare {
        val body = MultiPartFormDataContent(
            formData {
                append("snapshot", snapshot, Headers.build { append(HttpHeaders.ContentType, "application/json") })
                // A file name makes this a file part, which the server reads as raw bytes.
                append(
                    "og", ogPng,
                    Headers.build {
                        append(HttpHeaders.ContentType, "image/png")
                        append(HttpHeaders.ContentDisposition, "filename=\"og.png\"")
                    },
                )
            }
        )
        // Checked here too, so an oversized card fails without spending the user's data.
        if ((body.contentLength ?: 0L) > MAX_BODY_BYTES) throw ShareLinkError.TooLarge
        val response = call { http.post("$server/api/shares") { setBody(body) } }
        return when (response.status) {
            HttpStatusCode.Created -> {
                val created = runCatching { json.decodeFromString(CreatedShare.serializer(), response.bodyAsText()) }
                    .getOrElse { throw ShareLinkError.Server(response.status.value) }
                // A local server still answers with the public address; point at itself instead.
                if (server == SHARE_SERVER) created else created.copy(url = "$server/s/${created.id.encodeURLPathPart()}")
            }
            else -> throw failure(response)
        }
    }

    /** Takes a link down. One the server no longer has counts as deleted. */
    suspend fun delete(id: String, deleteToken: String) {
        val response = call {
            http.delete("$server/api/shares/${id.encodeURLPathPart()}") {
                header(HttpHeaders.Authorization, "Bearer $deleteToken")
            }
        }
        when (response.status) {
            HttpStatusCode.NoContent, HttpStatusCode.OK, HttpStatusCode.NotFound, HttpStatusCode.Gone -> Unit
            HttpStatusCode.Forbidden -> throw ShareLinkError.Forbidden
            else -> throw failure(response)
        }
    }

    private suspend fun call(request: suspend () -> HttpResponse): HttpResponse = try {
        request()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ShareLinkError.Network
    }

    private suspend fun failure(response: HttpResponse): ShareLinkError = when (response.status) {
        HttpStatusCode.TooManyRequests -> ShareLinkError.RateLimited
        HttpStatusCode.PayloadTooLarge -> ShareLinkError.TooLarge
        HttpStatusCode.BadRequest -> ShareLinkError.Rejected(
            runCatching { json.decodeFromString(ErrorBody.serializer(), response.bodyAsText()).error }.getOrNull()
                ?.take(MAX_REASON_CHARS) ?: response.status.description
        )
        else -> ShareLinkError.Server(response.status.value)
    }

    companion object {
        /** The server's limit on the whole multipart body, snapshot and image together. */
        const val MAX_BODY_BYTES = 1_572_864L
        private const val MAX_REASON_CHARS = 200
    }
}
