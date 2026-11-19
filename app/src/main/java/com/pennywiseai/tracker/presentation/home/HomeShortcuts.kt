package com.pennywiseai.tracker.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Flag
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
 * One tap from Home to the places behind it: budgets, bills, goals and accounts.
 * Settings is the avatar; the unsorted pile has its own row in the list.
 */
@Composable
fun HomeShortcuts(
    onBudgets: () -> Unit,
    onBills: () -> Unit,
    onGoals: () -> Unit,
    onAccounts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // Four equal columns, so the shapes line up with the cards above and below.
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Shortcut(
            label = stringResource(R.string.budgets_title),
            icon = Icons.Default.DonutLarge,
            shape = MaterialShapes.Clover4Leaf.toShape(),
            container = colors.primaryContainer,
            content = colors.onPrimaryContainer,
            onClick = onBudgets,
        )
        Shortcut(
            label = stringResource(R.string.plan_bills),
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            shape = MaterialShapes.Sunny.toShape(),
            container = colors.tertiaryContainer,
            content = colors.onTertiaryContainer,
            onClick = onBills,
        )
        Shortcut(
            label = stringResource(R.string.goals_section_title),
            icon = Icons.Default.Flag,
            shape = MaterialShapes.Cookie9Sided.toShape(),
            container = colors.surfaceContainerHighest,
            content = colors.onSurface,
            onClick = onGoals,
        )
        Shortcut(
            label = stringResource(R.string.home_shortcut_accounts),
            icon = Icons.Default.AccountBalance,
            shape = MaterialShapes.SoftBurst.toShape(),
            container = colors.secondaryContainer,
            content = colors.onSecondaryContainer,
            onClick = onAccounts,
        )
    }
}

@Composable
private fun RowScope.Shortcut(
    label: String,
    icon: ImageVector,
    shape: Shape,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
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
