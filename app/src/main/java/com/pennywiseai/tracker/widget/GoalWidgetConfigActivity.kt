package com.pennywiseai.tracker.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.ComponentName
import androidx.compose.material3.Button
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.repository.AppLockRepository
import com.pennywiseai.tracker.domain.security.BiometricAuthManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GoalRepository
import com.pennywiseai.tracker.presentation.goals.GoalRing
import com.pennywiseai.tracker.presentation.goals.statusLine
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseTheme
import com.pennywiseai.tracker.ui.theme.Spacing
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Picks the goal a 2×2 [GoalWidget] shows: launched by the launcher when the
 * widget is added (or reconfigured), and by the widget's "Goal removed" state.
 */
@AndroidEntryPoint
class GoalWidgetConfigActivity : FragmentActivity() {

    @Inject lateinit var goalRepository: GoalRepository
    @Inject lateinit var appLockRepository: AppLockRepository
    @Inject lateinit var biometricAuthManager: BiometricAuthManager

    /** Null while the app lock is checked; goals are read and shown only once true. */
    private var unlocked by mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out cancels: the launcher then drops a widget it was adding.
        setResult(RESULT_CANCELED, resultIntent(appWidgetId))
        // Exported for the launcher, so only configure an actual goal widget of ours.
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider
        if (provider != ComponentName(this, GoalWidgetReceiver::class.java)) { finish(); return }

        // Goal names are private: behind the app lock, same as opening the app.
        if (savedInstanceState?.getBoolean(KEY_UNLOCKED) == true) {
            unlocked = true
        } else {
            lifecycleScope.launch {
                unlocked = !appLockRepository.shouldLockApp()
                if (unlocked == false && savedInstanceState == null) promptUnlock()
            }
        }

        enableEdgeToEdge()
        setContent {
            PennyWiseTheme {
                PennyWiseScaffold(
                    title = stringResource(R.string.widget_goal_config_title),
                    navigationIcon = {
                        IconButton(onClick = ::finish) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.widget_goal_config_close))
                        }
                    },
                ) { padding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(Dimensions.Padding.content),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        when (unlocked) {
                            null -> Unit
                            false -> Locked(onUnlock = ::promptUnlock)
                            true -> {
                                val goals by goalRepository.observeActiveGoals().collectAsStateWithLifecycle(initialValue = null)
                                val list = goals ?: return@Column
                                if (list.isEmpty()) {
                                    Text(
                                        stringResource(R.string.goal_add_to_goal_no_goals),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    GoalChoices(list) { choose(appWidgetId, it) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, unlocked == true)
    }

    private fun promptUnlock() {
        biometricAuthManager.authenticate(
            activity = this,
            onSuccess = {
                lifecycleScope.launch { appLockRepository.updateAuthTimestamp() }
                unlocked = true
            },
            onError = {},
        )
    }

    private fun choose(appWidgetId: Int, goalId: Long) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@GoalWidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@GoalWidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply { this[GOAL_WIDGET_GOAL_ID] = goalId }
            }
            GoalWidget().update(this@GoalWidgetConfigActivity, glanceId)
            GoalWidgetUpdateWorker.enqueueOneShot(this@GoalWidgetConfigActivity)
            setResult(RESULT_OK, resultIntent(appWidgetId))
            finish()
        }
    }

    private fun resultIntent(appWidgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    private companion object {
        const val KEY_UNLOCKED = "unlocked"
    }
}

@Composable
private fun Locked(onUnlock: () -> Unit) {
    Text(stringResource(R.string.applock_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.applock_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onUnlock) { Text(stringResource(R.string.applock_unlock)) }
}

@Composable
private fun GoalChoices(goals: List<GoalProgress>, onChoose: (Long) -> Unit) {
    val context = LocalContext.current
    GroupedList {
        goals.forEachIndexed { i, progress ->
            GroupedRow(position = ListItemPosition.from(i, goals.size), onClick = { onChoose(progress.goal.id) }) {
                GoalRing(progress, Dimensions.Component.goalRingSmall, Dimensions.Component.goalRingSmallStroke) {}
                RowLabels(title = progress.goal.name, subtitle = progress.statusLine(context))
            }
        }
    }
}
