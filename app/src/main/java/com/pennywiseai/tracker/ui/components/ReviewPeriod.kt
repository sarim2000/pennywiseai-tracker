package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.review.PeriodReview
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.domain.review.ReviewRange
import com.pennywiseai.tracker.domain.review.isCalendarMonth
import com.pennywiseai.tracker.domain.review.partialYearSpan
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.DateRangeUtils

// The period picker and labels the review and the share studio both show.

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PeriodSwitcher(
    label: String,
    canGoOlder: Boolean,
    canGoNewer: Boolean,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOlder, enabled = canGoOlder, shapes = IconButtonDefaults.shapes()) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.review_older),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNewer, enabled = canGoNewer, shapes = IconButtonDefaults.shapes()) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.review_newer),
            )
        }
    }
}

/** What a period is compared with, as it reads after "more than": "September", "the week before". */
@Composable
internal fun comparisonTarget(range: ReviewRange): String = when (range.kind) {
    ReviewKind.WEEK -> stringResource(R.string.review_target_week)
    ReviewKind.MONTH ->
        if (range.isCalendarMonth()) range.previousStart.format(AppDates.pattern("MMMM"))
        else stringResource(R.string.review_target_cycle)
    ReviewKind.YEAR ->
        if (range.isComplete) range.previousStart.year.toString()
        else stringResource(R.string.review_target_same_span, range.previousStart.year)
}

/** Explicit dates on a non-calendar cycle: "October" would be wrong for 25 Sep – 24 Oct. */
@Composable
internal fun periodLabel(range: ReviewRange): String = when (range.kind) {
    ReviewKind.WEEK -> DateRangeUtils.formatDateRange(range.start, range.end)
    ReviewKind.MONTH ->
        if (range.isCalendarMonth()) range.start.format(AppDates.pattern("MMMM yyyy"))
        else DateRangeUtils.formatDateRange(range.start, range.end)
    ReviewKind.YEAR ->
        if (range.isComplete) range.start.year.toString()
        else stringResource(R.string.review_year_so_far, range.start.year)
}

@Composable
internal fun shareLabel(review: PeriodReview): String {
    val locale = LocalConfiguration.current.locales[0]
    if (review.range.kind != ReviewKind.YEAR) return periodLabel(review.range).uppercase(locale)
    val span = partialYearSpan(review.firstRecorded, review.lastRecorded)
        ?: return review.range.start.year.toString()
    val months = AppDates.pattern("MMM")
    return "${span.first.format(months)} – ${span.second.format(months)} ${span.first.year}"
        .uppercase(locale)
}

private fun ReviewRange.isCalendarMonth(): Boolean = isCalendarMonth(start, end)
