package com.example.share

import com.example.ui.SharedComponents.SITE_ORIGIN
import com.example.ui.ShareViews
import com.example.ui.ShareViews.respondShareGone
import com.example.ui.ShareViews.respondShareNotFound
import com.example.ui.ShareViews.respondSharePage
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray

/** Whole multipart body, snapshot and image together: 1.5 MiB. */
const val MAX_SHARE_BODY_BYTES = 1_572_864L

private const val PAGE_CACHE = "public, max-age=300"
private const val OG_CACHE = "public, max-age=86400, immutable"

fun Application.configureShares(service: ShareService) {
    routing {
        post("/api/shares") {
            val fingerprint = ShareClientKey.of(call)
            val body = readShareUpload(call).getOrElse { return@post call.respondShareError(it.message) }
            val snapshot = ShareSnapshotValidator.parse(body.snapshot).getOrElse { return@post call.respondShareError(it.message) }
            val png = ShareService.reencodePng(body.og).getOrElse { return@post call.respondShareError(it.message) }

            when (val result = service.create(snapshot, png, fingerprint)) {
                ShareService.CreateResult.RateLimited ->
                    call.respondShareError("Too many shares from this device. Try again later.", HttpStatusCode.TooManyRequests)
                is ShareService.CreateResult.Ok -> {
                    val created = result.created
                    val response = CreateShareResponse(
                        id = created.id,
                        url = "$SITE_ORIGIN/s/${created.id}",
                        deleteToken = created.deleteToken,
                        expiresAt = created.expiresAt?.toString(),
                    )
                    call.respondText(
                        ShareSnapshotValidator.json.encodeToString(CreateShareResponse.serializer(), response),
                        ContentType.Application.Json,
                        HttpStatusCode.Created,
                    )
                }
            }
        }

        delete("/api/shares/{id}") {
            val token = call.request.header(HttpHeaders.Authorization)
                ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                ?.substring("Bearer ".length)?.trim()
            when (service.delete(call.parameters["id"].orEmpty(), token)) {
                ShareService.DeleteResult.DELETED -> call.respond(HttpStatusCode.NoContent)
                ShareService.DeleteResult.FORBIDDEN -> call.respondShareError("Wrong delete token", HttpStatusCode.Forbidden)
                ShareService.DeleteResult.NOT_FOUND -> call.respondShareError("Unknown share", HttpStatusCode.NotFound)
            }
        }

        get("/s/{id}") {
            val id = call.parameters["id"].orEmpty()
            call.response.header("X-Robots-Tag", "noindex")
            when (val found = service.snapshot(id)) {
                is ShareService.Lookup.Found -> {
                    call.response.header(HttpHeaders.CacheControl, PAGE_CACHE)
                    call.response.header("Content-Security-Policy", ShareViews.CONTENT_SECURITY_POLICY)
                    call.response.header("Referrer-Policy", "same-origin")
                    call.respondSharePage(id, found.value)
                }
                ShareService.Lookup.NotFound -> call.respondShareNotFound()
                ShareService.Lookup.Gone -> call.respondShareGone()
            }
        }

        get("/s/{id}/og.png") {
            call.response.header("X-Robots-Tag", "noindex")
            when (val found = service.ogPng(call.parameters["id"].orEmpty())) {
                is ShareService.Lookup.Found -> {
                    call.response.header(HttpHeaders.CacheControl, OG_CACHE)
                    call.respondBytes(found.value, ContentType.Image.PNG)
                }
                ShareService.Lookup.NotFound -> {
                    call.response.header(HttpHeaders.CacheControl, "no-store")
                    call.respond(HttpStatusCode.NotFound)
                }
                ShareService.Lookup.Gone -> {
                    call.response.header(HttpHeaders.CacheControl, "no-store")
                    call.respond(HttpStatusCode.Gone)
                }
            }
        }

        post("/s/{id}/report") {
            val fingerprint = ShareClientKey.of(call)
            if (service.report(call.parameters["id"].orEmpty(), fingerprint)) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.NotFound)
        }
    }
}

private class ShareUpload(val snapshot: String, val og: ByteArray)

/**
 * Content-Length is checked up front, and since a chunked upload has none, every part is
 * also read under the budget that is left, so no more than [MAX_SHARE_BODY_BYTES] of
 * payload is ever accepted. Unknown or repeated parts end the read immediately.
 */
private suspend fun readShareUpload(call: ApplicationCall): Result<ShareUpload> {
    val contentType = call.request.header(HttpHeaders.ContentType)
    if (contentType == null || !ContentType.parse(contentType).match(ContentType.MultiPart.FormData)) {
        return Result.failure(IllegalArgumentException("expected multipart/form-data"))
    }
    val declared = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
    if (declared != null && declared > MAX_SHARE_BODY_BYTES) return tooLarge()

    var snapshot: String? = null
    var og: ByteArray? = null
    var budget = MAX_SHARE_BODY_BYTES
    try {
        val multipart = call.receiveMultipart(formFieldLimit = MAX_SHARE_BODY_BYTES)
        while (true) {
            val part = multipart.readPart() ?: break
            try {
                val bytes = when (part) {
                    is PartData.FormItem -> part.value.toByteArray()
                    is PartData.FileItem -> part.provider().readRemaining(budget + 1).readByteArray()
                    else -> null
                }
                if (bytes != null) budget -= bytes.size
                if (budget < 0) return tooLarge()
                when (part.name) {
                    "snapshot" -> {
                        if (snapshot != null || bytes == null) return Result.failure(IllegalArgumentException("one snapshot part expected"))
                        snapshot = bytes.decodeToString()
                    }
                    "og" -> {
                        if (og != null || bytes == null) return Result.failure(IllegalArgumentException("one og part expected"))
                        og = bytes
                    }
                    else -> return Result.failure(IllegalArgumentException("unexpected part '${part.name}'"))
                }
            } finally {
                part.dispose()
            }
        }
    } catch (e: Exception) {
        return Result.failure(IllegalArgumentException("malformed or oversized multipart body"))
    }
    return Result.success(
        ShareUpload(
            snapshot ?: return Result.failure(IllegalArgumentException("missing snapshot part")),
            og ?: return Result.failure(IllegalArgumentException("missing og part")),
        )
    )
}

private fun tooLarge(): Result<Nothing> =
    Result.failure(IllegalArgumentException("upload is larger than ${MAX_SHARE_BODY_BYTES / 1024} KiB"))

private suspend fun ApplicationCall.respondShareError(message: String?, status: HttpStatusCode = HttpStatusCode.BadRequest) {
    respondText(
        ShareSnapshotValidator.json.encodeToString(ShareErrorResponse.serializer(), ShareErrorResponse(message ?: "invalid request")),
        ContentType.Application.Json,
        status,
    )
}
