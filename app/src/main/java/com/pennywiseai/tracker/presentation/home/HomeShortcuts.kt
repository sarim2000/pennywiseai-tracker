package com.pennywiseai.tracker.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * One tap from Home to what used to hide behind menus: budgets, bills, accounts, and
 * the unsorted pile when there is one (goals otherwise). Settings is the avatar.
 */
@Composable
fun HomeShortcuts(
    unsortedCount: Int,
    onBudgets: () -> Unit,
    onBills: () -> Unit,
    onAccounts: () -> Unit,
    onUnsorted: () -> Unit,
    onGoals: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Shortcut(
            label = stringResource(R.string.budgets_title),
            icon = Icons.Default.DonutLarge,
            shape = MaterialShapes.Cookie9Sided.toShape(),
            container = colors.primaryContainer,
            content = colors.onPrimaryContainer,
            onClick = onBudgets,
        )
        Shortcut(
            label = stringResource(R.string.plan_bills),
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            shape = MaterialShapes.Clover4Leaf.toShape(),
            container = colors.tertiaryContainer,
            content = colors.onTertiaryContainer,
            onClick = onBills,
        )
        Shortcut(
            label = stringResource(R.string.home_shortcut_accounts),
            icon = Icons.Default.AccountBalance,
            shape = MaterialShapes.Sunny.toShape(),
            container = colors.secondaryContainer,
            content = colors.onSecondaryContainer,
            onClick = onAccounts,
        )
        if (unsortedCount > 0) {
            Shortcut(
                label = stringResource(R.string.home_shortcut_unsorted),
                icon = Icons.Default.Sell,
                shape = MaterialShapes.SoftBurst.toShape(),
                container = colors.surfaceContainerHighest,
                content = colors.onSurface,
                onClick = onUnsorted,
                badge = unsortedCount,
            )
        } else {
            Shortcut(
                label = stringResource(R.string.goals_section_title),
                icon = Icons.Default.Flag,
                shape = MaterialShapes.SoftBurst.toShape(),
                container = colors.surfaceContainerHighest,
                content = colors.onSurface,
                onClick = onGoals,
            )
        }
    }
}

@Composable
private fun Shortcut(
    label: String,
    icon: ImageVector,
    shape: Shape,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    badge: Int? = null,
) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        BadgedBox(
            badge = {
                if (badge != null) Badge { Text(if (badge > MAX_BADGE) "$MAX_BADGE+" else badge.toString()) }
            }
        ) {
            Box(
                modifier = Modifier
                    .size(Dimensions.Component.shortcutTile)
                    .clip(shape)
                    .background(container),
                contentAlignment = Alignment.Center,
            ) {
                // The label below names it.
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(Dimensions.Icon.medium))
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val MAX_BADGE = 99
