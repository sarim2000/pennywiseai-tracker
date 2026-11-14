package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.nameForBuiltin
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/** The categories a picker offers for [isIncome]: that type only, hidden ones left out (#736). */
fun List<CategoryEntity>.forPicker(isIncome: Boolean): List<CategoryEntity> =
    filter { it.isIncome == isIncome && !it.isHidden }

/**
 * The category a picker switches to when its type flips: the built-in
 * [builtin] under its current name (it may be renamed), or the first one
 * offered if that built-in is hidden or gone.
 */
fun List<CategoryEntity>.pickerDefault(builtin: String, isIncome: Boolean): String {
    val offered = forPicker(isIncome)
    val name = nameForBuiltin(builtin)
    return if (offered.any { it.name == name }) name else offered.firstOrNull()?.name ?: name
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
 * The rows of every category picker's dropdown (Add, Edit, manual subscription,
 * recurring): income categories for income and expense ones otherwise, each
 * with its emoji or colour dot, sub-categories indented under their parent
 * (#374). Shared so a picker can't drift back to "expense only, no emoji" (#876).
 *
 * A search field leads the list. With [mostUsed] history and no search, a
 * "Most used" section sits above "All" (#1058).
 *
 * @param categories Hierarchically ordered categories of any type.
 * @param onAddCategory When set, an "Add category" row leads the list, reachable
 *   without scrolling past sub-categories (#584, #835). While searching it sits
 *   below the matches as "Create '<query>'" and gets the query as the name.
 * @param mostUsed Category names by recent use, most first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryMenuItems(
    categories: List<CategoryEntity>,
    isIncome: Boolean,
    onSelected: (CategoryEntity) -> Unit,
    onAddCategory: ((name: String) -> Unit)? = null,
    mostUsed: List<String> = emptyList()
) {
    var query by rememberSaveable { mutableStateOf("") }
    val offered = categories.forPicker(isIncome)
    val labels = offered.associate { it.id to categoryLabel(it.name) }
    val shown = offered.matching(query) { labels[it.id] ?: it.name }
    val top = if (query.isBlank()) offered.mostUsed(mostUsed) else emptyList()
    val itemPadding = ExposedDropdownMenuDefaults.ItemContentPadding

    @Composable
    fun Item(category: CategoryEntity, indent: Boolean) = DropdownMenuItem(
        text = {
            CategoryChip(
                category = category,
                modifier = Modifier.padding(start = if (indent && category.parentId != null) Spacing.lg else Spacing.none)
            )
        },
        onClick = { onSelected(category) },
        contentPadding = itemPadding
    )

    @Composable
    fun CreateItem(label: String, name: String) = DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.medium)) },
        onClick = { onAddCategory?.invoke(name) },
        contentPadding = itemPadding
    )

    CategorySearchField(
        query = query,
        onQueryChange = { query = it },
        modifier = Modifier.padding(horizontal = Spacing.smd, vertical = Spacing.xs)
    )
    if (onAddCategory != null && query.isBlank()) {
        CreateItem(stringResource(R.string.txn_detail_add_category), "")
        HorizontalDivider()
    }
    if (top.isNotEmpty()) {
        CategoryPickerLabel(stringResource(R.string.category_picker_most_used), itemPadding)
        top.forEach { Item(it, indent = false) }
        CategoryPickerLabel(stringResource(R.string.category_picker_all), itemPadding)
    }
    // Matches are a flat list; the parent indent only reads in the full list.
    shown.forEach { Item(it, indent = query.isBlank()) }
    if (query.isNotBlank()) {
        if (onAddCategory != null) {
            if (shown.isNotEmpty()) HorizontalDivider()
            CreateItem(stringResource(R.string.category_picker_create, query.trim()), query.trim())
        } else if (shown.isEmpty()) {
            CategoryPickerLabel(stringResource(R.string.category_picker_no_match), itemPadding)
        }
    }
}

/** The pickers' name filter (#1058). */
@Composable
internal fun CategorySearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
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

/** A small section label ("Most used", "All") inside a picker. */
@Composable
internal fun CategoryPickerLabel(text: String, padding: PaddingValues) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(padding)
            .padding(vertical = Spacing.xs)
    )
}

/** A picker field's leading icon: the chosen category's emoji or dot, else a generic glyph. */
@Composable
fun SelectedCategoryIcon(category: CategoryEntity?) {
    if (category != null) {
        CategoryChip(category = category, showText = false, modifier = Modifier.padding(start = Spacing.smd))
    } else {
        Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.medium))
    }
}
