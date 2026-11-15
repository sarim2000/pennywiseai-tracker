package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.TrackingProblem

/** Home warning while something stops SMS tracking. Same layout as [RecurringSuggestionsCard]. */
@Composable
fun TrackingProblemCard(
    problem: TrackingProblem,
    onFix: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSecondaryContainer
    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentPadding = Spacing.none,
        onClick = onFix,
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, top = Spacing.md, end = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
        ) {
            Icon(
                imageVector = Icons.Default.SyncProblem,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(Dimensions.Icon.medium),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = stringResource(R.string.home_tracking_card_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = ink,
                )
                Text(
                    text = stringResource(problem.bodyRes),
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
            TextButton(onClick = onFix, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.home_tracking_card_fix))
            }
            IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.home_tracking_card_dismiss),
                    tint = ink,
                    modifier = Modifier.size(Dimensions.Icon.small),
                )
            }
        }
    }
}
