package com.pennywiseai.tracker.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pennywiseai.feature.learnedcategories.R
import com.pennywiseai.tracker.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The merchant → category pairs PennyWise learned from the user's own edits
 * (#1102). Smart Rules only lists rules the user wrote, so these were
 * invisible; removing one stops future transactions from that merchant getting
 * the category. Past transactions keep the category they have.
 */
@HiltViewModel
class LearnedCategoriesViewModel @Inject constructor(
    private val data: LearnedCategoriesData
) : ViewModel() {
    val mappings: StateFlow<List<LearnedCategory>?> = data.observeLearnedCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun remove(merchantName: String) {
        viewModelScope.launch { data.removeLearnedCategory(merchantName) }
    }
}

/**
 * The app's shared pieces come in as parameters, so this module needs nothing from
 * :app. `navigation/LearnedCategoriesEntry.kt` in :app supplies them.
 *
 * @param scaffold the app's scaffold, given the title and the content. It owns the
 *   top bar and the back button.
 * @param groupedRow one row of the app's grouped list, given the row's index, the
 *   list size, a modifier for the row and its content.
 * @param categoryLabel a category name as the user should read it.
 */
@Composable
fun LearnedCategoriesScreen(
    scaffold: @Composable (title: String, content: @Composable (PaddingValues) -> Unit) -> Unit,
    groupedRow: @Composable (
        index: Int,
        size: Int,
        modifier: Modifier,
        content: @Composable RowScope.() -> Unit,
    ) -> Unit,
    categoryLabel: @Composable (category: String) -> String,
    viewModel: LearnedCategoriesViewModel = hiltViewModel()
) {
    val mappings by viewModel.mappings.collectAsStateWithLifecycle()
    scaffold(stringResource(R.string.learned_categories_title)) { innerPadding ->
        val list = mappings ?: return@scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.Layout.groupedListGap)
        ) {
            item {
                Text(
                    text = stringResource(
                        if (list.isEmpty()) R.string.learned_categories_empty else R.string.learned_categories_intro
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.md)
                )
            }
            itemsIndexed(list, key = { _, m -> m.merchantName }) { index, mapping ->
                groupedRow(index, list.size, Modifier.animateItem()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(mapping.merchantName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            categoryLabel(mapping.category),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.remove(mapping.merchantName) }, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.learned_categories_remove, mapping.merchantName)
                        )
                    }
                }
            }
        }
    }
}
