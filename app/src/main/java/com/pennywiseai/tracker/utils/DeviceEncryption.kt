package com.pennywiseai.tracker.utils

import android.content.Context
import android.provider.Settings
import android.util.Base64
import com.pennywiseai.tracker.BuildConfig
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

object DeviceEncryption {

    /**
     * Encrypts device ID with timestamp using RSA public key
     * @param context Android context to get device ID
     * @return Base64 encoded encrypted data or null if encryption fails
     */
    fun encryptDeviceData(context: Context): String? {
        return try {
            // Get Android device ID
            val deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"

            // Add timestamp for replay protection
            val timestamp = System.currentTimeMillis()
            val dataToEncrypt = "$deviceId|$timestamp"

            // Load public key
            val publicKey = loadPublicKey()
            if (publicKey == null) {
                android.util.Log.e("DeviceEncryption", "Failed to load public key")
                return null
            }

            // Encrypt with RSA-OAEP
            val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encryptedBytes = cipher.doFinal(dataToEncrypt.toByteArray())

            // Return as Base64
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("DeviceEncryption", "Encryption failed", e)
            null
        }
    }

    /**
     * Loads the RSA public key from BuildConfig
     */
    private fun loadPublicKey(): PublicKey? {
        // Get key from BuildConfig
        val publicKeyBase64 = BuildConfig.RSA_PUBLIC_KEY

        // Check if key is available
        if (publicKeyBase64.isEmpty()) {
            android.util.Log.e("DeviceEncryption", "RSA public key not configured in BuildConfig")
            return null
        }

        return try {
            // Decode Base64 directly (no PEM headers needed)
            val keyBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT)

            // Generate public key
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            keyFactory.generatePublic(keySpec)
        } catch (e: Exception) {
            android.util.Log.e("DeviceEncryption", "Failed to load public key", e)
            null
        }
    }
}