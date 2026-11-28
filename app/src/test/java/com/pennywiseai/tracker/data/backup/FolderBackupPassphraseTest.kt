package com.pennywiseai.tracker.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.pennywiseai.tracker.core.DeviceSecretBox
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException

/** The folder backup encryption setting is only "on" once it's stored, and anything unreadable fails closed. */
class FolderBackupPassphraseTest {
    @get:Rule val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val disk by lazy {
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(tmp.root, "folder_backup.preferences_pb") })
    }

    /** A Keystore stand-in: alias -> key, where sealing creates a key and opening never does. */
    private val keys = mutableMapOf<String, Int>()
    private var nextKey = 0

    @Before
    fun setUp() {
        mockkObject(DeviceSecretBox)
        every { DeviceSecretBox.seal(any(), any()) } answers {
            val key = keys.getOrPut(secondArg()) { nextKey++ }
            "$key:" + firstArg<String>()
        }
        every { DeviceSecretBox.open(any(), any()) } answers {
            val key = keys[secondArg()] ?: throw GeneralSecurityException("no key")
            val (sealedWith, plain) = firstArg<String>().split(":", limit = 2)
            if (sealedWith != key.toString()) throw GeneralSecurityException("tag mismatch")
            plain
        }
        every { DeviceSecretBox.delete(any()) } answers { keys.remove(firstArg()); Unit }
    }

    @After
    fun tearDown() {
        unmockkObject(DeviceSecretBox)
        scope.cancel()
    }

    @Test
    fun `nothing stored means plain backups`() = runBlocking {
        val store = FolderBackupPassphrase(disk)

        assertNull(store.load())
        assertEquals(FolderBackupPassphrase.Status.OFF, store.status.first())
    }

    @Test
    fun `a stored passphrase is on and opens after a restart`() = runBlocking {
        FolderBackupPassphrase(disk).save("synthetic phrase".toCharArray())

        val restarted = FolderBackupPassphrase(disk)

        assertEquals(FolderBackupPassphrase.Status.ON, restarted.status.first())
        assertArrayEquals("synthetic phrase".toCharArray(), restarted.load())
    }

    @Test
    fun `a passphrase that couldn't be stored leaves encryption off and its new key gone`() = runBlocking {
        val store = FolderBackupPassphrase(failingWrites(disk))

        assertThrows<IOException> { store.save("synthetic phrase".toCharArray()) }

        assertNull(store.load())
        assertTrue("the key made for it is deleted", keys.isEmpty())
    }

    @Test
    fun `turning encryption off that couldn't be stored keeps it on`() = runBlocking {
        FolderBackupPassphrase(disk).save("synthetic phrase".toCharArray())
        val store = FolderBackupPassphrase(failingWrites(disk))

        assertThrows<IOException> { store.clear() }

        assertArrayEquals("synthetic phrase".toCharArray(), store.load())
    }

    @Test
    fun `a setting that can't be read is an error, never a plain backup`() = runBlocking {
        val store = FolderBackupPassphrase(unreadable())

        assertThrows<IOException> { store.load() }
        assertEquals(FolderBackupPassphrase.Status.LOCKED, store.status.first())
    }

    @Test
    fun `the status shows locked while the setting can't be read, then the stored setting`() = runBlocking {
        FolderBackupPassphrase(disk).save("synthetic phrase".toCharArray())
        var failedReads = 0
        val flaky = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow {
                if (failedReads < 2) {
                    failedReads++
                    throw IOException("busy")
                }
                emitAll(disk.data)
            }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = disk.updateData(transform)
        }
        val store = FolderBackupPassphrase(flaky, statusRetryDelayMs = 1)

        val shown = withTimeout(5_000) { store.status.takeWhile { it != FolderBackupPassphrase.Status.ON }.toList() }

        assertTrue(shown.isNotEmpty() && shown.all { it == FolderBackupPassphrase.Status.LOCKED })
        assertEquals(2, failedReads)
        assertEquals(FolderBackupPassphrase.Status.ON, store.status.first())
    }

    @Test
    fun `encryption on without its sealed passphrase fails closed`() = runBlocking {
        FolderBackupPassphrase(disk).save("synthetic phrase".toCharArray())
        disk.edit { it.remove(FolderBackupPassphrase.KEY_SEALED) }

        val store = FolderBackupPassphrase(disk)

        assertThrows<FolderBackupPassphrase.LockedException> { store.load() }
        assertEquals(FolderBackupPassphrase.Status.LOCKED, store.status.first())
    }

    @Test
    fun `a missing key fails that backup without making one or marking anything on disk`() = runBlocking {
        val store = FolderBackupPassphrase(disk)
        store.save("synthetic phrase".toCharArray())
        keys.clear()

        assertThrows<FolderBackupPassphrase.LockedException> { store.load() }

        assertTrue("unlocking never creates a key", keys.isEmpty())
        assertEquals(FolderBackupPassphrase.Status.LOCKED, store.status.first())
        assertTrue("nothing marked on disk", disk.data.first()[FolderBackupPassphrase.KEY_SEALED] != null)
    }

    @Test
    fun `a key lost while the app wasn't running shows as locked after a restart`() = runBlocking {
        FolderBackupPassphrase(disk).save("synthetic phrase".toCharArray())
        keys.clear()

        val restarted = FolderBackupPassphrase(disk)

        assertEquals(FolderBackupPassphrase.Status.LOCKED, restarted.status.first())
        assertTrue("checking never creates a key", keys.isEmpty())
    }

    @Test
    fun `a corrupted setting resets to encrypted with no passphrase, and can be saved or turned off`() = runBlocking {
        val file = File(tmp.root, "corrupt.preferences_pb").apply { writeBytes(byteArrayOf(0x0A, 0x7F)) }
        val corrupt = PreferenceDataStoreFactory.create(
            corruptionHandler = FolderBackupPassphrase.corruptionHandler,
            scope = scope,
            produceFile = { file }
        )
        val store = FolderBackupPassphrase(corrupt)

        assertEquals(FolderBackupPassphrase.Status.LOCKED, store.status.first())
        assertThrows<FolderBackupPassphrase.LockedException> { store.load() }

        store.save("synthetic phrase".toCharArray())
        assertEquals(FolderBackupPassphrase.Status.ON, store.status.first())
        store.clear()
        assertEquals(FolderBackupPassphrase.Status.OFF, store.status.first())
    }

    @Test
    fun `an unlock that works again clears the warning`() = runBlocking {
        val store = FolderBackupPassphrase(disk)
        store.save("synthetic phrase".toCharArray())
        every { DeviceSecretBox.open(any(), any()) } throws GeneralSecurityException("keystore busy")
        assertThrows<FolderBackupPassphrase.LockedException> { store.load() }
        assertEquals(FolderBackupPassphrase.Status.LOCKED, store.status.first())

        every { DeviceSecretBox.open(any(), any()) } returns "synthetic phrase"

        assertArrayEquals("synthetic phrase".toCharArray(), store.load())
        assertEquals(FolderBackupPassphrase.Status.ON, store.status.first())
    }

    @Test
    fun `entering the passphrase again seals it under a fresh key and drops the old one`() = runBlocking {
        val store = FolderBackupPassphrase(disk)
        store.save("synthetic phrase".toCharArray())
        val firstAlias = disk.data.first()[FolderBackupPassphrase.KEY_ALIAS]!!
        keys[firstAlias] = -1 // replaced, so the old seal no longer opens
        assertThrows<FolderBackupPassphrase.LockedException> { store.load() }

        store.save("synthetic phrase".toCharArray())

        val secondAlias = disk.data.first()[FolderBackupPassphrase.KEY_ALIAS]!!
        assertNotEquals(firstAlias, secondAlias)
        assertEquals(setOf(secondAlias), keys.keys)
        assertEquals(FolderBackupPassphrase.Status.ON, store.status.first())
        assertArrayEquals("synthetic phrase".toCharArray(), store.load())
    }

    @Test
    fun `a failed re-entry keeps the passphrase and key in use`() = runBlocking {
        FolderBackupPassphrase(disk).save("synthetic phrase".toCharArray())
        val alias = disk.data.first()[FolderBackupPassphrase.KEY_ALIAS]!!
        val store = FolderBackupPassphrase(failingWrites(disk))

        assertThrows<IOException> { store.save("another synthetic phrase".toCharArray()) }

        assertEquals(setOf(alias), keys.keys)
        assertArrayEquals("synthetic phrase".toCharArray(), store.load())
    }

    @Test
    fun `turning encryption off deletes the key`() = runBlocking {
        val store = FolderBackupPassphrase(disk)
        store.save("synthetic phrase".toCharArray())

        store.clear()

        assertNull(store.load())
        assertTrue(keys.isEmpty())
        assertEquals(FolderBackupPassphrase.Status.OFF, store.status.first())
    }

    private suspend inline fun <reified T : Throwable> assertThrows(crossinline block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return
            throw e
        }
        fail("expected ${T::class.simpleName}")
    }

    /** Reads [real], but every write fails like a full disk. */
    private fun failingWrites(real: DataStore<Preferences>) = object : DataStore<Preferences> {
        override val data: Flow<Preferences> = real.data
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            throw IOException("disk full")
        }
    }

    private fun unreadable() = object : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("corrupt") }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            throw IOException("corrupt")
        }
    }
}
