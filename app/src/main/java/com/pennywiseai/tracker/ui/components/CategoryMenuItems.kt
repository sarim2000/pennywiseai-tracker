package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.nameForBuiltin
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

/**
 * The rows of every category picker's dropdown (Add, Edit, manual subscription,
 * recurring): income categories for income and expense ones otherwise, each
 * with its emoji or colour dot, sub-categories indented under their parent
 * (#374). Shared so a picker can't drift back to "expense only, no emoji" (#876).
 *
 * @param categories Hierarchically ordered categories of any type.
 * @param onAddCategory When set, an "Add category" row leads the list, reachable
 *   without scrolling past sub-categories (#584, #835).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryMenuItems(
    categories: List<CategoryEntity>,
    isIncome: Boolean,
    onSelected: (CategoryEntity) -> Unit,
    onAddCategory: (() -> Unit)? = null
) {
    if (onAddCategory != null) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.txn_detail_add_category)) },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.medium)) },
            onClick = onAddCategory,
            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
        )
        HorizontalDivider()
    }
    categories.forPicker(isIncome).forEach { category ->
        DropdownMenuItem(
            text = {
                CategoryChip(
                    category = category,
                    modifier = Modifier.padding(start = if (category.parentId != null) Spacing.lg else Spacing.none)
                )
            },
            onClick = { onSelected(category) },
            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
        )
    }
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
