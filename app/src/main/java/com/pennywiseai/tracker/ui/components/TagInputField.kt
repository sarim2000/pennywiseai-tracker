package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * Free-text tag picker. Shows the currently selected tags as removable chips,
 * a text field for typing, and live suggestions drawn from existing tags. The
 * typed value can either match an existing tag (select) or be committed as a
 * brand-new tag (create) via the IME action, the add icon, or the "Create"
 * suggestion chip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagInputField(
    selectedTags: List<String>,
    allTags: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Tags (Optional)"
) {
    var input by remember { mutableStateOf("") }

    fun commit(raw: String) {
        val name = raw.trim()
        if (name.isEmpty()) return
        if (selectedTags.none { it.equals(name, ignoreCase = true) }) {
            onAddTag(name)
        }
        input = ""
    }

    val trimmed = input.trim()
    val suggestions = remember(input, allTags, selectedTags) {
        if (trimmed.isEmpty()) {
            emptyList()
        } else {
            allTags.filter { tag ->
                tag.contains(trimmed, ignoreCase = true) &&
                    selectedTags.none { it.equals(tag, ignoreCase = true) }
            }.take(5)
        }
    }
    val showCreateOption = trimmed.isNotEmpty() &&
        allTags.none { it.equals(trimmed, ignoreCase = true) } &&
        selectedTags.none { it.equals(trimmed, ignoreCase = true) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        if (selectedTags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                selectedTags.forEach { tag ->
                    InputChip(
                        selected = true,
                        onClick = { onRemoveTag(tag) },
                        label = {
                            Text(
                                tag,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove $tag",
                                modifier = Modifier.size(Dimensions.Icon.small)
                            )
                        }
                    )
                }
            }
        }

        TextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(label) },
            singleLine = true,
            leadingIcon = {
                Icon(
                    Icons.Default.Sell,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.Icon.medium)
                )
            },
            trailingIcon = {
                if (trimmed.isNotEmpty()) {
                    IconButton(onClick = { commit(input) }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add tag",
                            modifier = Modifier.size(Dimensions.Icon.medium)
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit(input) }),
            modifier = Modifier.fillMaxWidth()
        )

        if (suggestions.isNotEmpty() || showCreateOption) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                suggestions.forEach { suggestion ->
                    AssistChip(
                        onClick = { commit(suggestion) },
                        label = {
                            Text(
                                suggestion,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
                if (showCreateOption) {
                    AssistChip(
                        onClick = { commit(trimmed) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.Icon.small)
                            )
                        },
                        label = {
                            Text(
                                "Create \"$trimmed\"",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }
    }
}
