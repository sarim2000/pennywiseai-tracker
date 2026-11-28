package com.pennywiseai.tracker.data.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pennywiseai.tracker.core.DeviceSecretBox
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.folderBackupEncryptionStore: DataStore<Preferences> by preferencesDataStore(
    name = "folder_backup_encryption",
    corruptionHandler = FolderBackupPassphrase.corruptionHandler
)

/**
 * Whether automatic folder backups are encrypted, and the passphrase they're encrypted with,
 * sealed with an Android Keystore key. It lives in its own app-private DataStore, outside
 * PennyWise backups and Android backup (allowBackup=false), so it never travels with a
 * backup file.
 *
 * A DataStore edit is all or nothing: a write that fails throws and leaves the setting as it
 * was. A setting that can't be read stops backups; it never turns them plain.
 */
@Singleton
class FolderBackupPassphrase internal constructor(
    private val store: DataStore<Preferences>,
    private val statusRetryDelayMs: Long = STATUS_RETRY_DELAY_MS
) {
    // Production always takes the delegate: a second DataStore on the same file throws.
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.folderBackupEncryptionStore)

    enum class Status {
        /** Folder backups are written plain. */
        OFF,

        /** Folder backups are encrypted with the saved passphrase. */
        ON,

        /** Encryption is on but the passphrase couldn't be unlocked: no backup is written until it is. */
        LOCKED
    }

    /** The saved passphrase couldn't be unlocked. Never fall back to a plain backup. */
    class LockedException(cause: Throwable?) : Exception("Backup passphrase can't be unlocked", cause)

    // A backup that couldn't unlock it since: a later one that can, or a new passphrase, clears it.
    private val unlockFailed = MutableStateFlow(false)

    /**
     * Opening the saved passphrase is tried each time this is read, so a key lost while the
     * app wasn't running (an earlier failure isn't kept on disk) shows as locked too.
     */
    val status: Flow<Status> = combine(
        store.data.map { prefs ->
            when {
                !isRequired(prefs) -> Status.OFF
                !opens(prefs) -> Status.LOCKED
                else -> Status.ON
            }
        }.flowOn(Dispatchers.IO).retryWhen { _, _ ->
            // An unreadable setting shows as locked meanwhile; a read that works again shows it.
            emit(Status.LOCKED)
            delay(statusRetryDelayMs)
            true
        },
        unlockFailed
    ) { stored, failed -> if (stored == Status.ON && failed) Status.LOCKED else stored }

    /**
     * Encrypts folder backups from now on with [passphrase]; the caller clears the array.
     * It's sealed under a fresh Keystore key, which takes over only once it's stored; the
     * previous key is deleted after that.
     *
     * @throws IOException when it couldn't be stored; nothing changes then.
     */
    suspend fun save(passphrase: CharArray) {
        val alias = ALIAS_PREFIX + UUID.randomUUID()
        val sealed = DeviceSecretBox.seal(String(passphrase), alias)
        var previous: String? = null
        try {
            store.edit { prefs ->
                previous = prefs[KEY_ALIAS]
                prefs[KEY_ENCRYPT] = true
                prefs[KEY_SEALED] = sealed
                prefs[KEY_ALIAS] = alias
            }
        } catch (e: IOException) {
            runCatching { DeviceSecretBox.delete(alias) }
            throw e
        }
        unlockFailed.value = false
        previous?.takeIf { it != alias }?.let { old -> runCatching { DeviceSecretBox.delete(old) } }
    }

    /**
     * Folder backups are written plain from now on.
     *
     * @throws IOException when that couldn't be stored; they stay encrypted then.
     */
    suspend fun clear() {
        var previous: String? = null
        store.edit { prefs ->
            previous = prefs[KEY_ALIAS]
            prefs[KEY_ENCRYPT] = false
            prefs.remove(KEY_SEALED)
            prefs.remove(KEY_ALIAS)
        }
        unlockFailed.value = false
        previous?.let { old -> runCatching { DeviceSecretBox.delete(old) } }
    }

    /**
     * The passphrase to encrypt the next folder backup with (the caller clears it), or null
     * only when the stored setting says folder backups are plain.
     *
     * @throws IOException when the setting couldn't be read.
     * @throws LockedException when encryption is on but the passphrase can't be unlocked.
     */
    suspend fun load(): CharArray? {
        val prefs = store.data.first()
        if (!isRequired(prefs)) return null
        val passphrase = try {
            open(prefs).toCharArray()
        } catch (e: Exception) {
            unlockFailed.value = true
            throw LockedException(e)
        }
        unlockFailed.value = false
        return passphrase
    }

    /** Whether the saved passphrase opens; the opened copy is dropped. Never creates a key. */
    private fun opens(prefs: Preferences): Boolean = runCatching { open(prefs) }.isSuccess

    private fun open(prefs: Preferences): String {
        val sealed = prefs[KEY_SEALED] ?: throw GeneralSecurityException("No saved passphrase")
        val alias = prefs[KEY_ALIAS] ?: throw GeneralSecurityException("No key for the saved passphrase")
        return DeviceSecretBox.open(sealed, alias)
    }

    internal companion object {
        /** Each passphrase gets its own key: this plus a random suffix, stored beside it. */
        const val ALIAS_PREFIX = "pennywise_folder_backup_passphrase_"
        const val STATUS_RETRY_DELAY_MS = 2_000L
        val KEY_ENCRYPT = booleanPreferencesKey("encrypt")
        val KEY_SEALED = stringPreferencesKey("passphrase_sealed")
        val KEY_ALIAS = stringPreferencesKey("passphrase_key_alias")

        /**
         * A file that can't be parsed is replaced by "encrypt, no passphrase": nothing goes out
         * plain, Settings asks for the passphrase again, and saving or turning it off works.
         */
        val corruptionHandler = ReplaceFileCorruptionHandler<Preferences> { preferencesOf(KEY_ENCRYPT to true) }

        // Either one means "encrypt": a half-present setting fails closed.
        fun isRequired(prefs: Preferences) = prefs[KEY_ENCRYPT] == true || prefs[KEY_SEALED] != null
    }
}
