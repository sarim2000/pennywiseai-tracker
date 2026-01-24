package com.example.feedback

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.http.*
import java.security.MessageDigest

object FingerprintService {
    private const val SALT = "pennywiseai-feedback-v1"
    private const val COOKIE_NAME = "pw_fp"
    private const val COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 365 // 1 year

    fun getFingerprint(call: ApplicationCall): String {
        val existingCookie = call.request.cookies[COOKIE_NAME]
        if (existingCookie != null && existingCookie.length == 64) {
            return existingCookie
        }

        return generateFingerprint(call)
    }

    fun generateFingerprint(call: ApplicationCall): String {
        val userAgent = call.request.userAgent() ?: "unknown"
        val ip = call.request.local.remoteHost
        val forwardedFor = call.request.header(HttpHeaders.XForwardedFor) ?: ip

        val raw = "$userAgent|$forwardedFor|$SALT"
        return sha256(raw)
    }

    fun getCookieName(): String = COOKIE_NAME

    fun getCookieMaxAge(): Int = COOKIE_MAX_AGE_SECONDS

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
