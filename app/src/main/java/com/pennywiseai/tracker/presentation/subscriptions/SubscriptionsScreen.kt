package com.pennywiseai.tracker.presentation.subscriptions

import android.animation.ValueAnimator
import com.pennywiseai.tracker.ui.animations.pwExpand
import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.utils.AppDates

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.components.skeleton.TransactionItemSkeleton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import com.pennywiseai.tracker.ui.animations.StaggeredEntrance
import com.pennywiseai.tracker.ui.animations.rememberEntranceDone
import com.pennywiseai.tracker.ui.animations.staggerDelayMs
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.Dimensions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.domain.model.getAccountType
import com.pennywiseai.tracker.presentation.accounts.icon
import com.pennywiseai.tracker.domain.model.AccountType
import com.pennywiseai.tracker.ui.components.*
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.cards.SummaryCardV2
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.theme.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.formatAmount
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import com.pennywiseai.tracker.ui.icons.categoryLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    viewModel: SubscriptionsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onAddSubscriptionClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // Subscription currently being marked-as-paid. Null = sheet closed.
    var markPaidTarget by remember { mutableStateOf<SubscriptionEntity?>(null) }
    // Suggested-payment candidates resolved when target changes. Empty list
    // = no recent SMS-derived matches (user falls through to date picker).
    var markPaidCandidates by remember {
        mutableStateOf<List<com.pennywiseai.tracker.data.database.entity.TransactionEntity>>(emptyList())
    }
    // Suggested bill under review. Null = sheet closed.
    var reviewTarget by remember { mutableStateOf<com.pennywiseai.tracker.domain.spending.RecurringSuggestion?>(null) }
    LaunchedEffect(markPaidTarget) {
        val target = markPaidTarget
        markPaidCandidates = if (target != null) viewModel.candidatesFor(target) else emptyList()
    }

    // Scroll behaviors for collapsible TopAppBar
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }
    val lazyListState = rememberLazyListState()

    // Staggered entrance — only animates on first composition
    val hasAnimated = rememberEntranceDone()

    val context = LocalContext.current
    val undoLabel = stringResource(R.string.subscriptions_undo)
    val markPaidText = uiState.markPaidMessage?.asString()

    // Show snackbar when subscription is hidden
    LaunchedEffect(uiState.lastHiddenSubscription) {
        uiState.lastHiddenSubscription?.let { subscription ->
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.subscriptions_snackbar_hidden, subscription.merchantName),
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoHide()
            }
        }
    }

    // Mark-as-paid feedback snackbar (#412). The sheet itself closes
    // optimistically; the VM publishes the outcome string here.
    LaunchedEffect(uiState.markPaidMessage) {
        markPaidText?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            viewModel.clearMarkPaidMessage()
        }
    }

    // "Payment settled": the user's own mark-paid / link gets the row's check + haptic.
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(uiState.settledSubscriptionId) {
        val id = uiState.settledSubscriptionId ?: return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        try { if (ValueAnimator.areAnimatorsEnabled()) delay(SETTLED_HOLD_MILLIS) } finally { viewModel.clearSettled(id) }
    }

    // "Not recurring" on a suggestion, undoable.
    LaunchedEffect(uiState.lastDismissedSuggestion) {
        uiState.lastDismissedSuggestion?.let { suggestion ->
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.subscriptions_suggestion_dismissed, suggestion.merchantName),
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDismissSuggestion()
            } else {
                viewModel.clearDismissedSuggestion()
            }
        }
    }

    // Show snackbar with undo action when a subscription is ended.
    // ENDED is intentionally a "soft action" — there's the Cancelled section
    // for later recovery — but mirroring the hide-undo flow keeps parity
    // with the rest of the screen and gives an instant escape hatch.
    LaunchedEffect(uiState.lastEndedSubscription) {
        uiState.lastEndedSubscription?.let { subscription ->
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.subscriptions_snackbar_ended, subscription.merchantName),
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoEnd()
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.subscriptions_title),
                hazeState = hazeState
            )
        },
        floatingActionButton = {
            SmallFloatingActionButton(
                onClick = onAddSubscriptionClick,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.subscriptions_add)
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
        ) {
            // Total Monthly Subscriptions Summary (0ms delay)
            item {
                StaggeredEntrance(delayMs = 0L, skip = hasAnimated) {
                    TotalSubscriptionsSummary(
                        totalAmount = uiState.totalMonthlyAmount,
                        totalByCurrency = uiState.totalByCurrency,
                        isUnified = uiState.isUnifiedMode,
                        activeCount = uiState.activeSubscriptions.size,
                        paidThisCycleCount = uiState.paidThisCycleCount,
                        currency = uiState.displayCurrency
                    )
                }
            }

            // Suggested: repeating payments found in history, not tracked yet.
            // Above the active list so they're seen; they never touch the totals.
            if (uiState.suggestions.isNotEmpty()) {
                item(key = "suggested-header") {
                    SectionHeaderV2(
                        title = stringResource(R.string.subscriptions_suggested_section),
                        subtitle = stringResource(R.string.subscriptions_suggested_hint)
                    )
                }
                item(key = "suggested-list") {
                    var showAll by rememberSaveable { mutableStateOf(false) }
                    val shown = if (showAll) uiState.suggestions else uiState.suggestions.take(SUGGESTIONS_PREVIEW)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        shown.forEach { suggestion ->
                            androidx.compose.runtime.key(suggestion.key) {
                                SuggestedSubscriptionItem(
                                    suggestion = suggestion,
                                    onReview = { reviewTarget = suggestion },
                                    onDismiss = { viewModel.dismissSuggestion(suggestion) }
                                )
                            }
                        }
                        if (uiState.suggestions.size > SUGGESTIONS_PREVIEW) {
                            TextButton(
                                onClick = { showAll = !showAll },
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                shapes = ButtonDefaults.shapes()
                            ) {
                                Text(
                                    if (showAll) stringResource(R.string.subscriptions_suggested_show_less)
                                    else stringResource(R.string.subscriptions_suggested_show_all, uiState.suggestions.size)
                                )
                            }
                        }
                    }
                }
            }

            // Active Subscriptions (staggered 50ms per item, starting at 50ms)
            if (uiState.activeSubscriptions.isNotEmpty()) {
                item {
                    SectionHeaderV2(title = stringResource(R.string.subscriptions_active_section))
                }
                itemsIndexed(
                    items = uiState.activeSubscriptions,
                    key = { _, item -> item.id }
                ) { index, subscription ->
                    StaggeredEntrance(delayMs = staggerDelayMs(index), skip = hasAnimated, modifier = Modifier.animateItem()) {
                        SwipeableSubscriptionItem(
                            subscription = subscription,
                            accounts = accounts,
                            isPaidThisCycle = subscription.id in uiState.paidThisCycleIds,
                            settled = subscription.id == uiState.settledSubscriptionId,
                            convertedAmount = uiState.convertedAmounts[subscription.id],
                            displayCurrency = uiState.displayCurrency,
                            onTap = { markPaidTarget = subscription },
                            onHide = { viewModel.hideSubscription(subscription.id) },
                            onMarkAsEnded = { viewModel.markAsEnded(subscription.id) },
                            onEdit = { merchantName, amount, nextDate, category, account, accountChanged ->
                                viewModel.updateSubscription(subscription.id, merchantName, amount, nextDate, category, account, accountChanged)
                            },
                            onDelete = { viewModel.deleteSubscription(subscription.id) }
                        )
                    }
                }
            }

            // Cancelled subscriptions (collapsible; only rendered when any
            // exist so the section disappears entirely on empty state).
            // Stable key so `rememberSaveable` below isn't bound to the
            // positional slot index — without it, expanding the section and
            // then reactivating / hiding an active subscription would shift
            // the index and silently collapse the section.
            if (uiState.endedSubscriptions.isNotEmpty()) {
                item(key = "cancelled-section") {
                    var expanded by rememberSaveable { mutableStateOf(false) }
                    EndedSubscriptionsSection(
                        endedSubscriptions = uiState.endedSubscriptions,
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                        onReactivate = { viewModel.reactivateSubscription(it) },
                        onDelete = { viewModel.deleteSubscription(it) }
                    )
                }
            }

            // Empty State — only when there's truly nothing to show. A user
            // who has ended every subscription would otherwise see the
            // "No subscriptions detected yet" message right above their
            // Cancelled section, which is contradictory.
            if (uiState.activeSubscriptions.isEmpty() &&
                uiState.endedSubscriptions.isEmpty() &&
                uiState.suggestions.isEmpty() &&
                !uiState.isLoading
            ) {
                item {
                    PennyWiseEmptyState(
                        icon = Icons.Default.Subscriptions,
                        headline = stringResource(R.string.subscriptions_empty_headline),
                        description = stringResource(R.string.subscriptions_empty_description)
                    )
                }
            }

            // Loading State
            if (uiState.isLoading) {
                items(5) {
                    SubscriptionItemSkeleton()
                }
            }
        }
    }

    reviewTarget?.let { target ->
        RecurringSuggestionSheet(
            suggestion = target,
            onDismiss = { reviewTarget = null },
            onTrack = { amount -> viewModel.acceptSuggestion(target, amount) },
            onNotRecurring = { viewModel.dismissSuggestion(target) }
        )
    }

    // Mark-as-paid sheet (#412). Rendered at screen scope so a list item
    // re-composition (snackbar, scroll) can't tear down the sheet.
    markPaidTarget?.let { target ->
        MarkAsPaidSheet(
            subscription = target,
            isPaidThisCycle = target.id in uiState.paidThisCycleIds,
            candidates = markPaidCandidates,
            onDismiss = { markPaidTarget = null },
            onConfirm = { paymentDate ->
                viewModel.markAsPaid(target.id, paymentDate)
            },
            onLinkExisting = { txn ->
                viewModel.linkExistingTransaction(target.id, txn.id)
            },
        )
    }
}

