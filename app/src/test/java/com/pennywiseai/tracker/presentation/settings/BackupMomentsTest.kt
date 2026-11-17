package com.pennywiseai.tracker.presentation.settings

import android.content.Context
import android.net.Uri
import com.pennywiseai.tracker.backup.folder.FolderBackupWriter
import com.pennywiseai.tracker.core.AppForegroundState
import com.pennywiseai.tracker.data.backup.BackupExporter
import com.pennywiseai.tracker.data.backup.BackupImporter
import com.pennywiseai.tracker.data.backup.ExportResult
import com.pennywiseai.tracker.data.backup.ImportResult
import com.pennywiseai.tracker.data.events.MascotMoment
import com.pennywiseai.tracker.data.events.MascotMoments
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.widget.WidgetRefresher
import com.pennywiseai.tracker.worker.DailySummaryWorker
import com.pennywiseai.tracker.worker.WeeklySummaryWorker
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/** The shield shows only for a backup the user wrote or restored, and only once it's done. */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupMomentsTest {

    private val context = mockk<Context>(relaxed = true)
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)
    private val exporter = mockk<BackupExporter>()
    private val importer = mockk<BackupImporter>()
    private val writer = mockk<FolderBackupWriter>()
    private val moments = MascotMoments(AppForegroundState { true })
    private val heard = mutableListOf<MascotMoment>()
    private lateinit var listening: Job
    private lateinit var viewModel: SettingsViewModel
    private lateinit var backupFile: File

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(WidgetRefresher, DailySummaryWorker, WeeklySummaryWorker)
        every { WidgetRefresher.refreshTransactionWidgets(any()) } returns Unit
        every { DailySummaryWorker.sync(any(), any()) } returns Unit
        every { WeeklySummaryWorker.sync(any(), any()) } returns Unit
        every { preferences.baseCurrency } returns flowOf("INR")
        every { preferences.exportIncludeReceipts } returns flowOf(false)
        every { preferences.folderBackupIncludeReceipts } returns flowOf(false)
        every { preferences.dailySummaryEnabled } returns flowOf(false)
        every { preferences.weeklySummaryEnabled } returns flowOf(false)
        coEvery { preferences.getScheduledFolderBackupTreeUri() } returns "content://tree"
        every { writer.canWriteToFolder(any()) } returns true
        backupFile = File.createTempFile("backup", ".pennywisebackup").apply { writeText("{}") }
        coEvery { exporter.exportBackup(any(), any()) } answers { ExportResult.Success(backupFile) }
        listening = CoroutineScope(Dispatchers.Unconfined).launch { moments.moments.collect { heard += it } }
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
            folderBackupWriter = writer,
            scheduledFolderBackupScheduler = mockk(relaxed = true),
            contactsResolver = mockk(relaxed = true),
            modelDownloader = mockk(relaxed = true),
            moneyAlertChecker = mockk(relaxed = true),
            budgetGroupRepository = mockk(relaxed = true),
            currencyConversionService = mockk(relaxed = true),
            mascotMoments = moments,
            entitlementGate = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        listening.cancel()
        backupFile.delete()
        unmockkObject(WidgetRefresher, DailySummaryWorker, WeeklySummaryWorker)
        Dispatchers.resetMain()
    }

    @Test
    fun `backing up to the folder by hand says so once it's written`() {
        every { writer.writeBackup(any(), any()) } returns FolderBackupWriter.Result.Success

        viewModel.backupToFolderNow()

        assertEquals(listOf<MascotMoment>(MascotMoment.BackupSaved), heard)
    }

    @Test
    fun `a folder backup that failed to write says nothing`() {
        every { writer.writeBackup(any(), any()) } returns FolderBackupWriter.Result.Failure("no space")

        viewModel.backupToFolderNow()

        assertEquals(emptyList<MascotMoment>(), heard)
    }

    @Test
    fun `saving the exported backup to a file says so once it's copied`() {
        every { context.contentResolver.openOutputStream(any<Uri>()) } returns ByteArrayOutputStream()
        viewModel.exportBackup()
        // Making the file isn't keeping it: only the copy the user chose counts.
        assertEquals(emptyList<MascotMoment>(), heard)

        viewModel.saveBackupToFile(mockk())

        assertEquals(listOf<MascotMoment>(MascotMoment.BackupSaved), heard)
    }

    @Test
    fun `a file the system wouldn't open saves nothing and says nothing`() {
        every { context.contentResolver.openOutputStream(any<Uri>()) } returns null
        viewModel.exportBackup()

        viewModel.saveBackupToFile(mockk())

        assertEquals(emptyList<MascotMoment>(), heard)
    }

    @Test
    fun `restoring a backup says so once it's in`() {
        coEvery { importer.importBackup(any(), any()) } returns ImportResult.Success(3, 1, 0)

        viewModel.importBackup(mockk())

        assertEquals(listOf<MascotMoment>(MascotMoment.BackupRestored), heard)
    }

    @Test
    fun `a restore that failed says nothing`() {
        coEvery { importer.importBackup(any(), any()) } returns ImportResult.Error("Incompatible backup version")

        viewModel.importBackup(mockk())

        assertEquals(emptyList<MascotMoment>(), heard)
    }

    @Test
    fun `a restore that skipped rows it couldn't insert says nothing`() {
        coEvery { importer.importBackup(any(), any()) } returns ImportResult.Success(3, 1, 0, skippedRows = 2)

        viewModel.importBackup(mockk())

        assertEquals(emptyList<MascotMoment>(), heard)
    }
}
