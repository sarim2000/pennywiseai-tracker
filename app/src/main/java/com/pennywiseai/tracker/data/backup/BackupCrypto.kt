package com.pennywiseai.tracker.data.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase encryption for backup files. See "Encrypted backups" in docs/backup-format.md.
 *
 * Header: `PWENC`, version, KDF id, iterations (int), 16-byte salt, 8-byte nonce prefix.
 * Body: frames of `isLast` (byte) + ciphertext length (int) + AES-GCM ciphertext of up to
 * 64 KiB plaintext. Frame nonce = prefix ‖ index; AAD = header ‖ index ‖ isLast, so a
 * changed header, a dropped, reordered or truncated frame, or trailing bytes all fail.
 *
 * The caller owns the passphrase array and clears it when it's done.
 */
object BackupCrypto {
    private val MAGIC = "PWENC".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val KDF_PBKDF2_SHA256: Byte = 1
    private const val ITERATIONS = 310_000
    private val ITERATION_RANGE = 100_000..2_000_000
    private const val SALT_BYTES = 16
    private const val NONCE_PREFIX_BYTES = 8
    private const val HEADER_BYTES = 5 + 1 + 1 + 4 + SALT_BYTES + NONCE_PREFIX_BYTES
    private const val FRAME_BYTES = 64 * 1024
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / 8

    /** Bytes [isEncrypted] needs to see. */
    val MAGIC_SIZE: Int get() = MAGIC.size

    /** The passphrase didn't open the file (or the file was changed). */
    class WrongPassphraseException : IOException("Wrong passphrase")

    /** The file is encrypted and no passphrase was given. */
    class PassphraseRequiredException : IOException("This backup is encrypted")

    fun isEncrypted(firstBytes: ByteArray): Boolean =
        firstBytes.size >= MAGIC.size && MAGIC.indices.all { firstBytes[it] == MAGIC[it] }

    fun encrypt(input: InputStream, output: OutputStream, passphrase: CharArray) {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val noncePrefix = ByteArray(NONCE_PREFIX_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC).put(VERSION).put(KDF_PBKDF2_SHA256).putInt(ITERATIONS).put(salt).put(noncePrefix)
            .array()
        val key = deriveKey(passphrase, salt, ITERATIONS)
        val out = DataOutputStream(output)
        out.write(header)

        // One frame of lookahead, so the writer knows which frame is the last.
        var current = readFrame(input)
        var index = 0
        while (true) {
            val next = if (current.size == FRAME_BYTES) readFrame(input) else ByteArray(0)
            val isLast = next.isEmpty()
            val sealed = cipher(Cipher.ENCRYPT_MODE, key, header, noncePrefix, index, isLast).doFinal(current)
            out.writeByte(if (isLast) 1 else 0)
            out.writeInt(sealed.size)
            out.write(sealed)
            if (isLast) break
            current = next
            index++
        }
        out.flush()
    }

    /**
     * Writes the plaintext to [output] frame by frame. On a failure part of it may already
     * be written, so decrypt into a temp file and use it only when this returns.
     */
    fun decrypt(input: InputStream, output: OutputStream, passphrase: CharArray) {
        val data = DataInputStream(input)
        val header = ByteArray(HEADER_BYTES)
        readFully(data, header)
        if (!isEncrypted(header)) throw IOException("Not an encrypted backup")
        val fields = ByteBuffer.wrap(header, MAGIC.size, HEADER_BYTES - MAGIC.size)
        val version = fields.get()
        if (version != VERSION) throw IOException("Unsupported encrypted backup version $version")
        val kdf = fields.get()
        if (kdf != KDF_PBKDF2_SHA256) throw IOException("Unsupported key derivation $kdf")
        val iterations = fields.int
        if (iterations !in ITERATION_RANGE) throw IOException("Unsupported key derivation settings")
        val salt = ByteArray(SALT_BYTES).also { fields.get(it) }
        val noncePrefix = ByteArray(NONCE_PREFIX_BYTES).also { fields.get(it) }
        val key = deriveKey(passphrase, salt, iterations)

        var index = 0
        while (true) {
            val flag = readFully(data, ByteArray(1))[0].toInt()
            if (flag != 0 && flag != 1) throw IOException("Damaged encrypted backup")
            val length = data.readIntOrTruncated()
            if (length !in TAG_BYTES..FRAME_BYTES + TAG_BYTES) throw IOException("Damaged encrypted backup")
            val sealed = readFully(data, ByteArray(length))
            val plain = try {
                cipher(Cipher.DECRYPT_MODE, key, header, noncePrefix, index, flag == 1).doFinal(sealed)
            } catch (e: AEADBadTagException) {
                // The first frame is where a wrong passphrase shows; later ones mean damage.
                if (index == 0) throw WrongPassphraseException()
                throw IOException("Damaged encrypted backup")
            }
            output.write(plain)
            if (flag == 1) break
            index++
        }
        if (data.read() != -1) throw IOException("Damaged encrypted backup")
        output.flush()
    }

    private fun readFrame(input: InputStream): ByteArray {
        val buffer = ByteArray(FRAME_BYTES)
        var filled = 0
        while (filled < FRAME_BYTES) {
            val read = input.read(buffer, filled, FRAME_BYTES - filled)
            if (read == -1) break
            filled += read
        }
        return if (filled == FRAME_BYTES) buffer else buffer.copyOf(filled)
    }

    private fun readFully(data: DataInputStream, into: ByteArray): ByteArray {
        try {
            data.readFully(into)
        } catch (e: EOFException) {
            throw IOException("Encrypted backup is incomplete")
        }
        return into
    }

    private fun DataInputStream.readIntOrTruncated(): Int = try {
        readInt()
    } catch (e: EOFException) {
        throw IOException("Encrypted backup is incomplete")
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            // SecretKeySpec keeps its own copy.
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    private fun cipher(
        mode: Int,
        key: SecretKeySpec,
        header: ByteArray,
        noncePrefix: ByteArray,
        index: Int,
        isLast: Boolean
    ): Cipher {
        val nonce = ByteBuffer.allocate(NONCE_PREFIX_BYTES + 4).put(noncePrefix).putInt(index).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(header)
            updateAAD(ByteBuffer.allocate(5).putInt(index).put(if (isLast) 1 else 0).array())
        }
    }
}
