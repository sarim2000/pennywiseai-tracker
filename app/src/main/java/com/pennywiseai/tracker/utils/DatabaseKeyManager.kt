package com.pennywiseai.tracker.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object DatabaseKeyManager {
    private const val KEY_ALIAS = "pennywise_db_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PREFS_NAME = "pennywise_secure_prefs"
    private const val ENCRYPTED_PASS_KEY = "encrypted_db_pass"
    private const val IV_KEY = "db_pass_iv"

    @Synchronized
    fun getDatabasePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedPassBase64 = prefs.getString(ENCRYPTED_PASS_KEY, null)
        val ivBase64 = prefs.getString(IV_KEY, null)

        return if (encryptedPassBase64 != null && ivBase64 != null) {
            try {
                val encryptedPass = Base64.decode(encryptedPassBase64, Base64.DEFAULT)
                val iv = Base64.decode(ivBase64, Base64.DEFAULT)
                decryptPassphrase(encryptedPass, iv)
            } catch (e: Exception) {
                // If Keystore was cleared or corrupted, generate a new one.
                val newPass = generateRandomPassphrase()
                savePassphrase(context, newPass)
                newPass
            }
        } else {
            val newPass = generateRandomPassphrase()
            savePassphrase(context, newPass)
            newPass
        }
    }

    private fun generateRandomPassphrase(): ByteArray {
        val random = SecureRandom()
        val key = ByteArray(32) // 256 bits
        random.nextBytes(key)
        return key
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey?
        if (secretKey != null) return secretKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun savePassphrase(context: Context, passphrase: ByteArray) {
        val secretKey = getSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encryptedPass = cipher.doFinal(passphrase)
        val iv = cipher.iv

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(ENCRYPTED_PASS_KEY, Base64.encodeToString(encryptedPass, Base64.DEFAULT))
            .putString(IV_KEY, Base64.encodeToString(iv, Base64.DEFAULT))
            .apply()
    }

    private fun decryptPassphrase(encryptedPassphrase: ByteArray, iv: ByteArray): ByteArray {
        val secretKey = getSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(encryptedPassphrase)
    }

    private const val PREF_KEY_ALIAS = "pennywise_pref_key"

    private fun getPrefSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val secretKey = keyStore.getKey(PREF_KEY_ALIAS, null) as SecretKey?
        if (secretKey != null) return secretKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            PREF_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encryptString(plainText: String): String {
        val secretKey = getPrefSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        
        val encryptedBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        return "$encryptedBase64:$ivBase64"
    }

    fun decryptString(encryptedTextWithIv: String): String {
        val parts = encryptedTextWithIv.split(":")
        if (parts.size != 2) throw IllegalArgumentException("Invalid encrypted text format")
        
        val encrypted = Base64.decode(parts[0], Base64.NO_WRAP)
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        
        val secretKey = getPrefSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, Charsets.UTF_8)
    }
}

