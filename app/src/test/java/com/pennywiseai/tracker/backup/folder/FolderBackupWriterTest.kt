package com.pennywiseai.tracker.backup.folder

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/** The backup file is only ever replaced by a complete copy, and success means it holds the new one. */
class FolderBackupWriterTest {
    @get:Rule val temp = TemporaryFolder()

    private val final = ScheduledFolderBackupConstants.BACKUP_FILE_NAME
    private val tmp = ScheduledFolderBackupConstants.TEMP_BACKUP_FILE_NAME
    private val tree = FakeTree()
    private val context = mockk<Context> { every { contentResolver } returns tree.resolver }
    private val writer = FolderBackupWriter(context)

    @Before
    fun setUp() {
        mockkStatic(DocumentFile::class)
        every { DocumentFile.fromTreeUri(any(), any()) } returns tree.folder
    }

    @After
    fun tearDown() = unmockkStatic(DocumentFile::class)

    private fun backup(content: String) = File(temp.root, "export.pennywisebackup").apply { writeText(content) }

    @Test
    fun `a new backup replaces the old one and leaves no temp file`() {
        tree.put(final, "old")

        assertEquals(FolderBackupWriter.Result.Success, writer.writeBackup("tree", backup("new")))

        assertEquals(mapOf(final to "new"), tree.contents())
    }

    @Test
    fun `an old backup the provider won't delete is written over in place`() {
        tree.put(final, "old")
        tree.undeletable += final

        assertEquals(FolderBackupWriter.Result.Success, writer.writeBackup("tree", backup("new")))

        assertEquals(mapOf(final to "new"), tree.contents())
    }

    @Test
    fun `an old backup the provider won't delete or overwrite still gets the new copy beside it`() {
        tree.put(final, "old")
        tree.undeletable += final
        tree.unwritable += final

        val result = writer.writeBackup("tree", backup("new"))

        assertEquals(FolderBackupWriter.Result.NameUnconfirmed, result)
        assertEquals(mapOf(final to "old", "$final (1)" to "new"), tree.contents())
    }

    @Test
    fun `the same refused delete twice in a row still writes both backups`() {
        tree.put(final, "old")
        tree.undeletable += final

        assertEquals(FolderBackupWriter.Result.Success, writer.writeBackup("tree", backup("first")))
        assertEquals(FolderBackupWriter.Result.Success, writer.writeBackup("tree", backup("second")))

        assertEquals(mapOf(final to "second"), tree.contents())
    }

    @Test
    fun `a rename the provider suffixes keeps the new copy and says its name is unconfirmed`() {
        tree.put(final, "old")
        tree.lyingDeletes += final // says it deleted, keeps the file: the rename gets "name (1)"

        val result = writer.writeBackup("tree", backup("new"))

        assertEquals(FolderBackupWriter.Result.NameUnconfirmed, result)
        assertEquals(mapOf(final to "old", "$final (1)" to "new"), tree.contents())
    }

    @Test
    fun `a renamed backup whose name can't be read back is kept, never deleted`() {
        tree.put(final, "old")
        tree.unreadableNames += final // the name query after the rename fails

        val result = writer.writeBackup("tree", backup("new"))

        assertEquals(FolderBackupWriter.Result.NameUnconfirmed, result)
        assertEquals(mapOf(final to "new"), tree.contents())
    }

    @Test
    fun `a temp left by an interrupted run is removed, never put in place`() {
        tree.put(tmp, "stale")
        tree.unwritable += tmp // this run's own write fails

        val result = writer.writeBackup("tree", backup("new"))

        assertTrue(result is FolderBackupWriter.Result.Failure)
        assertEquals(emptyMap<String, String>(), tree.contents())
    }

    @Test
    fun `a stale temp beside the backup is replaced by this run's copy`() {
        tree.put(final, "old")
        tree.put(tmp, "stale")

        assertEquals(FolderBackupWriter.Result.Success, writer.writeBackup("tree", backup("new")))

        assertEquals(mapOf(final to "new"), tree.contents())
    }

    @Test
    fun `a stale temp the provider won't delete doesn't stop the backup, and is never put in place`() {
        tree.put(final, "old")
        tree.put(tmp, "stale")
        tree.undeletable += tmp

        assertEquals(FolderBackupWriter.Result.Success, writer.writeBackup("tree", backup("new")))

        assertEquals(mapOf(final to "new", tmp to "stale"), tree.contents())
    }

    @Test
    fun `without rename the backup is copied into place and the temp removed`() {
        tree.put(final, "old")
        tree.renameSupported = false

        assertEquals(FolderBackupWriter.Result.Success, writer.writeBackup("tree", backup("new")))

        assertEquals(mapOf(final to "new"), tree.contents())
    }

    @Test
    fun `without rename a suffixed backup file is a failure, not a second file`() {
        tree.put(final, "old")
        tree.renameSupported = false
        tree.lyingDeletes += final

        val result = writer.writeBackup("tree", backup("new"))

        assertTrue(result is FolderBackupWriter.Result.Failure)
        assertTrue("no \"name (1)\" copy", tree.contents().keys.none { it.startsWith("$final (") })
    }

    @Test
    fun `without rename a failed copy is reported`() {
        tree.put(final, "old")
        tree.renameSupported = false
        tree.unwritable += final

        val result = writer.writeBackup("tree", backup("new"))

        assertTrue(result is FolderBackupWriter.Result.Failure)
    }

    /** A SAF folder that suffixes clashing names like ExternalStorageProvider, with knobs for its failures. */
    private class FakeTree {
        private inner class Entry(var name: String, var content: String) {
            val uri = mockk<Uri>()
            val doc = mockk<DocumentFile> {
                every { name } answers { this@Entry.name.takeUnless { it in unreadableNames } }
                every { uri } returns this@Entry.uri
                every { delete() } answers {
                    when {
                        this@Entry.name in undeletable -> false
                        this@Entry.name in lyingDeletes -> true
                        else -> entries.remove(this@Entry)
                    }
                }
                every { renameTo(any()) } answers {
                    if (renameSupported) {
                        this@Entry.name = unique(firstArg())
                        true
                    } else {
                        false
                    }
                }
            }
        }

        private val entries = mutableListOf<Entry>()
        val undeletable = mutableSetOf<String>()
        val unwritable = mutableSetOf<String>()
        val lyingDeletes = mutableSetOf<String>()
        val unreadableNames = mutableSetOf<String>()
        var renameSupported = true

        val folder = mockk<DocumentFile> {
            every { isDirectory } returns true
            every { canWrite() } returns true
            every { findFile(any()) } answers { entries.firstOrNull { it.name == firstArg<String>() }?.doc }
            every { createFile(any(), any()) } answers { Entry(unique(secondArg()), "").also { entries += it }.doc }
        }

        val resolver = mockk<ContentResolver> {
            every { openOutputStream(any(), "wt") } answers {
                val entry = entries.first { it.uri === firstArg<Uri>() }
                if (entry.name in unwritable) throw IOException("disk full")
                object : ByteArrayOutputStream() {
                    override fun close() {
                        entry.content = toString(Charsets.UTF_8.name())
                    }
                }
            }
        }

        fun put(name: String, content: String) {
            entries += Entry(name, content)
        }

        fun contents(): Map<String, String> = entries.associate { it.name to it.content }

        private fun unique(name: String): String {
            if (entries.none { it.name == name }) return name
            var n = 1
            while (entries.any { it.name == "$name ($n)" }) n++
            return "$name ($n)"
        }
    }
}
