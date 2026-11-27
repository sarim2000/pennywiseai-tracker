package com.pennywiseai.tracker.presentation.reimbursements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.cards.TransactionItem
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.AmountPrivacy
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Every reimbursable expense (#1389): what's still waiting to be paid back, with its
 * total per currency, then what was. A row opens the transaction.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReimbursementsScreen(
    onNavigateBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: ReimbursementsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    PennyWiseScaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        customTopBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.reimburse_screen_title),
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accounts_back))
                    }
                },
                hazeState = hazeState,
            )
        },
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@PennyWiseScaffold
        }
        if (uiState.pending.isEmpty() && uiState.reimbursed.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                PennyWiseEmptyState(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    headline = stringResource(R.string.reimburse_empty_title),
                    description = stringResource(R.string.reimburse_empty_description),
                )
            }
            return@PennyWiseScaffold
        }

        val lazyListState = rememberLazyListState()
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background)
                .overScrollVertical(),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + Spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.Layout.groupedListGap),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState },
        ) {
            if (uiState.pending.isNotEmpty()) {
                item(key = "waiting") {
                    PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
                        // Label, figures and count as three beats; the figures stay one block.
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(
                                stringResource(R.string.reimburse_waiting_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // A line per currency, each shrinking to fit rather than wrapping
                            // mid-number. Hidden amounts show one mask, not one per currency.
                            val totals = if (AmountPrivacy.hidden) {
                                listOf(AmountPrivacy.MASK)
                            } else {
                                uiState.pendingTotals.values.sortedByDescending { it.amount.abs() }.map { it.format() }
                            }
                            Column {
                                totals.forEach { total ->
                                    Text(
                                        total,
                                        style = PennyWiseText.amountMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        autoSize = TextAutoSize.StepBased(PennyWiseText.autoSizeFloor, PennyWiseText.amountMedium.fontSize),
                                    )
                                }
                            }
                            Text(
                                pluralStringResource(R.plurals.reimburse_pending_count, uiState.pending.size, uiState.pending.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                rows("pending", R.string.reimburse_section_pending, uiState.pending, onOpenTransaction)
            }
            if (uiState.reimbursed.isNotEmpty()) {
                rows("done", R.string.reimburse_section_done, uiState.reimbursed, onOpenTransaction)
            }
            item(key = "note") {
                Text(
                    stringResource(R.string.reimburse_counts_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.md),
                )
            }
        }
    }
}

private fun LazyListScope.rows(
    key: String,
    title: Int,
    transactions: List<TransactionEntity>,
    onOpenTransaction: (Long) -> Unit,
) {
    item(key = "$key-header") { SectionHeaderV2(title = stringResource(title), modifier = Modifier.animateItem()) }
    // Keyed by id alone (a row is in one section at a time), so a row slides from Pending to Reimbursed.
    itemsIndexed(transactions, key = { _, it -> it.id }) { i, txn ->
        TransactionItem(
            transaction = txn,
            listItemPosition = ListItemPosition.from(i, transactions.size),
            onClick = { onOpenTransaction(txn.id) },
            modifier = Modifier.animateItem(),
        )
    }
}
