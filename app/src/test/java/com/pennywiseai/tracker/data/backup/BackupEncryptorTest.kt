package com.pennywiseai.tracker.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupEncryptorTest {

    @Test
    fun testEncryptDecryptSuccess() {
        val testData = "This is a secret backup content for PennyWise tracker! 💸".toByteArray(Charsets.UTF_8)
        val password = "StrongBackupPassword123".toCharArray()

        // Encrypt
        val encrypted = BackupEncryptor.encrypt(testData, password)
        assertTrue(encrypted.size > 32) // MAGIC + SALT + IV + TAG + CIPHERTEXT

        // Decrypt
        val decrypted = BackupEncryptor.decrypt(encrypted, password)

        assertArrayEquals(testData, decrypted)
        assertEquals(String(testData, Charsets.UTF_8), String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun testDecryptWithIncorrectPasswordThrows() {
        val testData = "Top secret bank records".toByteArray(Charsets.UTF_8)
        val correctPassword = "CorrectPassword".toCharArray()
        val incorrectPassword = "WrongPassword".toCharArray()

        // Encrypt
        val encrypted = BackupEncryptor.encrypt(testData, correctPassword)

        // Decrypting with wrong password should throw exception (due to AES-GCM tag mismatch)
        assertThrows(Exception::class.java) {
            BackupEncryptor.decrypt(encrypted, incorrectPassword)
        }
    }

    @Test
    fun testDecryptWithInvalidMagicBytesThrows() {
        val testData = "Data".toByteArray(Charsets.UTF_8)
        val password = "Password".toCharArray()

        // Encrypt
        val encrypted = BackupEncryptor.encrypt(testData, password)

        // Corrupt magic bytes
        encrypted[0] = 0x00
        encrypted[1] = 0x00

        val exception = assertThrows(IllegalArgumentException::class.java) {
            BackupEncryptor.decrypt(encrypted, password)
        }
        assertTrue(exception.message!!.contains("magic bytes mismatch"))
    }

    @Test
    fun testDecryptTooShortDataThrows() {
        val password = "Password".toCharArray()
        val tooShort = byteArrayOf(0x50, 0x57, 0x42, 0x4B, 0x01) // Magic bytes + 1 byte

        val exception = assertThrows(IllegalArgumentException::class.java) {
            BackupEncryptor.decrypt(tooShort, password)
        }
        assertTrue(exception.message!!.contains("too short"))
    }
}
