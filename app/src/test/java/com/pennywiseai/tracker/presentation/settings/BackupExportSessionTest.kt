package com.pennywiseai.tracker.presentation.settings

import android.content.Context
import android.net.Uri
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.core.AppForegroundState
import com.pennywiseai.tracker.data.backup.BackupExporter
import com.pennywiseai.tracker.data.backup.BackupImporter
import com.pennywiseai.tracker.data.backup.ExportResult
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

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
    fun `an export started while another runs is ignored and its passphrase cleared`() {
        val running = CompletableDeferred<ExportResult>()
        coEvery { exporter.exportBackup(any(), any(), any()) } coAnswers {
            if (thirdArg<CharArray?>() == null) running.await() else ExportResult.Success(encryptedFile)
        }
        val passphrase = "synthetic".toCharArray()

        viewModel.exportBackup()
        viewModel.exportBackup(passphrase)
        running.complete(ExportResult.Success(plainFile))

        coVerify(exactly = 1) { exporter.exportBackup(any(), any(), any()) }
        assertEquals(plainFile, viewModel.exportedBackupFile.value)
        assertArrayEquals(CharArray(passphrase.size), passphrase)
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
}
