package com.pennywiseai.tracker.aiaccess

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.billing.EntitlementGate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Quick Settings tile: one swipe to start or stop a 30-minute AI session from inside Claude or ChatGPT. */
@AndroidEntryPoint
class AiSessionTileService : TileService() {

    @Inject lateinit var state: AiSessionState
    @Inject lateinit var entitlementGate: EntitlementGate

    override fun onStartListening() {
        val tile = qsTile ?: return
        val on = state.session.value != null
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = getString(if (on) R.string.ai_session_tile_on else R.string.ai_session_tile_off)
        tile.updateTile()
    }

    override fun onClick() {
        unlockAndRun {
            when {
                state.session.value != null -> AiSessionService.stop(this)
                // Not Pro: open the app, where AI access explains the upgrade.
                !entitlementGate.isProEntitled.value -> openApp()
                else -> runCatching { AiSessionService.start(this, 30) }.onFailure { openApp() }
            }
            onStartListening()
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
