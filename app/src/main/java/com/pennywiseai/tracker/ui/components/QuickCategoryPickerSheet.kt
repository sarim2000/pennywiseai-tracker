package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.ui.icons.categoryLabel
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
    onTagsChanged: ((List<String>) -> Unit)? = null,
    // Notification picker only: a "2 of 3" beside the title while walking the
    // recap's unsorted rows, and a "Not an expense" row that drops the
    // transaction from totals ([onExclude] persists and dismisses).
    progress: String? = null,
    // Notification picker only: which transaction this is ("Merchant · JD21"),
    // in place of the generic title — from the recap there's no alert beside it.
    subject: String? = null,
    excludeLabel: String? = null,
    onExclude: (() -> Unit)? = null,
    // Category names by recent use, most first: a "Most used" section above "All" (#1058).
    mostUsed: List<String> = emptyList(),
    // When set, an "Add category" row leads the list and, while searching, a
    // "Create '<query>'" row follows the matches. Gets the name to pre-fill (#1058).
    onAddCategory: ((name: String) -> Unit)? = null
) {
    var query by rememberSaveable { mutableStateOf("") }
    val labels = categories.associate { it.id to categoryLabel(it.name) }
    val shown = categories.matching(query) { labels[it.id] ?: it.name }
    val top = if (query.isBlank()) categories.mostUsed(mostUsed) else emptyList()
    val labelPadding = PaddingValues(horizontal = Dimensions.Padding.content)
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        if (onTagsChanged != null) {
            var tags by remember(initialTags) { mutableStateOf(initialTags) }
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Dimensions.Padding.content,
                    end = Dimensions.Padding.content,
                    bottom = Spacing.sm
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = subject ?: stringResource(R.string.quick_category_title),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (progress != null) {
                Text(
                    text = progress,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        CategorySearchField(
            query = query,
            onQueryChange = { query = it },
            modifier = Modifier.padding(start = Dimensions.Padding.content, end = Dimensions.Padding.content, bottom = Spacing.sm)
        )
        // LazyColumn so long category lists stay reachable when the sheet is
        // fully expanded — a plain Column would render items past the screen
        // bottom unscrollable.
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.md)
        ) {
            if (onExclude != null && excludeLabel != null) {
                item(key = "exclude") {
                    ActionRow(Icons.Outlined.VisibilityOff, excludeLabel, MaterialTheme.colorScheme.onSurfaceVariant, onExclude)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = Dimensions.Padding.content))
                }
            }
            // Reachable without scrolling past sub-categories (#584, #835).
            if (onAddCategory != null && query.isBlank()) {
                item(key = "add") {
                    ActionRow(Icons.Default.Add, stringResource(R.string.txn_detail_add_category), MaterialTheme.colorScheme.onSurface) { onAddCategory("") }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = Dimensions.Padding.content))
                }
            }
            if (top.isNotEmpty()) {
                item(key = "most-used") { CategoryPickerLabel(stringResource(R.string.category_picker_most_used), labelPadding) }
                items(top, key = { "top-${it.id}" }) { category ->
                    CategoryRow(category, currentCategory == category.name, indent = false, Modifier.animateItem()) { onCategorySelected(category.name) }
                }
                item(key = "all") { CategoryPickerLabel(stringResource(R.string.category_picker_all), labelPadding) }
            }
            if (shown.isEmpty() && onAddCategory == null) {
                item(key = "no-match") { CategoryPickerLabel(stringResource(R.string.category_picker_no_match), labelPadding) }
            }
            // Matches are a flat list; the parent indent only reads in the full list.
            items(shown, key = { it.id }) { category ->
                CategoryRow(category, currentCategory == category.name, indent = query.isBlank(), Modifier.animateItem()) { onCategorySelected(category.name) }
            }
            if (onAddCategory != null && query.isNotBlank()) {
                item(key = "create") {
                    val name = query.trim()
                    ActionRow(Icons.Default.Add, stringResource(R.string.category_picker_create, name), MaterialTheme.colorScheme.onSurface) { onAddCategory(name) }
                }
            }
        }
    }
}

/** The categories whose shown (maybe translated) name or stored name contains [query] (#1058). */
fun List<CategoryEntity>.matching(query: String, label: (CategoryEntity) -> String): List<CategoryEntity> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { label(it).contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true) }
}

/** Up to [limit] of these categories, in the order of [usage] (names, most used first) (#1058). */
fun List<CategoryEntity>.mostUsed(usage: List<String>, limit: Int = 8): List<CategoryEntity> {
    val byName = associateBy { it.name }
    return usage.mapNotNull { byName[it] }.take(limit)
}

/**
 * The picker's name filter. Lives in this sheet, not the dropdown menus: an
 * ExposedDropdownMenu popup sets FLAG_ALT_FOCUSABLE_IM, so a field in it never
 * gets the keyboard. Not auto-focused, so the keyboard doesn't cover the list.
 */
@Composable
private fun CategorySearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.category_picker_search)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** A small section label ("Most used", "All") inside the picker. */
@Composable
private fun CategoryPickerLabel(text: String, padding: PaddingValues) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(padding)
            .padding(vertical = Spacing.xs)
    )
}

@Composable
private fun ActionRow(icon: ImageVector, text: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color)
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
private fun CategoryRow(category: CategoryEntity, selected: Boolean, indent: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                // Sub-categories indent under their parent (#374)
                start = Dimensions.Padding.content + (if (indent && category.parentId != null) Spacing.lg else Spacing.none),
                end = Dimensions.Padding.content,
                top = Spacing.sm,
                bottom = Spacing.sm
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        CategoryChip(category = category, showText = false)
        Text(
            text = categoryLabel(category.name),
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
    onDismiss: () -> Unit,
    mostUsed: List<String> = emptyList()
) = QuickCategoryPickerSheet(
    currentCategory = transaction.category,
    categories = categories,
    onCategorySelected = onCategorySelected,
    onDismiss = onDismiss,
    mostUsed = mostUsed
)
