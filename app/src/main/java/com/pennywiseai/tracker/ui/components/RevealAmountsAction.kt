package com.pennywiseai.tracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.core.ApplicationScope
import com.pennywiseai.tracker.data.events.MascotMoment
import com.pennywiseai.tracker.data.events.MascotMoments
import com.pennywiseai.tracker.data.preferences.PrivacyPreferences
import com.pennywiseai.tracker.utils.AmountPrivacy
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The top-bar eye that shows amounts again while they're hidden app-wide
 * ([AmountPrivacy.hidden]), so a screen of "••••" says why and has a way out
 * without going back to Home. Renders nothing otherwise. [PennyWiseScaffold]'s
 * bar and [CustomTitleTopAppBar] put it first in their actions.
 */
@Composable
fun RevealAmountsAction() {
    if (!AmountPrivacy.hidden) return
    val context = LocalContext.current
    IconButton(
        onClick = {
            val app = EntryPointAccessors.fromApplication(context.applicationContext, RevealAmountsEntryPoint::class.java)
            // The app's scope: the eye leaves composition as soon as the amounts show.
            app.scope().launch { showAmounts(app.preferences(), app.mascotMoments()) }
        },
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(Icons.Default.VisibilityOff, contentDescription = stringResource(R.string.amounts_show))
    }
}

/**
 * Writes the same saved choice as Home's eye and the shake, and tells the Chat tab's owl the
 * same way they do. A set, not a toggle: a second tap before the eye goes can't hide them
 * again, and only the tap that showed them announces it.
 */
internal suspend fun showAmounts(preferences: PrivacyPreferences, mascotMoments: MascotMoments) {
    if (preferences.setBalanceHidden(false)) mascotMoments.announce(MascotMoment.AmountsShown)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RevealAmountsEntryPoint {
    fun preferences(): PrivacyPreferences

    fun mascotMoments(): MascotMoments

    @ApplicationScope
    fun scope(): CoroutineScope
}
