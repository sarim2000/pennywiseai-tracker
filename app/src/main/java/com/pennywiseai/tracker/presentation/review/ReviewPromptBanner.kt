package com.pennywiseai.tracker.presentation.review

import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.domain.review.ReviewPrompt
import com.pennywiseai.tracker.domain.review.isCalendarMonth
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.DateRangeUtils

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * The once-a-month invitation to review a finished cycle, and in January the year.
 *
 * Phrased as something the user gets ("July, summed up") rather than a favour they're
 * being asked for. It appears in the feed rather than as a push notification — a
 * promotional push from an app holding SMS permission is how you get muted, and it would
 * contradict the no-ads positioning. Dismissal is honoured for the whole period.
 */
@Composable
fun ReviewPromptBanner(
    prompt: ReviewPrompt,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isYear = prompt.kind == ReviewKind.YEAR
    val label = if (isYear) {
        prompt.start.year.toString()
    } else if (isCalendarMonth(prompt.start, prompt.end)) {
        prompt.start.format(AppDates.pattern("MMMM"))
    } else {
        // Explicit dates: "October" would be wrong for 25 Sep – 24 Oct.
        DateRangeUtils.formatDateRange(prompt.start, prompt.end)
    }

    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                start = Spacing.md,
                top = Spacing.sm,
                end = Spacing.sm,
                bottom = Spacing.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Insights,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.Icon.medium),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.md),
            ) {
                Text(
                    text = stringResource(if (isYear) R.string.review_banner_year_title else R.string.share_banner_title, label),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(if (isYear) R.string.review_banner_year_subtitle else R.string.share_banner_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.share_banner_dismiss),
                    modifier = Modifier.size(Dimensions.Icon.small),
                )
            }
        }
    }
}
