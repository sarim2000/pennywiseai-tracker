package com.pennywiseai.tracker.presentation.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.achievements.Achievement
import com.pennywiseai.tracker.ui.components.AchievementBadge
import com.pennywiseai.tracker.ui.components.ErrorState
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.achievementDescription
import com.pennywiseai.tracker.ui.components.achievementName
import com.pennywiseai.tracker.ui.components.badgeArt
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.progressLabel
import com.pennywiseai.tracker.ui.components.tierLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.AppDates

/**
 * Every achievement: the earned ones with when, the rest with how close. Each earned one
 * opens the share studio on itself; "Share all" opens it on the whole set, where the
 * user can still leave some off.
 */
@Composable
fun AchievementsScreen(
    onNavigateBack: () -> Unit,
    onShare: (key: String?) -> Unit,
    viewModel: AchievementsViewModel = hiltViewModel(),
) {
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    val result = achievements
    val list = result?.getOrNull()
    val earned = list.orEmpty().filter { it.earned }
    val locked = list.orEmpty().filterNot { it.earned }

    PennyWiseScaffold(
        title = stringResource(R.string.achievements_title),
        navigationIcon = {
            IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.achievements_back))
            }
        },
        bottomBar = {
            if (earned.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Button(
                        onClick = { onShare(null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.smd),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Text(stringResource(R.string.achievements_share_all), modifier = Modifier.padding(start = Spacing.sm))
                    }
                }
            }
        },
    ) { padding ->
        if (result?.isFailure == true) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                ErrorState(message = stringResource(R.string.achievements_load_failed), onRetry = viewModel::retry)
            }
            return@PennyWiseScaffold
        }
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@PennyWiseScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                bottom = Spacing.Layout.scrollBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent),
        ) {
            item { Summary(list) }
            if (earned.isNotEmpty()) {
                item { SectionHeaderV2(title = stringResource(R.string.achievements_section_earned)) }
                item {
                    GroupedList {
                        earned.forEachIndexed { i, a ->
                            AchievementRow(a, ListItemPosition.from(i, earned.size), onShare = { onShare(a.id.key) })
                        }
                    }
                }
            }
            if (locked.isNotEmpty()) {
                item { SectionHeaderV2(title = stringResource(R.string.achievements_section_locked)) }
                item {
                    GroupedList {
                        locked.forEachIndexed { i, a -> AchievementRow(a, ListItemPosition.from(i, locked.size), onShare = null) }
                    }
                }
            }
        }
    }
}

/** The whole collection at a glance: every badge, earned in colour, the rest as rings. */
@Composable
private fun Summary(all: List<Achievement>) {
    val earned = all.count { it.earned }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.smd),
        ) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                all.sortedBy { it.id.ordinal }.forEach { a -> Badge(a, Dimensions.Icon.list) }
            }
            Text(
                text = stringResource(R.string.achievements_summary, earned, all.size),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            LinearProgressIndicator(
                progress = { if (all.isEmpty()) 0f else earned.toFloat() / all.size },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.achievements_summary_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Badge(a: Achievement, size: Dp) {
    AchievementBadge(
        id = a.id,
        tier = a.tier,
        size = size,
        progress = a.fractionToNext,
        lockedColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        lockedContent = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AchievementRow(a: Achievement, position: ListItemPosition, onShare: (() -> Unit)?) {
    val name = achievementName(a.id)
    GroupedRow(position = position, horizontalArrangement = Arrangement.spacedBy(Spacing.smd)) {
        Badge(a, Dimensions.Icon.avatarLarge)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = achievementDescription(a),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            earnedLine(a)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            progressLabel(a)?.let { label ->
                Row(
                    Modifier.padding(top = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    LinearProgressIndicator(
                        progress = { a.fractionToNext },
                        modifier = Modifier
                            .weight(1f)
                            .height(Spacing.xs)
                            .clip(CircleShape),
                        color = badgeArt(a.id).hue,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        drawStopIndicator = {},
                    )
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (onShare != null) {
            IconButton(onClick = onShare, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.achievements_share_one, name))
            }
        }
    }
}

/** "Tier 2 of 4 · Earned 9 Nov 2026"; null while locked. The bar under it says what's next. */
@Composable
private fun earnedLine(a: Achievement): String? {
    if (!a.earned) return null
    val earnedOn = a.unlockedOn?.let { stringResource(R.string.achievements_earned_on, it.format(AppDates.pattern("d MMM yyyy"))) }
    return listOfNotNull(tierLabel(a), earnedOn).joinToString(SEPARATOR).ifEmpty { null }
}

private const val SEPARATOR = " · "
