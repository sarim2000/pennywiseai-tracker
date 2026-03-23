package com.pennywiseai.tracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.pennywiseai.tracker.data.preferences.BusinessFilter

@Composable
fun BusinessFilterDropdown(
    expanded: Boolean,
    selectedFilter: BusinessFilter,
    onFilterSelected: (BusinessFilter) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        BusinessFilter.entries.forEach { filter ->
            DropdownMenuItem(
                text = {
                    Text(
                        when (filter) {
                            BusinessFilter.ALL -> "All Accounts"
                            BusinessFilter.PERSONAL -> "Personal"
                            BusinessFilter.BUSINESS -> "Business"
                        }
                    )
                },
                onClick = {
                    onFilterSelected(filter)
                    onDismiss()
                },
                leadingIcon = {
                    Icon(
                        imageVector = businessFilterIcon(filter),
                        contentDescription = null
                    )
                },
                trailingIcon = if (selectedFilter == filter) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else null
            )
        }
    }
}

fun businessFilterIcon(filter: BusinessFilter) = when (filter) {
    BusinessFilter.ALL -> Icons.Outlined.AccountBalance
    BusinessFilter.PERSONAL -> Icons.Outlined.Person
    BusinessFilter.BUSINESS -> Icons.Outlined.Business
}
