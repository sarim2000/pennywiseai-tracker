package com.pennywiseai.tracker.presentation.transactions

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.label
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToEditCategory(
    transaction: TransactionEntity,
    onRequestEdit: (TransactionEntity) -> Unit,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // Not rememberSaveable: a restored row must start at rest, or onDismiss re-fires.
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val dismissState = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    // M3 re-runs onDismiss whenever the lambda changes while dismissed, so keep it stable.
    val currentTransaction by rememberUpdatedState(transaction)
    val currentOnRequestEdit by rememberUpdatedState(onRequestEdit)
    val onDismiss = remember {
        { _: SwipeToDismissBoxValue ->
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            currentOnRequestEdit(currentTransaction)
            scope.launch { dismissState.reset() }
            Unit
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        // The swipe is purely a gesture trigger: fire the edit, then animate back to rest.
        onDismiss = onDismiss,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart ->
                        MaterialTheme.colorScheme.secondaryContainer
                    else -> Color.Transparent
                },
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                label = "swipe_edit_background"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = Dimensions.Padding.content),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = stringResource(R.string.txn_list_change_category),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        },
        content = { content() }
    )
}

@Composable
internal fun TransactionDateHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    // Fade the sticky date header into a transparent base so scrolling content
    // looks like it's passing under the label. Use `background` (the Scaffold
    // surface) instead of `surface`, otherwise an AMOLED-style theme — where
    // `background` is true-black but `surface` is tinted — paints a visible
    // dark slab behind every date header.
    val pageBg = MaterialTheme.colorScheme.background
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        pageBg,
                        pageBg,
                        pageBg.copy(alpha = 0.92f),
                        pageBg.copy(alpha = 0f)
                    )
                )
            )
            .padding(top = Spacing.md, bottom = Spacing.sm)
    ) {
        Spacer(
            modifier = Modifier
                .width(DATE_MARKER_WIDTH)
                .height(DATE_MARKER_HEIGHT)
                .background(
                    color = MaterialTheme.colorScheme.tertiary,
                    shape = CircleShape
                )
        )
        SectionHeaderV2(
            title = title,
            modifier = Modifier.weight(1f),
            // The Row already supplies the gap above; a second inset here
            // would push the marker out of line with the title.
            topSpacing = Spacing.none
        )
    }
}

@Composable
internal fun EmptyTransactionsState(
    searchQuery: String,
    selectedPeriod: TimePeriod,
    onAddClick: () -> Unit = {}
) {
    val headline = when {
        searchQuery.isNotEmpty() -> stringResource(R.string.txn_list_empty_no_results, searchQuery)
        selectedPeriod != TimePeriod.ALL -> stringResource(R.string.txn_list_empty_period, selectedPeriod.label.lowercase())
        else -> stringResource(R.string.txn_list_empty_title)
    }
    val description = when {
        searchQuery.isNotEmpty() -> stringResource(R.string.txn_list_empty_search_hint)
        selectedPeriod != TimePeriod.ALL -> stringResource(R.string.txn_list_empty_period_hint)
        else -> stringResource(R.string.txn_list_empty_hint)
    }
    val actionLabel = if (searchQuery.isEmpty() && selectedPeriod == TimePeriod.ALL) stringResource(R.string.txn_list_add_transaction) else null
    val onAction = if (actionLabel != null) onAddClick else null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimensions.Padding.content),
        contentAlignment = Alignment.Center
    ) {
        PennyWiseEmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            headline = headline,
            description = description,
            actionLabel = actionLabel,
            onAction = onAction
        )
    }
}

/** Width of the tertiary-coloured marker beside a sticky date header. */
private val DATE_MARKER_WIDTH = Spacing.xs

/** Height of that marker — set to the cap height of the header text so the two
 *  read as one unit rather than a bar next to a label. */
private val DATE_MARKER_HEIGHT = Spacing.md + Spacing.xxs
