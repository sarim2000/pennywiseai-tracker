package com.pennywiseai.tracker.worker

import com.pennywiseai.tracker.backup.folder.FolderBackupWriter
import com.pennywiseai.tracker.data.backup.BackupCrypto
import com.pennywiseai.tracker.data.backup.BackupExporter
import com.pennywiseai.tracker.data.backup.ExportResult
import com.pennywiseai.tracker.data.backup.FolderBackupPassphrase
import com.pennywiseai.tracker.data.preferences.BackupPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** A folder backup is encrypted with the saved passphrase, and never written plain when it can't be unlocked. */
class FolderBackupRunnerTest {
    @get:Rule val temp = TemporaryFolder()

    private val treeUri = "content://test/tree"
    private val storedPassphrase = "synthetic folder passphrase"
    private val exporter = mockk<BackupExporter>()
    private val writer = mockk<FolderBackupWriter>()
    private val passphrase = mockk<FolderBackupPassphrase>()
    private val preferences = mockk<BackupPreferences>(relaxed = true) {
        every { folderBackupIncludeReceipts } returns flowOf(false)
    }
    private val runner = FolderBackupRunner(exporter, writer, passphrase, preferences)

    // What the exporter was handed, and what reached the folder.
    private var exportedWith: String? = null
    private var handedArray: CharArray? = null
    private var written: ByteArray? = null

    init {
        // Encrypts the way BackupExporter does when it's given a passphrase.
        coEvery { exporter.exportBackup(any(), any(), any()) } coAnswers {
            val key = thirdArg<CharArray?>()
            handedArray = key
            exportedWith = key?.let { String(it) }
            val file = File(temp.root, "export.pennywisebackup")
            val json = "{}".byteInputStream()
            file.outputStream().use { out -> if (key == null) json.copyTo(out) else BackupCrypto.encrypt(json, out, key) }
            ExportResult.Success(file)
        }
        every { writer.writeBackup(treeUri, any()) } answers {
            written = secondArg<File>().readBytes()
            FolderBackupWriter.Result.Success
        }
    }

    @Test
    fun `an encrypted folder backup is written encrypted with the saved passphrase`() = runBlocking {
        every { passphrase.load() } answers { storedPassphrase.toCharArray() }

        val result = runner.run(treeUri)

        assertEquals(FolderBackupRunner.Result.Success(skippedReceipts = 0), result)
        assertEquals(storedPassphrase, exportedWith)
        assertTrue("folder file starts with the envelope magic", BackupCrypto.isEncrypted(written!!.copyOf(BackupCrypto.MAGIC_SIZE)))
        assertTrue("passphrase is cleared after use", handedArray!!.all { it == '\u0000' })
        assertFalse("the cache copy is removed", File(temp.root, "export.pennywisebackup").exists())
        coVerify { preferences.recordScheduledFolderBackup(any(), 0) }
    }

    @Test
    fun `a passphrase this device can't unlock writes nothing, not a plain backup`() = runBlocking {
        every { passphrase.load() } throws FolderBackupPassphrase.LostException(IllegalStateException("key gone"))

        val result = runner.run(treeUri)

        assertEquals(FolderBackupRunner.Result.PassphraseLost, result)
        coVerify(exactly = 0) { exporter.exportBackup(any(), any(), any()) }
        verify(exactly = 0) { writer.writeBackup(any(), any()) }
        coVerify(exactly = 0) { preferences.recordScheduledFolderBackup(any(), any()) }
    }

    @Test
    fun `a Keystore that fails this time writes nothing and can be retried`() = runBlocking {
        every { passphrase.load() } throws FolderBackupPassphrase.UnavailableException(IllegalStateException("busy"))

        val result = runner.run(treeUri)

        assertEquals(FolderBackupRunner.Result.PassphraseUnavailable, result)
        verify(exactly = 0) { writer.writeBackup(any(), any()) }
    }

    @Test
    fun `a backup in flight finishes before encryption turns on, so the encrypted one lands last`() = runBlocking {
        // A tiny settings store: on once save() is called.
        var saved: String? = null
        every { passphrase.load() } answers { saved?.toCharArray() }
        every { passphrase.save(any()) } answers { saved = String(firstArg<CharArray>()) }
        val exporting = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writes = mutableListOf<Boolean>()
        every { writer.writeBackup(treeUri, any()) } answers {
            writes += BackupCrypto.isEncrypted(secondArg<File>().readBytes().copyOf(BackupCrypto.MAGIC_SIZE))
            FolderBackupWriter.Result.Success
        }
        coEvery { exporter.exportBackup(any(), any(), null) } coAnswers {
            exporting.complete(Unit)
            release.await()
            ExportResult.Success(File(temp.root, "plain.pennywisebackup").apply { writeText("{}") })
        }

        // The nightly run read "plain" and is still exporting when the user turns encryption on.
        val nightly = async(Dispatchers.Default) { runner.run(treeUri) }
        exporting.await()
        val enable = async(Dispatchers.Default) {
            runner.saveEncryption(storedPassphrase.toCharArray())
            runner.run(treeUri)
        }
        delay(100)
        assertNull("encryption waits for the backup in flight", saved)
        release.complete(Unit)

        assertEquals(FolderBackupRunner.Result.Success(skippedReceipts = 0), nightly.await())
        assertEquals(FolderBackupRunner.Result.Success(skippedReceipts = 0), enable.await())
        assertEquals("plain first, then encrypted: the folder ends encrypted", listOf(false, true), writes)
    }

    @Test
    fun `without a passphrase the folder backup stays plain`() = runBlocking {
        every { passphrase.load() } returns null

        val result = runner.run(treeUri)

        assertEquals(FolderBackupRunner.Result.Success(skippedReceipts = 0), result)
        assertNull(exportedWith)
        assertEquals("{}", written!!.decodeToString())
    }

    @Test
    fun `a failed write is reported and not recorded`() = runBlocking {
        every { passphrase.load() } answers { storedPassphrase.toCharArray() }
        every { writer.writeBackup(treeUri, any()) } returns FolderBackupWriter.Result.Failure("no space")

        val result = runner.run(treeUri)

        assertEquals(FolderBackupRunner.Result.Failure("no space"), result)
        coVerify(exactly = 0) { preferences.recordScheduledFolderBackup(any(), any()) }
    }
}
