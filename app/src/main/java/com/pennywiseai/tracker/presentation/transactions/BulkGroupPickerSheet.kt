package com.pennywiseai.tracker.presentation.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.ui.theme.*

/**
 * Bulk "Add to group" picker (#506). Lists existing groups (tap to add all
 * selected transactions) and a "Create new group" affordance. Unlike the
 * single-transaction [GroupBottomSheet], there is no "current group" — the
 * selection can span rows in different groups, so adding simply moves them all
 * to the chosen group.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BulkGroupPickerSheet(
    selectedCount: Int,
    groups: List<TransactionGroupEntity>,
    onGroupSelected: (TransactionGroupEntity) -> Unit,
    onCreateGroup: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreateField by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Dimensions.Padding.content)
        ) {
            Text(
                text = stringResource(R.string.txn_list_bulk_group_title, selectedCount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = Spacing.md)
            )

            if (groups.isNotEmpty()) {
                // Cap the list height and let it scroll so a long group list
                // doesn't push "Create new group" off the bottom of the sheet.
                Column(
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    groups.forEach { group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onGroupSelected(group) }
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(Dimensions.Icon.medium)
                            )
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            if (showCreateField) {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text(stringResource(R.string.txn_list_group_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (newGroupName.isNotBlank()) {
                                onCreateGroup(newGroupName.trim())
                            }
                        }
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (newGroupName.isNotBlank()) {
                                    onCreateGroup(newGroupName.trim())
                                }
                            },
                            enabled = newGroupName.isNotBlank(),
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.txn_list_group_create))
                        }
                    }
                )
            } else {
                TextButton(
                    onClick = { showCreateField = true },
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.txn_list_group_create_new))
                }
            }
        }
    }
}
