package com.pennywiseai.tracker.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.GeneralSecurityException
import java.util.Base64

/** A sealed value is checked before its IV and ciphertext reach a cipher. */
class DeviceSecretBoxTest {
    private fun b64(size: Int) = Base64.getEncoder().encodeToString(ByteArray(size) { it.toByte() })

    @Test
    fun `a value seal could have made splits into its IV and ciphertext`() {
        val (iv, data) = DeviceSecretBox.parse("${b64(12)}.${b64(20)}")

        assertArrayEquals(ByteArray(12) { it.toByte() }, iv)
        assertArrayEquals(ByteArray(20) { it.toByte() }, data)
    }

    @Test
    fun `malformed values are rejected as a security error`() {
        listOf(
            "",
            b64(12), // no ciphertext
            "${b64(12)}.${b64(20)}.${b64(4)}", // extra part
            "${b64(0)}.${b64(20)}", // empty IV: GCM init would throw something else
            "${b64(16)}.${b64(20)}", // wrong IV size
            "${b64(12)}.${b64(15)}", // shorter than the GCM tag
            "not base64!.${b64(20)}",
        ).forEach { sealed ->
            assertThrows(sealed, GeneralSecurityException::class.java) { DeviceSecretBox.parse(sealed) }
        }
    }
}
