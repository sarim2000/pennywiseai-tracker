package com.pennywiseai.tracker.ui.components

import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * Reusable expandable list component
 * Shows limited items initially with "View All" button
 */
@Composable
fun <T> ExpandableList(
    items: List<T>,
    visibleItemCount: Int = 5,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            items.take(visibleItemCount).forEach { item ->
                itemContent(item)
            }
        }

        // The rest opens below the visible items. Each gap rides inside the animated
        // block (top padding) so nothing snaps open before the rows grow in.
        AnimatedVisibility(visible = isExpanded, enter = pwExpand(), exit = pwCollapse()) {
            Column {
                items.drop(visibleItemCount).forEach { item ->
                    Box(Modifier.padding(top = Spacing.md)) { itemContent(item) }
                }
            }
        }

        // View All / View Less button
        if (items.size > visibleItemCount) {
            TextButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                shapes = ButtonDefaults.shapes()
            ) {
                Text(
                    text = if (isExpanded) {
                        stringResource(R.string.expandable_list_view_less)
                    } else {
                        stringResource(R.string.expandable_list_view_all_more, items.size - visibleItemCount)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}