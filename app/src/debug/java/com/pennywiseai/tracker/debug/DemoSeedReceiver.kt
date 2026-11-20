package com.pennywiseai.tracker.debug

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Shell entry point for the disposable Debug install described in docs/debug-demo-data.md. */
@AndroidEntryPoint
class DemoSeedReceiver : BroadcastReceiver() {
    @Inject internal lateinit var seeder: DemoSeeder

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val today = LocalDate.now()
                val status = seeder.seed(today)
                if (status == DemoSeedStatus.HAS_DATA) {
                    result.resultCode = Activity.RESULT_CANCELED
                    result.resultData = "Debug database already contains data. Use a fresh disposable Debug install."
                    return@launch
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
}