/** Suggestions shown before "Show all". */
private const val SUGGESTIONS_PREVIEW = 3

@Composable
private fun EndedSubscriptionsSection(
    endedSubscriptions: List<SubscriptionEntity>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onReactivate: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.subscriptions_cancelled_section, endedSubscriptions.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.subscriptions_collapse else R.string.subscriptions_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = expanded, enter = pwExpand(), exit = pwCollapse()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                endedSubscriptions.forEach { sub ->
                    EndedSubscriptionItem(
                        subscription = sub,
                        onReactivate = { onReactivate(sub.id) },
                        onDelete = { onDelete(sub.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EndedSubscriptionItem(
    subscription: SubscriptionEntity,
    onReactivate: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subscription.merchantName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.formatCurrency(
                        subscription.amount, subscription.currency
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onReactivate, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.subscriptions_reactivate)) }
            IconButton(onClick = { showDeleteConfirm = true }, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.subscriptions_action_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.subscriptions_delete_title)) },
            text = {
                Text(stringResource(R.string.subscriptions_delete_permanently_message, subscription.merchantName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.subscriptions_action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.subscriptions_action_cancel)) }
            }
        )
    }
}

@Composable
private fun TotalSubscriptionsSummary(
    totalAmount: BigDecimal,
    totalByCurrency: Map<String, com.pennywiseai.tracker.utils.Money> = emptyMap(),
    isUnified: Boolean = false,
    activeCount: Int,
    paidThisCycleCount: Int = 0,
    currency: String? = null
) {
    // Ink for the card it sits on: the total is spending, not a problem.
    val amountColor = MaterialTheme.colorScheme.onSecondaryContainer

    // Subtitle shape:
    //   no paid yet  → "5 active subscriptions"
    //   some paid    → "3 of 5 paid this cycle"
    //   all paid     → "All 5 paid this cycle ✓"
    val subtitle = when {
        activeCount == 0 -> stringResource(R.string.subscriptions_summary_none_active)
        paidThisCycleCount == 0 -> pluralStringResource(R.plurals.subscriptions_summary_active, activeCount, activeCount)
        paidThisCycleCount == activeCount -> pluralStringResource(R.plurals.subscriptions_summary_all_paid, activeCount, activeCount)
        else -> stringResource(R.string.subscriptions_summary_some_paid, paidThisCycleCount, activeCount)
    }

    SummaryCardV2(
        title = stringResource(R.string.subscriptions_summary_title),
        // Unified mode: one converted figure. Native mode: per-currency so a
        // ₹ + $ mix shows "₹399 · $30" rather than dropping non-base subs.
        amount = when {
            !isUnified -> CurrencyFormatter.formatByCurrency(
                totalByCurrency,
                fallbackCurrency = currency ?: "INR"
            )
            currency != null -> CurrencyFormatter.formatCurrency(totalAmount, currency)
            AmountPrivacy.hidden -> AmountPrivacy.MASK
            else -> totalAmount.toPlainString()
        },
        subtitle = subtitle,
        amountColor = amountColor,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableSubscriptionItem(
    subscription: SubscriptionEntity,
    accounts: List<AccountBalanceEntity> = emptyList(),
    isPaidThisCycle: Boolean = false,
    settled: Boolean = false,
    convertedAmount: BigDecimal? = null,
    displayCurrency: String? = null,
    onTap: () -> Unit = {},
    onHide: () -> Unit,
    onMarkAsEnded: () -> Unit = {},
    onEdit: (merchantName: String, amount: BigDecimal, nextDate: LocalDate?, category: String?, account: AccountBalanceEntity?, accountChanged: Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onDelete: () -> Unit = {}
) {
    var showSmsBody by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Spending is ink; a recurring income gets its colour and a "+" so it reads apart without colour.
    val isIncome = subscription.direction == SubscriptionDirection.INCOME
    val amountColor = if (isIncome) MaterialTheme.colorScheme.income else MaterialTheme.colorScheme.onSurface
    val amountPrefix = if (isIncome) "+" else ""
    
    // Not rememberSaveable: a restored row (e.g. after Undo) must start at rest, or onDismiss re-fires.
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val dismissState = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    // M3 re-runs onDismiss whenever the lambda changes while dismissed, so keep it stable.
    val currentOnHide by rememberUpdatedState(onHide)
    val onDismiss = remember { { _: SwipeToDismissBoxValue -> currentOnHide() } }

    SwipeToDismissBox(
        state = dismissState,
        // Only swipe-left hides; there is no StartToEnd anchor to veto.
        enableDismissFromStartToEnd = false,
        onDismiss = onDismiss,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                    else -> Color.Transparent
                },
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                label = "background color"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = Dimensions.Padding.content),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = stringResource(R.string.subscriptions_hide),
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                PennyWiseCardV2(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Tap = mark as paid (#412). Existing SMS-body expand
                        // moved to the kebab menu's "View source" item so the
                        // primary tap action is meaningful for ALL subs, not
                        // only those that arrived via SMS.
                        .clickable(onClick = onTap),
                    contentPadding = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Padding.content),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Brand Icon
                        SettledCheck(settled = settled, size = Dimensions.Icon.avatarLarge) {
                            BrandIcon(
                                merchantName = subscription.merchantName,
                                size = Dimensions.Icon.avatarLarge,
                                showBackground = true
                            )
                        }
                        
                        // Content
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = Spacing.sm)
                        ) {
                            Text(
                                text = subscription.merchantName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            
                            // Due-date line — icon + relative-time copy. Single
                            // line, ellipsised. Previously this row also tried to
                            // fit the category, which wrapped mid-word
                            // ("Entert\nainment") whenever the right column was
                            // wide enough to be visible.
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!subscription.smsBody.isNullOrBlank()) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = stringResource(R.string.subscriptions_sms_available),
                                        modifier = Modifier.size(Dimensions.Icon.small),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                val today = LocalDate.now()
                                val subscriptionDate = subscription.nextPaymentDate
                                if (subscriptionDate == null) {
                                    Text(
                                        text = stringResource(R.string.subscriptions_no_date),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                } else {
                                    var nextPaymentDate: LocalDate = subscriptionDate
                                    while (nextPaymentDate.isBefore(today) || nextPaymentDate.isEqual(today)) {
                                        nextPaymentDate = nextPaymentDate.plusMonths(1)
                                    }
                                    val daysUntilNext = ChronoUnit.DAYS.between(today, nextPaymentDate)

                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(Dimensions.Icon.small),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = when {
                                            daysUntilNext == 0L -> stringResource(R.string.subscriptions_due_today)
                                            daysUntilNext == 1L -> stringResource(R.string.subscriptions_due_tomorrow)
                                            daysUntilNext in 2..7 -> pluralStringResource(
                                                R.plurals.subscriptions_due_in_days,
                                                daysUntilNext.toInt(),
                                                daysUntilNext.toInt()
                                            )
                                            else -> nextPaymentDate.format(
                                                AppDates.dayMonthFormatter()
                                            )
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            daysUntilNext <= 3 -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            // "Paid Mar 15" badge — shown when this cycle has
                            // already been marked. Computed in the VM
                            // (today-anchored cycle check, single source of
                            // truth shared with the partition sort). Sits
                            // ABOVE the category since payment state is more
                            // important than the category label.
                            if (isPaidThisCycle) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(Dimensions.Icon.small),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.subscriptions_paid_on,
                                            "${subscription.lastPaidAt?.format(AppDates.dayMonthFormatter())}"
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            // Category — its own line. No bullet, no row
                            // sharing. Wraps if very long but won't break
                            // mid-word against a narrow column.
                            subscription.category?.let { category ->
                                Text(
                                    text = categoryLabel(category),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                        
                        if (convertedAmount != null && displayCurrency != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = CurrencyFormatter.signed(amountPrefix, CurrencyFormatter.formatCurrency(convertedAmount, displayCurrency)),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = amountColor
                                )
                                Text(
                                    text = "(${subscription.formatAmount()})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                text = CurrencyFormatter.signed(amountPrefix, subscription.formatAmount()),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = amountColor
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(Dimensions.Component.minTouchTarget),
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.subscriptions_more_options),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.subscriptions_menu_mark_paid)) },
                                    leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onTap()
                                    }
                                )
                                if (!subscription.smsBody.isNullOrBlank()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.subscriptions_menu_view_source)) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            showSmsBody = !showSmsBody
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.subscriptions_menu_edit)) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showEditDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.subscriptions_menu_end)) },
                                    leadingIcon = { Icon(Icons.Default.Cancel, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onMarkAsEnded()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.subscriptions_action_delete), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showDeleteConfirm = true
                                    }
                                )
                            }
                        }
                    }
                }
                
                // SMS Body Display (expandable)
                if (showSmsBody && !subscription.smsBody.isNullOrBlank()) {
                    PennyWiseCardV2(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        contentPadding = 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimensions.Padding.content)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(Dimensions.Icon.medium)
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text(
                                    text = stringResource(
                                        if (subscription.bankName == "Manual Entry") R.string.subscriptions_source_notes
                                        else R.string.subscriptions_source_original_sms
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            
                            // SMS text in monospace font
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = CurrencyFormatter.maskAmountsIn(subscription.smsBody),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    modifier = Modifier.padding(Spacing.md)
                                )
                            }
                        }
                    }
                }
            }
        }
    )

    if (showEditDialog) {
        EditSubscriptionDialog(
            subscription = subscription,
            accounts = accounts,
            onDismiss = { showEditDialog = false },
            onSave = { merchantName, amount, nextDate, category, account, accountChanged ->
                onEdit(merchantName, amount, nextDate, category, account, accountChanged)
                showEditDialog = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.subscriptions_delete_title)) },
            text = {
                Text(stringResource(R.string.subscriptions_delete_message, subscription.merchantName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.subscriptions_action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.subscriptions_action_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSubscriptionDialog(
    subscription: SubscriptionEntity,
    accounts: List<AccountBalanceEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (merchantName: String, amount: BigDecimal, nextDate: LocalDate?, category: String?, account: AccountBalanceEntity?, accountChanged: Boolean) -> Unit
) {
    var merchantName by remember { mutableStateOf(subscription.merchantName) }
    var amountText by remember { mutableStateOf(subscription.amount.toPlainString()) }
    var category by remember { mutableStateOf(subscription.category.orEmpty()) }
    var nextDate by remember { mutableStateOf(subscription.nextPaymentDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    // Whether the user actually changed the account. Guards against wiping a
    // stored account the picker can't represent (e.g. one not yet in the
    // balance list) when the user edits other fields and saves.
    var accountChanged by remember(subscription.id) { mutableStateOf(false) }
    var selectedAccount by remember(subscription.id) {
        mutableStateOf<AccountBalanceEntity?>(null)
    }
    // Pre-select the subscription's current funding account by (bank, last4).
    // Runs when the async accounts list first resolves, but never once the user
    // has touched the picker — otherwise an accounts tick mid-edit would revert
    // a freshly-picked account while accountChanged stayed true, writing the
    // stale value on save (keyed both on subscription.id keeps the two in sync).
    LaunchedEffect(subscription.id, accounts) {
        if (!accountChanged) {
            selectedAccount = accounts.firstOrNull {
                it.bankName == subscription.bankName &&
                    it.accountLast4 == subscription.accountLast4
            }
        }
    }

    val parsedAmount = remember(amountText) {
        amountText.trim().takeIf { it.isNotEmpty() }?.let {
            try { BigDecimal(it) } catch (_: NumberFormatException) { null }
        }
    }
    val isValid = merchantName.isNotBlank() && parsedAmount != null && parsedAmount > BigDecimal.ZERO

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subscriptions_edit_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedTextField(
                    value = merchantName,
                    onValueChange = { merchantName = it },
                    label = { Text(stringResource(R.string.subscriptions_edit_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.subscriptions_edit_amount, subscription.currency)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    isError = parsedAmount == null || (parsedAmount != null && parsedAmount <= BigDecimal.ZERO)
                )
                OutlinedTextField(
                    value = nextDate?.format(AppDates.dateFormatter()) ?: stringResource(R.string.subscriptions_edit_tap_to_set),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.subscriptions_edit_next_date)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    enabled = false,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Default.CalendarToday, contentDescription = stringResource(R.string.subscriptions_edit_pick_date))
                        }
                    }
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.subscriptions_edit_category)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Funding account (#570). Marking this subscription paid moves
                // the chosen account's balance; "No account" keeps it unlinked.
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedAccount?.displayLabel ?: stringResource(R.string.subscriptions_edit_no_account),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.subscriptions_edit_paid_from)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAccountMenu = true },
                        enabled = false,
                        trailingIcon = {
                            IconButton(onClick = { showAccountMenu = true }, shapes = IconButtonDefaults.shapes()) {
                                Icon(Icons.Default.AccountBalance, contentDescription = stringResource(R.string.subscriptions_edit_pick_account))
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = showAccountMenu,
                        onDismissRequest = { showAccountMenu = false }
                    ) {
                        SelectableDropdownMenuItem(
                            selected = selectedAccount == null,
                            shapes = MenuDefaults.itemShape(0, 1),
                            colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                            text = { Text(stringResource(R.string.subscriptions_edit_no_account)) },
                            onClick = {
                                selectedAccount = null
                                accountChanged = true
                                showAccountMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                            selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                        )
                        HorizontalDivider()
                        accounts.forEachIndexed { index, account ->
                            SelectableDropdownMenuItem(
                                selected = selectedAccount?.id == account.id,
                                shapes = MenuDefaults.itemShape(index, accounts.size),
                                colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                                text = {
                                    Column {
                                        Text(account.displayLabel)
                                        Text(
                                            CurrencyFormatter.formatCurrency(account.balance, account.currency),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedAccount = account
                                    accountChanged = true
                                    showAccountMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        account.getAccountType().icon,
                                        contentDescription = null
                                    )
                                },
                                selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    parsedAmount?.let { amt ->
                        onSave(merchantName, amt, nextDate, category, selectedAccount, accountChanged)
                    }
                },
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(R.string.subscriptions_edit_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.subscriptions_action_cancel)) }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = nextDate?.toEpochDay()?.times(86_400_000)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        nextDate = LocalDate.ofEpochDay(millis / 86_400_000)
                    }
                    showDatePicker = false
                }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.subscriptions_action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.subscriptions_action_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun SubscriptionItemSkeleton(
    modifier: Modifier = Modifier
) {
    TransactionItemSkeleton(modifier = modifier)
}

