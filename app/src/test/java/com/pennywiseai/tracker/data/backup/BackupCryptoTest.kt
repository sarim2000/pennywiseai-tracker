package com.pennywiseai.tracker.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.random.Random

class BackupCryptoTest {
    private val passphrase = "synthetic test passphrase"
    private val headerBytes = 35

    private fun encrypt(plain: ByteArray, pass: String = passphrase): ByteArray =
        ByteArrayOutputStream().also { BackupCrypto.encrypt(ByteArrayInputStream(plain), it, pass.toCharArray()) }.toByteArray()

    private fun decrypt(sealed: ByteArray, pass: String = passphrase): ByteArray =
        ByteArrayOutputStream().also { BackupCrypto.decrypt(ByteArrayInputStream(sealed), it, pass.toCharArray()) }.toByteArray()

    /** Byte ranges of each frame (flag + length + ciphertext) after the header. */
    private fun frames(sealed: ByteArray): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var at = headerBytes
        while (at < sealed.size) {
            val length = ByteBuffer.wrap(sealed, at + 1, 4).int
            ranges += at until at + 5 + length
            at += 5 + length
        }
        return ranges
    }

    private fun bytes(size: Int) = Random(size).nextBytes(size)

    @Test
    fun smallRoundTrip() {
        val plain = "{\"_format\":\"PennyWise Backup v1.3\"}".toByteArray()
        assertArrayEquals(plain, decrypt(encrypt(plain)))
    }

    @Test
    fun emptyRoundTrip() {
        assertArrayEquals(ByteArray(0), decrypt(encrypt(ByteArray(0))))
    }

    @Test
    fun multiFrameRoundTrip() {
        val plain = bytes(64 * 1024 * 2 + 123)
        val sealed = encrypt(plain)
        assertTrue(frames(sealed).size == 3)
        assertArrayEquals(plain, decrypt(sealed))
    }

    @Test
    fun exactFrameMultipleRoundTrip() {
        val plain = bytes(64 * 1024 * 2)
        val sealed = encrypt(plain)
        assertTrue(frames(sealed).size == 2)
        assertArrayEquals(plain, decrypt(sealed))
    }

    @Test
    fun streamedWritesMatchACopiedInput() {
        val plain = bytes(64 * 1024 * 2 + 123)
        val sealed = ByteArrayOutputStream().also { out ->
            BackupCrypto.encrypt(out, passphrase.toCharArray()) { sink ->
                // Uneven pieces that straddle the frame edges, and single bytes.
                var at = 0
                while (at < plain.size) {
                    val n = minOf(1000, plain.size - at)
                    sink.write(plain, at, n)
                    at += n
                    if (at < plain.size) sink.write(plain[at++].toInt())
                }
            }
        }.toByteArray()
        assertTrue(frames(sealed).size == 3)
        assertArrayEquals(plain, decrypt(sealed))
    }

    @Test
    fun aWriteThatFailsLeavesNoCompleteBackup() {
        val out = ByteArrayOutputStream()
        assertThrows(IllegalStateException::class.java) {
            BackupCrypto.encrypt(out, passphrase.toCharArray()) { sink ->
                sink.write(bytes(64 * 1024 + 10))
                error("export failed halfway")
            }
        }
        // No last frame was written, so what's there can't pass for a backup.
        assertThrows(IOException::class.java) { decrypt(out.toByteArray()) }
    }

    @Test
    fun wrongPassphraseIsReported() {
        val sealed = encrypt(bytes(100))
        assertThrows(BackupCrypto.WrongPassphraseException::class.java) { decrypt(sealed, "not the passphrase") }
    }

    @Test
    fun changedHeaderFails() {
        val sealed = encrypt(bytes(100))
        // A salt byte: the header is the AAD of every frame and feeds the key.
        sealed[headerBytes - 10] = (sealed[headerBytes - 10] + 1).toByte()
        assertThrows(IOException::class.java) { decrypt(sealed) }
    }

    @Test
    fun changedBodyFails() {
        val sealed = encrypt(bytes(64 * 1024 + 10))
        val second = frames(sealed)[1]
        sealed[second.first + 6] = (sealed[second.first + 6] + 1).toByte()
        assertThrows(IOException::class.java) { decrypt(sealed) }
    }

    @Test
    fun droppedLastFrameFails() {
        val sealed = encrypt(bytes(64 * 1024 * 2 + 10))
        val last = frames(sealed).last()
        assertThrows(IOException::class.java) { decrypt(sealed.copyOf(last.first)) }
    }

    @Test
    fun cutFrameFails() {
        val sealed = encrypt(bytes(100))
        assertThrows(IOException::class.java) { decrypt(sealed.copyOf(sealed.size - 1)) }
    }

    @Test
    fun lastFlagCannotBeMovedToAnEarlierFrame() {
        val sealed = encrypt(bytes(64 * 1024 + 10))
        val first = frames(sealed)[0]
        sealed[first.first] = 1
        assertThrows(IOException::class.java) { decrypt(sealed.copyOf(first.last + 1)) }
    }

    @Test
    fun appendedBytesFail() {
        val sealed = encrypt(bytes(100))
        assertThrows(IOException::class.java) { decrypt(sealed + byteArrayOf(0)) }
    }

    @Test
    fun reorderedFramesFail() {
        val sealed = encrypt(bytes(64 * 1024 * 3 + 10))
        val (a, b, c, d) = frames(sealed)
        val reordered = sealed.copyOfRange(0, headerBytes) +
            sealed.sliceArray(b) + sealed.sliceArray(a) + sealed.sliceArray(c) + sealed.sliceArray(d)
        assertThrows(IOException::class.java) { decrypt(reordered) }
    }

    @Test
    fun unknownVersionIsRejected() {
        val sealed = encrypt(bytes(100))
        sealed[5] = 2
        val error = assertThrows(IOException::class.java) { decrypt(sealed) }
        assertTrue(error.message!!.contains("version"))
    }

    @Test
    fun outOfRangeIterationsAreRejectedBeforeDeriving() {
        val sealed = encrypt(bytes(100))
        ByteBuffer.wrap(sealed, 7, 4).putInt(Int.MAX_VALUE)
        val error = assertThrows(IOException::class.java) { decrypt(sealed) }
        assertTrue(error.message!!.contains("key derivation"))
    }

    @Test
    fun plainBackupIsNotDetectedAsEncrypted() {
        assertTrue(BackupCrypto.isEncrypted(encrypt(bytes(10))))
        assertFalse(BackupCrypto.isEncrypted("{\"_format\":\"PennyWise Backup v1.3\"}".toByteArray()))
        assertFalse(BackupCrypto.isEncrypted("PWE".toByteArray()))
    }
}
