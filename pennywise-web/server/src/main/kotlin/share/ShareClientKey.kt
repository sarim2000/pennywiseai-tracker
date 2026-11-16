package com.example.share

import io.ktor.server.application.*
import io.ktor.server.request.*
import java.net.InetAddress
import java.security.MessageDigest

/**
 * Who is publishing or reporting, for the hourly publish limit and one-report-per-visitor.
 *
 * Keyed on the client IP alone. The feedback fingerprint (User-Agent + the raw
 * X-Forwarded-For header) is no good here: a client controls both, so changing either
 * buys a fresh identity, which would let one person hide any share with three requests
 * or publish without limit. CF-Connecting-IP is set by Cloudflare's edge and can't be
 * forged through it; the Worker passes it on to the container. Without it (local runs,
 * tests) the socket's peer address is used. IPv6 is cut to its /64, the block one
 * subscriber gets, so walking addresses inside it doesn't help either.
 *
 * Only a salted hash is stored, never the address.
 */
object ShareClientKey {
    private const val SALT = "pennywise-share-v1"
    private val IP_LITERAL = Regex("^[0-9A-Fa-f:.]{2,45}$")

    fun of(call: ApplicationCall): String {
        val ip = call.request.header("CF-Connecting-IP")?.trim()?.takeIf { it.isNotEmpty() }
            ?: call.request.local.remoteAddress
        return ShareService.sha256Hex("${normalize(ip)}|$SALT")
    }

    /** IPv4 as is, IPv6 reduced to its /64 prefix. */
    fun normalize(ip: String): String {
        // Only ever parse a literal: InetAddress.getByName on anything else is a DNS lookup.
        if (!ip.contains(':') || !IP_LITERAL.matches(ip)) return ip
        val bytes = try {
            InetAddress.getByName(ip).address
        } catch (e: Exception) {
            return ip
        }
        if (bytes.size != 16) return ip
        return bytes.copyOf(8).joinToString("") { "%02x".format(it) } + "::/64"
    }
}
