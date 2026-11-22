package com.pennywiseai.tracker.debug

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.pennywiseai.tracker.BuildConfig
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DemoSeeder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: PennyWiseDatabase,
    private val preferences: UserPreferencesRepository
) {
    private val mutex = Mutex()

    suspend fun seed(today: LocalDate): DemoSeedStatus = withContext(Dispatchers.IO) {
        mutex.withLock { seedUnlocked(today) }
    }

    private suspend fun seedUnlocked(today: LocalDate): DemoSeedStatus {
        val status = database.seedDemoData(today, ::writeReceipt)
        if (status != DemoSeedStatus.HAS_DATA && !preferences.userPreferences.first().hasCompletedOnboarding) {
            preferences.updateBaseCurrency(DEMO_CURRENCY)
            preferences.updateSkippedSmsPermission(true)
            preferences.markScanTutorialShown()
            preferences.setBalanceHidden(false)
            preferences.setLastSeenAppVersion(BuildConfig.VERSION_NAME)
            preferences.updateHasCompletedOnboarding(true, freshInstall = true)
        }
        return status
    }

    private fun writeReceipt(): String {
        val path = "receipts/pennywise-debug-demo.pdf"
        val directory = File(context.filesDir, "receipts")
        check(directory.isDirectory || directory.mkdirs())
        val temporary = File.createTempFile("demo-receipt-", ".tmp", directory)
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(360, 200, 1).create())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f }
            val amount = CurrencyFormatter.formatCurrency(BigDecimal("34.000"), DEMO_CURRENCY, maskIfHidden = false)
            page.canvas.drawText("PennyWise test receipt", 24f, 42f, paint)
            page.canvas.drawText("Demo Market   $amount", 24f, 76f, paint)
            pdf.finishPage(page)
            temporary.outputStream().use(pdf::writeTo)
            check(temporary.renameTo(File(context.filesDir, path)))
        } finally {
            pdf.close()
            temporary.delete()
        }
        return path
    }
}
