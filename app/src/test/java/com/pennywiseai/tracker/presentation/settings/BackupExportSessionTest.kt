package com.pennywiseai.tracker.presentation.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.core.AppForegroundState
import com.pennywiseai.tracker.data.backup.BackupExporter
import com.pennywiseai.tracker.data.backup.BackupImporter
import com.pennywiseai.tracker.data.backup.ExportResult
import com.pennywiseai.tracker.data.backup.ImportResult
import com.pennywiseai.tracker.data.events.MascotMoments
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.ui.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** One export at a time, and Save writes the export the user chose to save. */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupExportSessionTest {

    private val context = mockk<Context>(relaxed = true)
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)
    private val exporter = mockk<BackupExporter>()
    private val importer = mockk<BackupImporter>()
    private lateinit var viewModel: SettingsViewModel
    private lateinit var plainFile: File
    private lateinit var encryptedFile: File

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { preferences.baseCurrency } returns flowOf("INR")
        every { preferences.exportIncludeReceipts } returns flowOf(false)
        plainFile = File.createTempFile("plain", ".pennywisebackup").apply { writeText("plain") }
        encryptedFile = File.createTempFile("encrypted", ".pennywisebackup").apply { writeText("encrypted") }
        viewModel = SettingsViewModel(
            context = context,
            modelRepository = mockk(relaxed = true),
            userPreferencesRepository = preferences,
            unrecognizedSmsRepository = mockk(relaxed = true),
            transactionRepository = mockk(relaxed = true),
            deleteAllTransactionsUseCase = mockk(relaxed = true),
            accountBalanceRepository = mockk(relaxed = true),
            backupExporter = exporter,
            backupImporter = importer,
            importCsvUseCase = mockk(relaxed = true),
            folderBackupWriter = mockk(relaxed = true),
            scheduledFolderBackupScheduler = mockk(relaxed = true),
            contactsResolver = mockk(relaxed = true),
            modelDownloader = mockk(relaxed = true),
            moneyAlertChecker = mockk(relaxed = true),
            budgetGroupRepository = mockk(relaxed = true),
            currencyConversionService = mockk(relaxed = true),
            mascotMoments = MascotMoments(AppForegroundState { true }),
            entitlementGate = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        plainFile.delete()
        encryptedFile.delete()
        Dispatchers.resetMain()
    }

    @Test
    fun `an export started while another runs is refused out loud and its passphrase cleared`() {
        val running = CompletableDeferred<ExportResult>()
        coEvery { exporter.exportBackup(any(), any(), any()) } coAnswers {
            if (thirdArg<CharArray?>() == null) running.await() else ExportResult.Success(encryptedFile)
        }
        val passphrase = "synthetic".toCharArray()

        viewModel.exportBackup()
        assertTrue(viewModel.isExportingBackup.value)
        viewModel.exportBackup(passphrase)

        assertEquals(UiText.Res(R.string.settings_export_already_running), viewModel.importExportMessage.value)
        assertArrayEquals(CharArray(passphrase.size), passphrase)
        running.complete(ExportResult.Success(plainFile))

        coVerify(exactly = 1) { exporter.exportBackup(any(), any(), any()) }
        assertFalse(viewModel.isExportingBackup.value)
        assertEquals(plainFile, viewModel.exportedBackupFile.value)
        assertFalse(viewModel.exportedBackupEncrypted.value)
    }

    @Test
    fun `an encrypted export is marked encrypted, and a plain one after it is not`() {
        coEvery { exporter.exportBackup(any(), any(), any<CharArray>()) } returns ExportResult.Success(encryptedFile)
        coEvery { exporter.exportBackup(any(), any(), null) } returns ExportResult.Success(plainFile)

        viewModel.exportBackup("synthetic".toCharArray())
        assertTrue(viewModel.exportedBackupEncrypted.value)
        viewModel.exportBackup()
        assertFalse(viewModel.exportedBackupEncrypted.value)
    }

    @Test
    fun `save writes the export it was picked for, not a newer one`() {
        val written = ByteArrayOutputStream()
        every { context.contentResolver.openOutputStream(any<Uri>()) } returns written
        coEvery { exporter.exportBackup(any(), any(), null) } returns ExportResult.Success(plainFile)
        viewModel.exportBackup()
        val shownPath = viewModel.exportedBackupFile.value?.path
        coEvery { exporter.exportBackup(any(), any(), any<CharArray>()) } returns ExportResult.Success(encryptedFile)
        viewModel.exportBackup("synthetic".toCharArray())

        viewModel.clearImportExportMessage()
        viewModel.saveBackupToFile(mockk(), shownPath)
        val message = runBlocking { withTimeout(5_000) { viewModel.importExportMessage.first { it != null } } }

        assertEquals(0, written.size())
        assertEquals(
            UiText.Res(R.string.settings_backup_save_failed, listOf("The backup is no longer available. Export it again")),
            message
        )
    }

    @Test
    fun `a slow save of an older export leaves a newer export and its status alone`() {
        val opened = CountDownLatch(1)
        val release = CountDownLatch(1)
        val slowStream = object : OutputStream() {
            override fun write(b: Int) {
                release.await()
            }
        }
        every { context.contentResolver.openOutputStream(any<Uri>()) } answers {
            opened.countDown()
            slowStream
        }
        coEvery { exporter.exportBackup(any(), any(), null) } returns ExportResult.Success(plainFile)
        viewModel.exportBackup()
        val shownPath = viewModel.exportedBackupFile.value?.path
        viewModel.clearImportExportMessage()
        val scope = viewModel.viewModelScope.coroutineContext.job
        val before = scope.children.toSet()
        viewModel.saveBackupToFile(mockk(), shownPath)
        val save = (scope.children.toSet() - before).single()
        assertTrue(opened.await(5, TimeUnit.SECONDS))

        coEvery { exporter.exportBackup(any(), any(), any<CharArray>()) } returns ExportResult.Success(encryptedFile)
        viewModel.exportBackup("synthetic".toCharArray())
        release.countDown()
        runBlocking { withTimeout(5_000) { save.join() } }

        assertEquals(encryptedFile, viewModel.exportedBackupFile.value)
        assertEquals(UiText.Res(R.string.settings_backup_created_choose), viewModel.importExportMessage.value)
    }

    @Test
    fun `a restore stays in progress while another backup action posts its status`() {
        val restoring = CompletableDeferred<ImportResult>()
        coEvery { importer.importBackup(any(), any(), any()) } coAnswers { restoring.await() }
        coEvery { exporter.exportBackup(any(), any(), any()) } returns ExportResult.Error("synthetic")

        viewModel.importBackup(mockk())
        viewModel.exportBackup()

        assertTrue(viewModel.isRestoringBackup.value)
        restoring.complete(ImportResult.PassphraseRequired)
        assertFalse(viewModel.isRestoringBackup.value)
    }

    @Test
    fun `the restore passphrase is cleared once the import is done`() {
        coEvery { importer.importBackup(any(), any(), any()) } returns ImportResult.WrongPassphrase
        val passphrase = "synthetic".toCharArray()

        viewModel.importBackup(mockk(), passphrase)

        assertArrayEquals(CharArray(passphrase.size), passphrase)
    }

    @Test
    fun `a passphrase is cleared even when its job is cancelled before it starts`() {
        viewModel.viewModelScope.cancel()
        val exportPassphrase = "synthetic".toCharArray()
        val restorePassphrase = "synthetic".toCharArray()

        viewModel.exportBackup(exportPassphrase)
        viewModel.importBackup(mockk(), restorePassphrase)

        assertArrayEquals(CharArray(exportPassphrase.size), exportPassphrase)
        assertArrayEquals(CharArray(restorePassphrase.size), restorePassphrase)
        assertFalse(viewModel.isExportingBackup.value)
        assertFalse(viewModel.isRestoringBackup.value)
    }
}
