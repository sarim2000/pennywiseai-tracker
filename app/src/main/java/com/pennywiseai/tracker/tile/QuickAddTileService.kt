package com.pennywiseai.tracker.tile

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.quicksettings.TileService
import com.pennywiseai.tracker.MainActivity

/**
 * Quick Settings tile that lands straight on Add Transaction.
 *
 * Android has no back-tap API, so this is the closest thing to a
 * from-anywhere quick-add gesture that works on every OEM: swipe down, tap.
 */
class QuickAddTileService : TileService() {

    override fun onClick() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("${MainActivity.DEEP_LINK_SCHEME}://${MainActivity.DEEP_LINK_HOST_ADD}"),
            this,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // Tapping from the lock screen asks for the keyguard before running.
        unlockAndRun {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // startActivityAndCollapse(Intent) throws from API 34 onwards.
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }
}
