package com.example.share

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.sql.SQLException
import java.sql.Types
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Base64
import javax.imageio.ImageIO

/**
 * Unlisted share links: a validated snapshot plus the app-rendered OG image.
 *
 * Stores only the SHA-256 of the delete token, never the token. Deleting or expiring a
 * share drops its snapshot and image but keeps a tombstone row, so the link answers 410
 * ("gone") rather than 404 and the publisher's rate-limit window still counts it.
 */
class ShareService(
    private val connection: Connection,
    private val clock: Clock = Clock.systemUTC(),
) {
    companion object {
        const val OG_WIDTH = 1200
        const val OG_HEIGHT = 630

        /** Hidden once this many distinct visitors report it. */
        const val REPORTS_TO_HIDE = 3

        /**
         * Per client IP ([ShareClientKey]). It stops a buggy or looping client and a single
         * abuser, not an attacker with a pool of addresses.
         */
        const val MAX_SHARES_PER_HOUR = 20

        private val ID_PATTERN = Regex("^[A-Za-z0-9_-]{22}$")
        private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val random = SecureRandom()
        private val base64Url = Base64.getUrlEncoder().withoutPadding()

        fun isValidId(id: String): Boolean = ID_PATTERN.matches(id)

        /** 128 random bits, base64url — 22 characters. */
        fun newId(): String = base64Url.encodeToString(ByteArray(16).also(random::nextBytes))

        fun newDeleteToken(): String = base64Url.encodeToString(ByteArray(32).also(random::nextBytes))

        fun sha256Hex(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

        /**
         * Decodes the upload as a PNG of exactly 1200x630 and re-encodes it, which drops every
         * ancillary chunk (text, EXIF, timestamps) the encoder on the phone may have written.
         * Dimensions are read from the header before any pixels are decoded, so a
         * decompression bomb is rejected without allocating it.
         */
        fun reencodePng(bytes: ByteArray): Result<ByteArray> {
            if (bytes.size < PNG_MAGIC.size || !bytes.copyOfRange(0, PNG_MAGIC.size).contentEquals(PNG_MAGIC)) {
                return Result.failure(IllegalArgumentException("og must be a PNG image"))
            }
            return try {
                ImageIO.setUseCache(false)
                val image = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                    val reader = ImageIO.getImageReaders(input).asSequence()
                        .firstOrNull { it.formatName.equals("png", ignoreCase = true) }
                        ?: return Result.failure(IllegalArgumentException("og must be a PNG image"))
                    try {
                        reader.setInput(input, true, true)
                        if (reader.getWidth(0) != OG_WIDTH || reader.getHeight(0) != OG_HEIGHT) {
                            return Result.failure(IllegalArgumentException("og must be exactly ${OG_WIDTH}x$OG_HEIGHT"))
                        }
                        reader.read(0)
                    } finally {
                        reader.dispose()
                    }
                }
                val out = ByteArrayOutputStream(bytes.size)
                if (!ImageIO.write(image, "png", out)) return Result.failure(IllegalArgumentException("og could not be re-encoded"))
                Result.success(out.toByteArray())
            } catch (e: Exception) {
                Result.failure(IllegalArgumentException("og is not a readable PNG"))
            }
        }

        private const val CREATE_TABLE_SHARES = """
            CREATE TABLE IF NOT EXISTS shares (
                id VARCHAR(32) PRIMARY KEY,
                snapshot TEXT,
                og_png BYTEA,
                delete_token_sha256 VARCHAR(64) NOT NULL,
                publisher_fingerprint VARCHAR(64) NOT NULL,
                report_count INTEGER NOT NULL DEFAULT 0,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                expires_at TIMESTAMP WITH TIME ZONE,
                deleted_at TIMESTAMP WITH TIME ZONE
            )
        """

        private const val CREATE_TABLE_SHARE_REPORTS = """
            CREATE TABLE IF NOT EXISTS share_reports (
                share_id VARCHAR(32) NOT NULL REFERENCES shares(id) ON DELETE CASCADE,
                reporter_fingerprint VARCHAR(64) NOT NULL,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                PRIMARY KEY (share_id, reporter_fingerprint)
            )
        """

        private const val CREATE_INDEX_PUBLISHER = """
            CREATE INDEX IF NOT EXISTS idx_shares_publisher ON shares(publisher_fingerprint, created_at)
        """

        private const val CREATE_INDEX_EXPIRES = """
            CREATE INDEX IF NOT EXISTS idx_shares_expires ON shares(expires_at)
        """
    }

    init {
        try {
            connection.createStatement().use { statement ->
                statement.executeUpdate(CREATE_TABLE_SHARES)
                statement.executeUpdate(CREATE_TABLE_SHARE_REPORTS)
                statement.executeUpdate(CREATE_INDEX_PUBLISHER)
                statement.executeUpdate(CREATE_INDEX_EXPIRES)
            }
        } catch (e: Exception) {
            println("Share table initialization: ${e.message}")
        }
    }

    data class Created(val id: String, val deleteToken: String, val expiresAt: Instant?)

    sealed interface CreateResult {
        data class Ok(val created: Created) : CreateResult
        data object RateLimited : CreateResult
    }

    sealed interface Lookup<out T> {
        data class Found<T>(val value: T, val expiresAt: Instant?) : Lookup<T>
        data object NotFound : Lookup<Nothing>
        data object Gone : Lookup<Nothing>
    }

    enum class DeleteResult { DELETED, FORBIDDEN, NOT_FOUND }

    private fun now(): Instant = clock.instant().truncatedTo(ChronoUnit.SECONDS)
    private fun Instant.toOffset(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

    suspend fun create(snapshot: ShareSnapshot, png: ByteArray, fingerprint: String): CreateResult = withContext(Dispatchers.IO) {
        val now = now()
        purgeExpired(now)
        if (publishedSince(fingerprint, now.minus(Duration.ofHours(1))) >= MAX_SHARES_PER_HOUR) {
            return@withContext CreateResult.RateLimited
        }
        val id = newId()
        val token = newDeleteToken()
        val expiresAt = snapshot.expiresInDays?.let { now.plus(Duration.ofDays(it.toLong())) }
        connection.prepareStatement(
            """
            INSERT INTO shares (id, snapshot, og_png, delete_token_sha256, publisher_fingerprint, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """
        ).use { st ->
            st.setString(1, id)
            st.setString(2, ShareSnapshotValidator.json.encodeToString(ShareSnapshot.serializer(), snapshot))
            st.setBytes(3, png)
            st.setString(4, sha256Hex(token))
            st.setString(5, fingerprint)
            st.setObject(6, now.toOffset())
            if (expiresAt != null) st.setObject(7, expiresAt.toOffset()) else st.setNull(7, Types.TIMESTAMP_WITH_TIMEZONE)
            st.executeUpdate()
        }
        CreateResult.Ok(Created(id, token, expiresAt))
    }

    suspend fun snapshot(id: String): Lookup<ShareSnapshot> = lookup(id, "snapshot") { rs ->
        ShareSnapshotValidator.json.decodeFromString(ShareSnapshot.serializer(), rs.getString("snapshot"))
    }

    suspend fun ogPng(id: String): Lookup<ByteArray> = lookup(id, "og_png") { rs -> rs.getBytes("og_png") }

    private suspend fun <T> lookup(id: String, column: String, read: (java.sql.ResultSet) -> T): Lookup<T> =
        withContext(Dispatchers.IO) {
            if (!isValidId(id)) return@withContext Lookup.NotFound
            connection.prepareStatement(
                "SELECT $column, expires_at, deleted_at, report_count FROM shares WHERE id = ?"
            ).use { st ->
                st.setString(1, id)
                st.executeQuery().use { rs ->
                    if (!rs.next()) return@withContext Lookup.NotFound
                    val expiresAt = rs.getObject("expires_at", OffsetDateTime::class.java)?.toInstant()
                    val gone = rs.getObject("deleted_at", OffsetDateTime::class.java) != null ||
                        rs.getInt("report_count") >= REPORTS_TO_HIDE ||
                        (expiresAt != null && !expiresAt.isAfter(now())) ||
                        rs.getObject(column) == null
                    if (gone) Lookup.Gone else Lookup.Found(read(rs), expiresAt)
                }
            }
        }

    suspend fun delete(id: String, token: String?): DeleteResult = withContext(Dispatchers.IO) {
        if (!isValidId(id)) return@withContext DeleteResult.NOT_FOUND
        val storedHash = connection.prepareStatement("SELECT delete_token_sha256 FROM shares WHERE id = ?").use { st ->
            st.setString(1, id)
            st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        } ?: return@withContext DeleteResult.NOT_FOUND
        val presented = token?.let { sha256Hex(it) } ?: return@withContext DeleteResult.FORBIDDEN
        if (!MessageDigest.isEqual(presented.toByteArray(), storedHash.toByteArray())) {
            return@withContext DeleteResult.FORBIDDEN
        }
        connection.prepareStatement(
            "UPDATE shares SET snapshot = NULL, og_png = NULL, deleted_at = COALESCE(deleted_at, ?) WHERE id = ?"
        ).use { st ->
            st.setObject(1, now().toOffset())
            st.setString(2, id)
            st.executeUpdate()
        }
        DeleteResult.DELETED
    }

    /** Records one report per visitor fingerprint. Returns false when the share doesn't exist. */
    suspend fun report(id: String, fingerprint: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidId(id)) return@withContext false
        val exists = connection.prepareStatement("SELECT 1 FROM shares WHERE id = ?").use { st ->
            st.setString(1, id)
            st.executeQuery().use { it.next() }
        }
        if (!exists) return@withContext false
        val inserted = try {
            connection.prepareStatement(
                "INSERT INTO share_reports (share_id, reporter_fingerprint, created_at) VALUES (?, ?, ?)"
            ).use { st ->
                st.setString(1, id)
                st.setString(2, fingerprint)
                st.setObject(3, now().toOffset())
                st.executeUpdate() > 0
            }
        } catch (e: SQLException) {
            // 23505 — this visitor already reported it. Portable across Postgres and H2,
            // unlike ON CONFLICT.
            if (e.sqlState == "23505") false else throw e
        }
        if (inserted) {
            connection.prepareStatement("UPDATE shares SET report_count = report_count + 1 WHERE id = ?").use { st ->
                st.setString(1, id)
                st.executeUpdate()
            }
        }
        true
    }

    private fun publishedSince(fingerprint: String, since: Instant): Int =
        connection.prepareStatement(
            "SELECT COUNT(*) FROM shares WHERE publisher_fingerprint = ? AND created_at > ?"
        ).use { st ->
            st.setString(1, fingerprint)
            st.setObject(2, since.toOffset())
            st.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }

    /** Expired shares keep a tombstone; their content is dropped the next time anyone publishes. */
    private fun purgeExpired(now: Instant) {
        connection.prepareStatement(
            "UPDATE shares SET snapshot = NULL, og_png = NULL WHERE expires_at <= ? AND snapshot IS NOT NULL"
        ).use { st ->
            st.setObject(1, now.toOffset())
            st.executeUpdate()
        }
    }
}
