package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * Home's compact entry to the Subscriptions screen's "Suggested" list:
 * "2 possible recurring bills — Netflix, Orange". Same layout as
 * [PaydaySuggestionCard].
 */
@Composable
fun RecurringSuggestionsCard(
    merchantNames: List<String>,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSecondaryContainer
    val count = merchantNames.size
    val names = merchantNames.take(MAX_NAMES).joinToString(", ")
    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentPadding = Spacing.none,
        onClick = onReview,
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, top = Spacing.md, end = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
        ) {
            Icon(
                imageVector = Icons.Default.EventRepeat,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(Dimensions.Icon.medium),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = pluralStringResource(R.plurals.home_recurring_card_title, count, count),
                    style = MaterialTheme.typography.titleSmall,
                    color = ink,
                )
                Text(
                    text = if (count > MAX_NAMES) {
                        stringResource(R.string.home_recurring_card_body_more, names, count - MAX_NAMES)
                    } else {
                        stringResource(R.string.home_recurring_card_body, names)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = Spacing.xs),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onReview, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.home_recurring_card_action))
            }
            IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.home_recurring_card_dismiss),
                    tint = ink,
                    modifier = Modifier.size(Dimensions.Icon.small),
                )
            }
        }
    }
}

private const val MAX_NAMES = 2
