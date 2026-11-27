package com.pennywiseai.tracker.data.backup

import android.content.Context
import android.content.SharedPreferences
import com.pennywiseai.tracker.core.DeviceSecretBox
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The passphrase automatic folder backups are encrypted with, sealed with an Android Keystore
 * key. It lives in app-private prefs, outside PennyWise backups and Android backup
 * (allowBackup=false), so it never travels with a backup file.
 *
 * Writes are committed before the status changes, and "encryption is on" is its own flag:
 * a sealed passphrase that's missing or can't be read stops backups, never turns them plain.
 * Call from a background thread.
 */
@Singleton
class FolderBackupPassphrase @Inject constructor(
    @ApplicationContext context: Context
) {
    enum class Status {
        /** Folder backups are written plain. */
        OFF,

        /** Folder backups are encrypted with the saved passphrase. */
        ON,

        /** A passphrase is saved but this device can no longer unseal it: backups stop until it's entered again. */
        LOST
    }

    /** The saved passphrase couldn't be unsealed. Never fall back to a plain backup. */
    class LostException(cause: Throwable?) : Exception("Backup passphrase can't be unlocked", cause)

    private val prefs = context.getSharedPreferences("folder_backup", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(
        when {
            !isRequired() -> Status.OFF
            prefs.getBoolean(KEY_LOST, false) || !prefs.contains(KEY_SEALED) -> Status.LOST
            else -> Status.ON
        }
    )
    val status: StateFlow<Status> = _status.asStateFlow()

    /**
     * Encrypts folder backups from now on with [passphrase]; the caller clears the array.
     *
     * @throws IOException when it couldn't be stored; nothing changes then.
     */
    fun save(passphrase: CharArray) {
        val sealed = DeviceSecretBox.seal(String(passphrase), ALIAS)
        commit { putString(KEY_SEALED, sealed).putBoolean(KEY_REQUIRED, true).remove(KEY_LOST) }
        _status.value = Status.ON
    }

    /**
     * Folder backups are written plain from now on.
     *
     * @throws IOException when that couldn't be stored; they stay encrypted then.
     */
    fun clear() {
        commit { remove(KEY_SEALED).remove(KEY_REQUIRED).remove(KEY_LOST) }
        _status.value = Status.OFF
    }

    /**
     * The passphrase to encrypt the next folder backup with (the caller clears it), or null
     * when folder backups aren't encrypted.
     *
     * @throws LostException when the saved passphrase is gone or can't be unsealed.
     */
    fun load(): CharArray? {
        if (!isRequired()) return null
        val sealed = prefs.getString(KEY_SEALED, null) ?: throw markLost(null)
        return try {
            DeviceSecretBox.open(sealed, ALIAS).toCharArray()
        } catch (e: Exception) {
            throw markLost(e)
        }
    }

    // Either one means "encrypt": a half-present setting fails closed.
    private fun isRequired() = prefs.getBoolean(KEY_REQUIRED, false) || prefs.contains(KEY_SEALED)

    private fun markLost(cause: Throwable?): LostException {
        prefs.edit().putBoolean(KEY_LOST, true).commit()
        _status.value = Status.LOST
        return LostException(cause)
    }

    private fun commit(change: SharedPreferences.Editor.() -> SharedPreferences.Editor) {
        if (!prefs.edit().change().commit()) throw IOException("Couldn't store the folder backup passphrase setting")
    }

    internal companion object {
        const val ALIAS = "pennywise_folder_backup_passphrase"
        const val KEY_SEALED = "passphrase_sealed"
        const val KEY_REQUIRED = "encryption_required"
        const val KEY_LOST = "passphrase_lost"
    }
}
