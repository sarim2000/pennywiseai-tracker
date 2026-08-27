package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * Modal bottom-sheet picker for changing a transaction's category. Used both
 * from the transactions list (long-press / overflow) and from the txn-alert
 * notification's "More categories…" action (#303). Shared so the in-app and
 * notification flows present identical UI.
 *
 * @param currentCategory The transaction's current category name — gets a
 *  check-mark and bold weight so the user sees what they're overriding.
 * @param categories Flat, already-ordered list of selectable categories.
 * @param onCategorySelected Fired with the chosen category name; caller is
 *  responsible for persisting and dismissing.
 * @param onDismiss Called when the user dismisses the sheet without picking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCategoryPickerSheet(
    currentCategory: String,
    categories: List<CategoryEntity>,
    onCategorySelected: (String) -> Unit,
    onDismiss: () -> Unit,
    // Optional inline tagging (notification "More…" flow, #696). When
    // [onTagsChanged] is non-null a Tags section is shown above the category
    // list; each add/remove commits immediately, so the user can tag without
    // also picking a category. In-app call sites omit these and see no change.
    tagSuggestions: List<String> = emptyList(),
    initialTags: List<String> = emptyList(),
    onTagsChanged: ((List<String>) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        if (onTagsChanged != null) {
            var tags by remember { mutableStateOf(initialTags) }
            TagInputField(
                selectedTags = tags,
                allTags = tagSuggestions,
                onAddTag = { name ->
                    if (name !in tags) {
                        tags = tags + name
                        onTagsChanged(tags)
                    }
                },
                onRemoveTag = { name ->
                    tags = tags - name
                    onTagsChanged(tags)
                },
                modifier = Modifier.padding(
                    start = Dimensions.Padding.content,
                    end = Dimensions.Padding.content,
                    bottom = Spacing.sm
                )
            )
            HorizontalDivider(
                modifier = Modifier.padding(
                    horizontal = Dimensions.Padding.content,
                    vertical = Spacing.xs
                )
            )
        }
        Text(
            text = "Change category",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                bottom = Spacing.sm
            )
        )
        // LazyColumn so long category lists stay reachable when the sheet is
        // fully expanded — a plain Column would render items past the screen
        // bottom unscrollable.
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.md)
        ) {
            items(categories, key = { it.id }) { category ->
                val selected = currentCategory == category.name
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCategorySelected(category.name) }
                        .padding(
                            horizontal = Dimensions.Padding.content,
                            vertical = Spacing.sm
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    CategoryChip(category = category, showText = false)
                    Text(
                        text = category.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Convenience overload for call sites that already have a TransactionEntity
 * in hand (e.g. TransactionsScreen). Delegates to the form above using the
 * transaction's current category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCategoryPickerSheet(
    transaction: TransactionEntity,
    categories: List<CategoryEntity>,
    onCategorySelected: (String) -> Unit,
    onDismiss: () -> Unit
) = QuickCategoryPickerSheet(
    currentCategory = transaction.category,
    categories = categories,
    onCategorySelected = onCategorySelected,
    onDismiss = onDismiss
)
