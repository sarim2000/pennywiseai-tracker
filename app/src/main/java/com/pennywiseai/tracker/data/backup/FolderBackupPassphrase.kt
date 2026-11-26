package com.pennywiseai.tracker.data.backup

import android.content.Context
import androidx.core.content.edit
import com.pennywiseai.tracker.core.DeviceSecretBox
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The passphrase automatic folder backups are encrypted with, sealed with an Android Keystore
 * key. It lives in app-private prefs, outside PennyWise backups and Android backup
 * (allowBackup=false), so it never travels with a backup file.
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
    class LostException(cause: Throwable) : Exception("Backup passphrase can't be unlocked", cause)

    private val prefs = context.getSharedPreferences("folder_backup", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(
        when {
            !prefs.contains(KEY_SEALED) -> Status.OFF
            prefs.getBoolean(KEY_LOST, false) -> Status.LOST
            else -> Status.ON
        }
    )
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Encrypts folder backups from now on with [passphrase]; the caller clears the array. */
    fun save(passphrase: CharArray) {
        val sealed = DeviceSecretBox.seal(String(passphrase), ALIAS)
        prefs.edit { putString(KEY_SEALED, sealed); remove(KEY_LOST) }
        _status.value = Status.ON
    }

    /** Folder backups are written plain from now on. */
    fun clear() {
        prefs.edit { remove(KEY_SEALED); remove(KEY_LOST) }
        _status.value = Status.OFF
    }

    /**
     * The passphrase to encrypt the next folder backup with (the caller clears it), or null
     * when folder backups aren't encrypted.
     *
     * @throws LostException when the saved passphrase can't be unsealed (the Keystore key is gone).
     */
    fun load(): CharArray? {
        val sealed = prefs.getString(KEY_SEALED, null) ?: return null
        return try {
            DeviceSecretBox.open(sealed, ALIAS).toCharArray()
        } catch (e: Exception) {
            prefs.edit { putBoolean(KEY_LOST, true) }
            _status.value = Status.LOST
            throw LostException(e)
        }
    }

    private companion object {
        const val ALIAS = "pennywise_folder_backup_passphrase"
        const val KEY_SEALED = "passphrase_sealed"
        const val KEY_LOST = "passphrase_lost"
    }
}
