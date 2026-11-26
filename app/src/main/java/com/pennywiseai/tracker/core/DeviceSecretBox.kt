package com.pennywiseai.tracker.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals a secret with an AES-GCM key that never leaves the Android Keystore: the relay device
 * secret by default, or another secret under its own key [alias].
 */
internal object DeviceSecretBox {
    private const val ALIAS = "pennywise_ai_access_device"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private fun key(alias: String): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    /** `base64(iv) + "." + base64(ciphertext)` */
    fun seal(plain: String, alias: String = ALIAS): String {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key(alias)) }
        val out = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." + Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun open(sealed: String, alias: String = ALIAS): String {
        val (iv, data) = sealed.split(".", limit = 2).map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, key(alias), GCMParameterSpec(128, iv)) }
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }
}
