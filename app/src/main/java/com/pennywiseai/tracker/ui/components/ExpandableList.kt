package com.pennywiseai.tracker.ui.components

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
    val displayItems = if (isExpanded) items else items.take(visibleItemCount)
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Display items
        displayItems.forEach { item ->
            itemContent(item)
        }
        
        // View All / View Less button
        if (items.size > visibleItemCount) {
            TextButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.fillMaxWidth()
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