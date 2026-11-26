package com.pennywiseai.tracker.data.backup

import android.content.Context
import android.net.Uri
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** An encrypted backup restores with its passphrase, and nothing is touched without it. */
class BackupImporterEncryptedTest {
    @get:Rule val temp = TemporaryFolder()

    private val passphrase = "synthetic test passphrase"
    private val backup = PennyWiseBackup(
        created = "2026-01-02T10:15:30",
        metadata = BackupMetadata(exportId = "test-export", appVersion = "test")
    )
    private val plain = backupJson.encodeToString(backup).toByteArray()
    private val encrypted = ByteArrayOutputStream().also {
        BackupCrypto.encrypt(ByteArrayInputStream(plain), it, passphrase.toCharArray())
    }.toByteArray()

    // Strict: any call into the database fails the test.
    private val database = mockk<PennyWiseDatabase>()
    private val uri = mockk<Uri>()
    private val context = mockk<Context> {
        every { cacheDir } answers { temp.root }
        every { contentResolver.openInputStream(uri) } answers { ByteArrayInputStream(encrypted) }
    }
    private val importer = BackupImporter(context, database, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))

    @Test
    fun encryptedBackupDecodesWithItsPassphrase() {
        val decoded = importer.decodeBackup(ByteArrayInputStream(encrypted), passphrase.toCharArray(), temp.root)
        assertEquals(backup, decoded)
        assertTrue("decrypted temp file is removed", temp.root.listFiles()!!.isEmpty())
    }

    @Test
    fun plainBackupStillDecodesWithoutPassphrase() {
        assertEquals(backup, importer.decodeBackup(ByteArrayInputStream(plain), null, temp.root))
    }

    @Test
    fun wrongPassphraseLeavesTheDatabaseUntouched() = runBlocking {
        val result = importer.importBackup(uri, passphrase = "not the passphrase".toCharArray())
        assertEquals(ImportResult.WrongPassphrase, result)
        confirmVerified(database)
        assertTrue("decrypted temp file is removed", temp.root.listFiles()!!.isEmpty())
    }

    @Test
    fun missingPassphraseAsksForOne() = runBlocking {
        assertEquals(ImportResult.PassphraseRequired, importer.importBackup(uri))
        confirmVerified(database)
    }
}
