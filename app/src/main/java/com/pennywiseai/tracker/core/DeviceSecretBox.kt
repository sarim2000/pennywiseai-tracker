package com.pennywiseai.tracker.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals a secret with an AES-GCM key that never leaves the Android Keystore: the relay device
 * secret by default, or another secret under its own key [alias].
 *
 * Only [seal] creates a key. [open] never does: a missing key can't open anything sealed
 * before, so it fails instead of quietly replacing the key.
 */
internal object DeviceSecretBox {
    private const val ALIAS = "pennywise_ai_access_device"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun existingKey(alias: String): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    private fun keyForSealing(alias: String): SecretKey = existingKey(alias)
        ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()

    /** `base64(iv) + "." + base64(ciphertext)`, creating the [alias] key if it doesn't exist yet. */
    fun seal(plain: String, alias: String = ALIAS): String {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, keyForSealing(alias)) }
        val out = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return encode(cipher.iv) + "." + encode(out)
    }

    /**
     * Opens what [seal] sealed under [alias].
     *
     * @throws GeneralSecurityException when the key is gone or doesn't open it, or [sealed] is malformed.
     */
    fun open(sealed: String, alias: String = ALIAS): String {
        val (iv, data) = parse(sealed)
        val key = existingKey(alias) ?: throw GeneralSecurityException("No Keystore key for this secret")
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    /** Removes the [alias] key, and with it every secret it sealed. */
    fun delete(alias: String) {
        keyStore().deleteEntry(alias)
    }

    /**
     * Splits a sealed value into its IV and ciphertext, checked before they reach a cipher.
     *
     * @throws GeneralSecurityException when it isn't one [seal] could have made.
     */
    internal fun parse(sealed: String): Pair<ByteArray, ByteArray> {
        val parts = sealed.split(".")
        if (parts.size != 2) throw GeneralSecurityException("Malformed sealed secret")
        val (iv, data) = try {
            parts.map { Base64.getDecoder().decode(it) }
        } catch (e: IllegalArgumentException) {
            throw GeneralSecurityException("Malformed sealed secret", e)
        }
        // GCM output is the ciphertext plus its tag, so even an empty secret is a whole tag long.
        if (iv.size != IV_BYTES || data.size < TAG_BITS / 8) throw GeneralSecurityException("Malformed sealed secret")
        return iv to data
    }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
}
