package com.pennywiseai.tracker.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.icons.categoryLabel

/**
 * `LearnedCategoriesScreen` (in :feature:learnedcategories) with the app's scaffold,
 * rows and labels. Called by its full name, as the nav host did, so `navigation` adds
 * no import of `presentation.settings` (which imports `navigation` for its routes).
 */
@Composable
fun LearnedCategoriesEntry(onNavigateBack: () -> Unit) {
    com.pennywiseai.tracker.presentation.settings.LearnedCategoriesScreen(
        scaffold = { title, content ->
            PennyWiseScaffold(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
                    }
                },
                content = content,
            )
        },
        groupedRow = { index, size, modifier, content ->
            GroupedRow(position = ListItemPosition.from(index, size), modifier = modifier, content = content)
        },
        categoryLabel = { categoryLabel(it) },
    )
}
