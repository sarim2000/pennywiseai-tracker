package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The once-a-month invitation to share a finished month.
 *
 * Phrased as something the user gets ("July, summed up") rather than a favour they're
 * being asked for. It appears in the feed rather than as a push notification — a
 * promotional push from an app holding SMS permission is how you get muted, and it would
 * contradict the no-ads positioning. Dismissal is honoured for the whole month.
 */
@Composable
fun ShareMonthBanner(
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val month = remember {
        YearMonth.from(LocalDate.now()).minusMonths(1)
            .format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))
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
                imageVector = Icons.Default.Share,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.Icon.medium),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.md),
            ) {
                Text(
                    text = "$month, summed up",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "See what PennyWise tracked for you",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(Dimensions.Icon.small),
                )
            }
        }
    }
}
