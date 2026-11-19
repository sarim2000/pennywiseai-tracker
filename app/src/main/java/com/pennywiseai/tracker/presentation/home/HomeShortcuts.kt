package com.pennywiseai.tracker.presentation.home

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.graphics.shapes.RoundedPolygon
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.preferences.HomeShortcut
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * Home's shortcut row: the user's picks (Edit home), each a shaped tile with its
 * label under it. Unsorted carries its count and is left out while there's nothing
 * to sort. A long press anywhere on the row opens Edit home, to change them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeShortcuts(
    shortcuts: List<HomeShortcut>,
    unsortedCount: Int,
    onOpen: (HomeShortcut) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // Always four columns, so the tiles keep their size and place with fewer picks.
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        shortcuts.forEach { shortcut ->
            val look = shortcut.look(colors)
            val label = stringResource(shortcut.labelRes())
            val badge = unsortedCount.takeIf { shortcut == HomeShortcut.UNSORTED && it > 0 }
            Shortcut(
                label = label,
                description = badge?.let { pluralStringResource(R.plurals.home_shortcut_unsorted_badge, it, it) } ?: label,
                icon = look.icon,
                shape = look.shape,
                container = look.container,
                content = look.content,
                badge = badge,
                onClick = { onOpen(shortcut) },
                onLongClick = onEdit,
            )
        }
        repeat(HOME_SHORTCUT_COLUMNS - shortcuts.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** Columns in the row; [com.pennywiseai.tracker.data.preferences.HomeShortcutLayout.MAX] fills them. */
private const val HOME_SHORTCUT_COLUMNS = 4

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.Shortcut(
    label: String,
    description: String,
    icon: ImageVector,
    shape: RoundedPolygon,
    container: Color,
    content: Color,
    badge: Int?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        BadgedBox(
            badge = {
                if (badge != null) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Text(if (badge > 99) "99+" else badge.toString())
                    }
                }
            },
        ) {
            Box(
                modifier = Modifier
                    .size(Dimensions.Component.shortcutTile)
                    .clip(shape.toShape())
                    .background(container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(Dimensions.Icon.medium))
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            // Two lines, so a large font or a long translation wraps instead of cutting off.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A shortcut's glyph, silhouette and fill; the four defaults each get their own. */
private class ShortcutLook(val icon: ImageVector, val shape: RoundedPolygon, val container: Color, val content: Color)

private fun HomeShortcut.look(c: ColorScheme): ShortcutLook = when (this) {
    HomeShortcut.UNSORTED -> ShortcutLook(Icons.Rounded.Sell, MaterialShapes.Cookie9Sided, c.surfaceContainerHighest, c.onSurface)
    HomeShortcut.SEARCH -> ShortcutLook(Icons.Rounded.Search, MaterialShapes.Cookie4Sided, c.primaryContainer, c.onPrimaryContainer)
    HomeShortcut.BILLS -> ShortcutLook(Icons.AutoMirrored.Rounded.ReceiptLong, MaterialShapes.Sunny, c.tertiaryContainer, c.onTertiaryContainer)
    HomeShortcut.CALENDAR -> ShortcutLook(Icons.Rounded.CalendarMonth, MaterialShapes.Cookie12Sided, c.secondaryContainer, c.onSecondaryContainer)
    HomeShortcut.ACCOUNTS -> ShortcutLook(Icons.Rounded.AccountBalance, MaterialShapes.SoftBurst, c.secondaryContainer, c.onSecondaryContainer)
    HomeShortcut.BUDGETS -> ShortcutLook(Icons.Rounded.DonutLarge, MaterialShapes.Clover4Leaf, c.primaryContainer, c.onPrimaryContainer)
    HomeShortcut.GOALS -> ShortcutLook(Icons.Rounded.Flag, MaterialShapes.Flower, c.tertiaryContainer, c.onTertiaryContainer)
    HomeShortcut.REVIEW -> ShortcutLook(Icons.Rounded.Insights, MaterialShapes.Gem, c.primaryContainer, c.onPrimaryContainer)
    HomeShortcut.RECURRING -> ShortcutLook(Icons.Rounded.Repeat, MaterialShapes.Pentagon, c.secondaryContainer, c.onSecondaryContainer)
    HomeShortcut.CHECKUP -> ShortcutLook(Icons.Rounded.MarkEmailRead, MaterialShapes.Clover8Leaf, c.surfaceContainerHighest, c.onSurface)
}

@StringRes
internal fun HomeShortcut.labelRes(): Int = when (this) {
    HomeShortcut.UNSORTED -> R.string.home_shortcut_unsorted
    HomeShortcut.SEARCH -> R.string.txn_list_search
    HomeShortcut.BILLS -> R.string.plan_bills
    HomeShortcut.CALENDAR -> R.string.home_shortcut_calendar
    HomeShortcut.ACCOUNTS -> R.string.manage_accounts_title
    HomeShortcut.BUDGETS -> R.string.budgets_title
    HomeShortcut.GOALS -> R.string.goals_section_title
    HomeShortcut.REVIEW -> R.string.review_title
    HomeShortcut.RECURRING -> R.string.recurring_title
    HomeShortcut.CHECKUP -> R.string.home_shortcut_checkup
}
