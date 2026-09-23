package com.pennywiseai.tracker.presentation.categories

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.ui.components.CategoryChip
import com.pennywiseai.tracker.ui.components.ColorSwatchRow
import com.pennywiseai.tracker.ui.components.EmojiGlyph
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryEditDialog(
    category: CategoryEntity? = null,
    defaultIsIncome: Boolean = false,
    lockType: Boolean = false,
    // Candidate parents for the "Parent category" picker (#374); empty hides it.
    parentOptions: List<CategoryEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (name: String, color: String, isIncome: Boolean, icon: String?, parentId: Long?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var isIncome by remember { mutableStateOf(category?.isIncome ?: defaultIsIncome) }
    var parentId by remember { mutableStateOf(category?.parentId) }
    // Only top-level categories of the same type can be parents (one level deep).
    val parentCandidates = parentOptions.filter { it.parentId == null && it.isIncome == isIncome && it.id != category?.id }
    // A category with children can't itself become a child.
    val hasChildren = category != null && parentOptions.any { it.parentId == category.id }
    var nameError by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(category?.color ?: "#4CAF50") }
    var emoji by remember { mutableStateOf(category?.icon ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        PennyWiseCardV2(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            shape = RoundedCornerShape(28.dp),
            contentPadding = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Dimensions.Padding.card),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Title
                Text(
                    text = stringResource(if (category == null) R.string.category_edit_title_add else R.string.category_edit_title_edit),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                // Category Name Input
                TextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = it.isBlank()
                    },
                    label = { Text(stringResource(R.string.category_edit_name_label), fontWeight = FontWeight.SemiBold) },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.category_edit_name_required)) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                // Category Type Selection — hidden when the type is dictated by
                // context (e.g. adding a category from a transaction edit, where a
                // mismatched type would be filtered out of the picker anyway).
                if (!lockType) {
                    Column {
                        Text(
                            text = stringResource(R.string.category_edit_type),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            // A parent's type is fixed while it has children (#374): they'd
                            // otherwise end up in the other section without it.
                            FilterChip(
                                enabled = !hasChildren,
                                selected = !isIncome,
                                onClick = { isIncome = false; parentId = null },
                                label = { Text(stringResource(R.string.category_edit_type_expense)) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                enabled = !hasChildren,
                                selected = isIncome,
                                onClick = { isIncome = true; parentId = null },
                                label = { Text(stringResource(R.string.category_edit_type_income)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Parent category (#374) — optional, one level deep.
                if (parentCandidates.isNotEmpty() && !hasChildren) {
                    var parentMenu by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = parentMenu, onExpandedChange = { parentMenu = it }) {
                        TextField(
                            value = parentCandidates.firstOrNull { it.id == parentId }?.name ?: stringResource(R.string.category_edit_parent_none),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.category_edit_parent_label), fontWeight = FontWeight.SemiBold) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = parentMenu) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            shape = MaterialTheme.shapes.large,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        ExposedDropdownMenu(expanded = parentMenu, onDismissRequest = { parentMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.category_edit_parent_none)) }, onClick = { parentId = null; parentMenu = false })
                            parentCandidates.forEach { p ->
                                DropdownMenuItem(
                                    text = { CategoryChip(category = p) },
                                    onClick = { parentId = p.id; parentMenu = false }
                                )
                            }
                        }
                    }
                }

                // Icon: one emoji from the keyboard's own picker (#760). Empty = default icon.
                TextField(
                    value = emoji,
                    onValueChange = { emoji = lastEmoji(it) ?: emoji },
                    label = { Text(stringResource(R.string.category_edit_icon_label), fontWeight = FontWeight.SemiBold) },
                    placeholder = { Text(stringResource(R.string.category_edit_icon_placeholder)) },
                    singleLine = true,
                    trailingIcon = if (emoji.isNotEmpty()) {
                        {
                            IconButton(onClick = { emoji = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.category_edit_clear_icon))
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                // Color Selection
                Column {
                    Text(
                        text = stringResource(R.string.category_edit_color),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))

                    ColorSwatchRow(selected = selectedColor, onSelect = { selectedColor = it })
                }

                // Preview
                PennyWiseCardV2(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    contentPadding = Dimensions.Padding.content
                ) {
                    Text(
                        text = stringResource(R.string.category_edit_preview),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        // Show selected color (+ emoji if set)
                        Box(
                            modifier = Modifier
                                .size(Dimensions.Icon.medium)
                                .clip(CircleShape)
                                .background(
                                    try { Color(android.graphics.Color.parseColor(selectedColor)) }
                                    catch (e: Exception) { MaterialTheme.colorScheme.primary }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (emoji.isNotEmpty()) EmojiGlyph(emoji, Dimensions.Icon.small)
                        }
                        Text(
                            text = name.ifBlank { stringResource(R.string.category_edit_name_label) },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.categories_action_cancel))
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(name.trim(), selectedColor, isIncome, emoji.ifBlank { null }, parentId)
                            } else {
                                nameError = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = name.isNotBlank()
                    ) {
                        Text(stringResource(if (category == null) R.string.category_edit_add else R.string.category_edit_save))
                    }
                }

                // Discoverable delete for custom categories (the swipe-to-delete
                // gesture stays as a shortcut). System categories never show this.
                if (category != null && !category.isSystem && onDelete != null) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.category_edit_delete))
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && category != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.category_edit_delete_title)) },
            text = {
                Text(stringResource(R.string.category_edit_delete_message, category.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete?.invoke()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.categories_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.categories_action_cancel)) }
            }
        )
    }
}

/**
 * The last user-perceived character of [text], or null if it isn't an emoji.
 * A multi-codepoint emoji survives; a second one replaces it; plain letters
 * and punctuation are rejected so the field can't save "c" as an icon.
 */
private fun lastEmoji(text: String): String? {
    if (text.isEmpty()) return ""
    val it = java.text.BreakIterator.getCharacterInstance()
    it.setText(text)
    val end = it.last()
    val start = it.previous()
    val grapheme = if (start < 0) text else text.substring(start, end)
    // ponytail: "anything in the symbol/emoji planes" — no full emoji table.
    return grapheme.takeIf { g -> g.codePoints().anyMatch { cp -> cp >= 0x2600 } }
}
