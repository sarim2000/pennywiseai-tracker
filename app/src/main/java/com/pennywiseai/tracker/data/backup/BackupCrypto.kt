package com.pennywiseai.tracker.data.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.SecureRandom
import java.text.Normalizer
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

    /** Shortest passphrase a new encrypted backup takes. Opening one takes any passphrase. */
    const val MIN_NEW_PASSPHRASE_LENGTH = 8

    /** Bytes [isEncrypted] needs to see. */
    val MAGIC_SIZE: Int get() = MAGIC.size

    /** The passphrase didn't open the file (or the file was changed). */
    class WrongPassphraseException : IOException("Wrong passphrase")

    /** The file is encrypted and no passphrase was given. */
    class PassphraseRequiredException : IOException("This backup is encrypted")

    fun isEncrypted(firstBytes: ByteArray): Boolean =
        firstBytes.size >= MAGIC.size && MAGIC.indices.all { firstBytes[it] == MAGIC[it] }

    fun encrypt(input: InputStream, output: OutputStream, passphrase: CharArray) =
        encrypt(output, passphrase) { input.copyTo(it) }

    /**
     * Encrypts what [write] writes, frame by frame, so the plaintext never exists in full.
     * The final frame is only written when [write] returns: if it throws, [output] holds
     * no complete backup. The passphrase is used before [write] runs; [output] isn't closed.
     */
    fun encrypt(output: OutputStream, passphrase: CharArray, write: (OutputStream) -> Unit) {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val noncePrefix = ByteArray(NONCE_PREFIX_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC).put(VERSION).put(KDF_PBKDF2_SHA256).putInt(ITERATIONS).put(salt).put(noncePrefix)
            .array()
        val key = deriveKey(passphrase, salt, ITERATIONS)
        val out = DataOutputStream(output)
        out.write(header)
        val frames = FrameWriter(out, key, header, noncePrefix)
        try {
            write(frames)
            frames.finish()
        } finally {
            frames.clear()
        }
        out.flush()
    }

    /**
     * Seals each full 64 KiB frame once more bytes arrive, so it knows which frame is the
     * last; [finish] seals what's left (maybe nothing) as the last frame.
     */
    private class FrameWriter(
        private val out: DataOutputStream,
        private val key: SecretKeySpec,
        private val header: ByteArray,
        private val noncePrefix: ByteArray
    ) : OutputStream() {
        private val buffer = ByteArray(FRAME_BYTES)
        private var filled = 0
        private var index = 0

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            var at = off
            var left = len
            while (left > 0) {
                if (filled == FRAME_BYTES) seal(isLast = false)
                val n = minOf(left, FRAME_BYTES - filled)
                System.arraycopy(b, at, buffer, filled, n)
                filled += n
                at += n
                left -= n
            }
        }

        fun finish() = seal(isLast = true)

        fun clear() = buffer.fill(0)

        private fun seal(isLast: Boolean) {
            val sealed = cipher(Cipher.ENCRYPT_MODE, key, header, noncePrefix, index, isLast).doFinal(buffer, 0, filled)
            out.writeByte(if (isLast) 1 else 0)
            out.writeInt(sealed.size)
            out.write(sealed)
            filled = 0
            index++
        }
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

    /**
     * Derives the AES key from the passphrase in Unicode NFC, so "é" typed composed or
     * decomposed opens the same file; NFC of an ASCII or already-NFC passphrase is itself,
     * so earlier files still open and the envelope version stays 1. Nothing else is
     * changed: leading and trailing spaces count.
     */
    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        // Normalizer returns a String, which can't be zeroed; that copy is unavoidable.
        val normalized = Normalizer.normalize(CharBuffer.wrap(passphrase), Normalizer.Form.NFC).toCharArray()
        val spec = PBEKeySpec(normalized, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            // SecretKeySpec keeps its own copy.
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
            normalized.fill('\u0000')
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
