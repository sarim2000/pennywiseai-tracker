package com.pennywiseai.tracker.debug

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Log
import com.pennywiseai.tracker.BuildConfig
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/** Shell entry point for the disposable Debug install described in docs/debug-demo-data.md. */
@AndroidEntryPoint
class DemoSeedReceiver : BroadcastReceiver() {
    @Inject lateinit var database: PennyWiseDatabase
    @Inject lateinit var preferences: UserPreferencesRepository

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val today = LocalDate.now()
                val status = database.seedDemoData(today) { writeReceipt(context.filesDir) }
                if (status == DemoSeedStatus.HAS_DATA) {
                    result.resultCode = Activity.RESULT_CANCELED
                    result.resultData = "Debug database already contains data. Use a fresh disposable Debug install."
                    return@launch
                }
                if (!preferences.userPreferences.first().hasCompletedOnboarding) {
                    preferences.updateBaseCurrency(DEMO_CURRENCY)
                    preferences.updateSkippedSmsPermission(true)
                    preferences.markScanTutorialShown()
                    preferences.setBalanceHidden(false)
                    preferences.setLastSeenAppVersion(BuildConfig.VERSION_NAME)
                    preferences.updateHasCompletedOnboarding(true)
                }
                result.resultCode = Activity.RESULT_OK
                result.resultData = if (status == DemoSeedStatus.CREATED) "Demo data seeded for $today." else "Demo data already present."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("DemoSeed", "Could not seed the Debug database", e)
                result.resultCode = Activity.RESULT_CANCELED
                result.resultData = "Demo seed failed. See DemoSeed in logcat."
            } finally {
                result.finish()
            }
        }
    }

    private fun writeReceipt(filesDir: File): String {
        val path = "receipts/pennywise-debug-demo.pdf"
        val directory = File(filesDir, "receipts")
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
            check(temporary.renameTo(File(filesDir, path)))
        } finally {
            pdf.close()
            temporary.delete()
        }
        return path
    }
}
