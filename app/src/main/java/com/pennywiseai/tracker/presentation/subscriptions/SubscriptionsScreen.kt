package com.pennywiseai.tracker.presentation.subscriptions

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.database.entity.formatAmount
import com.pennywiseai.tracker.domain.model.AccountType
import com.pennywiseai.tracker.domain.model.getAccountType
import com.pennywiseai.tracker.domain.spending.RecurringCharge
import com.pennywiseai.tracker.presentation.accounts.icon
import com.pennywiseai.tracker.presentation.plan.PlanHeroAmount
import com.pennywiseai.tracker.presentation.plan.PlanHeroCard
import com.pennywiseai.tracker.presentation.plan.PlanSwitcher
import com.pennywiseai.tracker.presentation.plan.PlanTabHost
import com.pennywiseai.tracker.presentation.plan.planOverline
import com.pennywiseai.tracker.ui.animations.StaggeredEntrance
import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
import com.pennywiseai.tracker.ui.animations.rememberEntranceDone
import com.pennywiseai.tracker.ui.animations.staggerDelayMs
import com.pennywiseai.tracker.ui.components.*
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.DockNavAction
import com.pennywiseai.tracker.ui.components.LocalNavDock
import com.pennywiseai.tracker.ui.components.NavDockAction
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.cards.toShape
import com.pennywiseai.tracker.ui.components.mascot.Mascot
import com.pennywiseai.tracker.ui.components.mascot.MascotShape
import com.pennywiseai.tracker.ui.components.skeleton.TransactionItemSkeleton
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.spending
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    viewModel: SubscriptionsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onAddSubscriptionClick: () -> Unit = {},
    // A tab root has no back arrow; opened over another screen (a calendar bill's link), it needs one.
    showBack: Boolean = false,
    /** Set when drawn as the Plan tab's Bills section. */
    planTab: PlanTabHost? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
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

    // With the floating nav bar, the Plan tab docks "Add" beside it instead of a FAB.
    val dockAdd = planTab != null && LocalNavDock.current != null
    if (dockAdd) {
        DockNavAction(
            NavDockAction(
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.subscriptions_add),
                onClick = onAddSubscriptionClick,
            )
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(if (planTab != null) R.string.nav_plan else R.string.subscriptions_title),
                overline = if (planTab != null) planOverline() else null,
                hasBackButton = showBack,
                navigationContent = {
                    if (showBack) {
                        IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.subscriptions_back)
                            )
                        }
                    }
                },
                hazeState = hazeState
            )
        },
        floatingActionButton = {
            if (!dockAdd) SmallFloatingActionButton(
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
                top = Spacing.sm + paddingValues.calculateTopPadding(),
                bottom = if (planTab != null) Dimensions.Component.bottomBarHeight + Spacing.md
                else paddingValues.calculateBottomPadding()
            ),
            // Rows of one block sit a grouped-list gutter apart; each block adds [BlockGap] on top.
            verticalArrangement = Arrangement.spacedBy(Spacing.Layout.groupedListGap),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
        ) {
            planTab?.let { host -> item(key = "plan-switch") { PlanSwitcher(host, Modifier.padding(bottom = BlockGap)) } }

            // The month's subscriptions and bills as one total (per currency unless unified).
            item(key = "summary") {
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
                        subtitle = stringResource(R.string.subscriptions_suggested_hint),
                        modifier = Modifier.padding(top = BlockGap)
                    )
                }
                item(key = "suggested-list") {
                    var showAll by rememberSaveable { mutableStateOf(false) }
                    val shown = if (showAll) uiState.suggestions else uiState.suggestions.take(SUGGESTIONS_PREVIEW)
                    Column(
                        modifier = Modifier.padding(top = Spacing.Layout.headerToContent),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.groupedListGap)
                    ) {
                        shown.forEachIndexed { index, suggestion ->
                            androidx.compose.runtime.key(suggestion.key) {
                                SuggestedSubscriptionItem(
                                    suggestion = suggestion,
                                    position = ListItemPosition.from(index, shown.size),
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

            // Active subscriptions: one segmented block, soonest first.
            if (uiState.activeSubscriptions.isNotEmpty()) {
                item(key = "active-header") {
                    SectionHeaderV2(
                        title = stringResource(R.string.plan_bills_next_payments),
                        modifier = Modifier.padding(top = BlockGap, bottom = Spacing.Layout.headerToContent)
                    )
                }
                val activeCount = uiState.activeSubscriptions.size
                itemsIndexed(
                    items = uiState.activeSubscriptions,
                    key = { _, item -> item.id }
                ) { index, subscription ->
                    StaggeredEntrance(delayMs = staggerDelayMs(index), skip = hasAnimated, modifier = Modifier.animateItem()) {
                        SwipeableSubscriptionItem(
                            subscription = subscription,
                            position = ListItemPosition.from(index, activeCount),
                            accounts = accounts,
                            isPaidThisCycle = subscription.id in uiState.paidThisCycleIds,
                            settled = subscription.id == uiState.settledSubscriptionId,
                            convertedAmount = uiState.convertedAmounts[subscription.id],
                            displayCurrency = uiState.displayCurrency,
                            onTap = { markPaidTarget = subscription },
                            onHide = { viewModel.hideSubscription(subscription.id) },
                            onMarkAsEnded = { viewModel.markAsEnded(subscription.id) },
                            onEdit = { merchantName, amount, nextDate, category, account, accountChanged, totalPayments, paymentsMade ->
                                viewModel.updateSubscription(subscription.id, merchantName, amount, nextDate, category, account, accountChanged, totalPayments, paymentsMade)
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
                        modifier = Modifier.padding(top = BlockGap),
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
                item(key = "empty") {
                    PennyWiseEmptyState(
                        icon = Icons.Default.Subscriptions,
                        headline = stringResource(R.string.subscriptions_empty_headline),
                        description = stringResource(R.string.subscriptions_empty_description),
                        mascot = { Mascot(size = Dimensions.Icon.extraLarge, shape = MascotShape.Bell, interactive = true) }
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

/** "3 of 12 paid · ₹4,500 left" for a plan with an end (#1061), null for one that keeps renewing. */
@Composable
internal fun paymentsProgress(subscription: SubscriptionEntity): String? {
    val total = subscription.totalPayments ?: return null
    val left = RecurringCharge.amountLeft(subscription) ?: return null
    return stringResource(
        R.string.subscriptions_payments_progress,
        subscription.paymentsMade,
        total,
        CurrencyFormatter.formatCurrency(left, subscription.currency)
    )
}

/** Gap between the blocks of a Plan section (hero, suggestions, payments); rows inside a block sit 2dp apart. */
private val BlockGap = Spacing.smd

/** Suggestions shown before "Show all". */
private const val SUGGESTIONS_PREVIEW = 3

@Composable
private fun EndedSubscriptionsSection(
    endedSubscriptions: List<SubscriptionEntity>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onReactivate: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onToggle)
                .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.subscriptions_cancelled_section, endedSubscriptions.size),
                style = PennyWiseText.sectionHeader,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.subscriptions_collapse else R.string.subscriptions_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = expanded, enter = pwExpand(), exit = pwCollapse()) {
            GroupedList {
                endedSubscriptions.forEachIndexed { index, sub ->
                    EndedSubscriptionItem(
                        subscription = sub,
                        position = ListItemPosition.from(index, endedSubscriptions.size),
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
    position: ListItemPosition,
    onReactivate: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    GroupedRow(position = position, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = subscription.merchantName,
                style = PennyWiseText.rowTitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = CurrencyFormatter.formatCurrency(
                    subscription.amount, subscription.currency
                ),
                style = PennyWiseText.amountSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            subscription.totalPayments?.let { total ->
                Text(
                    text = stringResource(R.string.subscriptions_payments_count, subscription.paymentsMade, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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

/**
 * The Bills hero: what the active subscriptions and bills cost a month (each
 * normalised to monthly), and how many there are / how many are paid this cycle.
 */
@Composable
private fun TotalSubscriptionsSummary(
    totalAmount: BigDecimal,
    totalByCurrency: Map<String, com.pennywiseai.tracker.utils.Money> = emptyMap(),
    isUnified: Boolean = false,
    activeCount: Int,
    paidThisCycleCount: Int = 0,
    currency: String? = null
) {
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
    // Unified mode: one converted figure. Native mode: per-currency so a
    // ₹ + $ mix shows "₹399 · $30" rather than dropping non-base subs.
    val amount = when {
        !isUnified -> CurrencyFormatter.formatByCurrency(
            totalByCurrency,
            fallbackCurrency = currency ?: "INR"
        )
        currency != null -> CurrencyFormatter.formatCurrency(totalAmount, currency)
        AmountPrivacy.hidden -> AmountPrivacy.MASK
        else -> totalAmount.toPlainString()
    }

    PlanHeroCard(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Text(
            text = stringResource(R.string.plan_bills_hero_title),
            style = MaterialTheme.typography.titleMedium
        )
        // "a month" rides the amount's baseline; a long (multi-currency) total shrinks to fit beside it.
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PlanHeroAmount(amount, modifier = Modifier.weight(1f, fill = false).alignByBaseline())
            Text(
                text = stringResource(R.string.plan_bills_per_month),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.alignByBaseline()
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** Where a bill is paid from: the linked account's own label, else its bank ••last4. */
private fun accountLabelFor(subscription: SubscriptionEntity, accounts: List<AccountBalanceEntity>): String? {
    accounts.firstOrNull {
        it.bankName == subscription.bankName && it.accountLast4 == subscription.accountLast4
    }?.let { return it.displayLabel }
    val bank = subscription.bankName?.takeIf { it.isNotBlank() && it != "Manual Entry" } ?: return null
    return AccountBalanceEntity.accountLabel(bank, subscription.accountLast4)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableSubscriptionItem(
    subscription: SubscriptionEntity,
    position: ListItemPosition = ListItemPosition.Single,
    accounts: List<AccountBalanceEntity> = emptyList(),
    isPaidThisCycle: Boolean = false,
    settled: Boolean = false,
    convertedAmount: BigDecimal? = null,
    displayCurrency: String? = null,
    onTap: () -> Unit = {},
    onHide: () -> Unit,
    onMarkAsEnded: () -> Unit = {},
    onEdit: (merchantName: String, amount: BigDecimal, nextDate: LocalDate?, category: String?, account: AccountBalanceEntity?, accountChanged: Boolean, totalPayments: Int?, paymentsMade: Int) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onDelete: () -> Unit = {}
) {
    var showSmsBody by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Spending takes its colour; a recurring income gets its colour and a "+" so it reads apart without colour.
    val isIncome = subscription.direction == SubscriptionDirection.INCOME
    val amountColor = if (isIncome) MaterialTheme.colorScheme.income else MaterialTheme.colorScheme.spending
    val amountPrefix = if (isIncome) "+" else ""
    val shape = position.toShape()

    // Not rememberSaveable: a restored row (e.g. after Undo) must start at rest, or onDismiss re-fires.
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val dismissState = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    // M3 re-runs onDismiss whenever the lambda changes while dismissed, so keep it stable.
    val currentOnHide by rememberUpdatedState(onHide)
    val onDismiss = remember { { _: SwipeToDismissBoxValue -> currentOnHide() } }

    SwipeToDismissBox(
        state = dismissState,
        // The red "hide" backdrop takes the row's own segmented shape.
        modifier = Modifier.clip(shape),
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
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Tap = mark as paid (#412). The SMS body lives in the
                            // overflow's "View source", so the primary tap means the
                            // same thing for every row, SMS-detected or not.
                            .clickable(
                                onClickLabel = stringResource(R.string.subscriptions_menu_mark_paid),
                                onClick = onTap
                            )
                            .padding(start = Spacing.md, top = Spacing.smd, bottom = Spacing.smd, end = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettledCheck(settled = settled, size = Dimensions.Icon.avatarLarge) {
                            BrandIcon(
                                merchantName = subscription.merchantName,
                                size = Dimensions.Icon.avatarLarge,
                                showBackground = true
                            )
                        }

                        SubscriptionLabels(
                            subscription = subscription,
                            accounts = accounts,
                            isPaidThisCycle = isPaidThisCycle,
                            modifier = Modifier.weight(1f)
                        )

                        if (convertedAmount != null && displayCurrency != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = CurrencyFormatter.signed(amountPrefix, CurrencyFormatter.formatCurrency(convertedAmount, displayCurrency)),
                                    style = PennyWiseText.amountRow,
                                    color = amountColor
                                )
                                Text(
                                    text = "(${subscription.formatAmount()})",
                                    style = PennyWiseText.amountSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                text = CurrencyFormatter.signed(amountPrefix, subscription.formatAmount()),
                                style = PennyWiseText.amountRow,
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

                    // The source SMS (or notes), opened from "View source".
                    AnimatedVisibility(
                        visible = showSmsBody && !subscription.smsBody.isNullOrBlank(),
                        enter = pwExpand(),
                        exit = pwCollapse()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(Dimensions.Icon.inline)
                                )
                                Text(
                                    text = stringResource(
                                        if (subscription.bankName == "Manual Entry") R.string.subscriptions_source_notes
                                        else R.string.subscriptions_source_original_sms
                                    ),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            // SMS text in monospace font
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = CurrencyFormatter.maskAmountsIn(subscription.smsBody.orEmpty()),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    modifier = Modifier.padding(Spacing.smd)
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
            onSave = { merchantName, amount, nextDate, category, account, accountChanged, totalPayments, paymentsMade ->
                onEdit(merchantName, amount, nextDate, category, account, accountChanged, totalPayments, paymentsMade)
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

/**
 * Name, then when it's next due and where it's paid from ("Due tomorrow · Card ••7720",
 * soon-due in the warning colour), then this cycle's paid badge and a fixed plan's
 * progress when they apply. The category stands in for the account when there's none.
 */
@Composable
private fun SubscriptionLabels(
    subscription: SubscriptionEntity,
    accounts: List<AccountBalanceEntity>,
    isPaidThisCycle: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = subscription.merchantName,
            style = PennyWiseText.rowTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )

        val today = LocalDate.now()
        val nextShown = subscription.nextPaymentDate?.let {
            RecurringCharge.nextPaymentShown(it, subscription.billingCycle, today)
        }
        val daysUntilNext = nextShown?.let { ChronoUnit.DAYS.between(today, it) }
        val due = when {
            nextShown == null || daysUntilNext == null -> stringResource(R.string.subscriptions_no_date)
            daysUntilNext == 0L -> stringResource(R.string.subscriptions_due_today)
            daysUntilNext == 1L -> stringResource(R.string.subscriptions_due_tomorrow)
            daysUntilNext in 2..7 -> pluralStringResource(
                R.plurals.subscriptions_due_in_days,
                daysUntilNext.toInt(),
                daysUntilNext.toInt()
            )
            else -> nextShown.format(AppDates.dayMonthFormatter())
        }
        val source = accountLabelFor(subscription, accounts)
            ?: subscription.category?.let { categoryLabel(it) }
        // Due within three days (and not yet paid) is worth a glance; the rest stays quiet.
        val dueSoon = !isPaidThisCycle && daysUntilNext != null && daysUntilNext <= 3
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
            Text(
                text = if (source != null) stringResource(R.string.plan_bills_due_from, due, source) else due,
                style = PennyWiseText.rowSubtitle,
                color = if (dueSoon) MaterialTheme.colorScheme.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }

        // "Paid Mar 15": this cycle is already marked. Computed in the VM
        // (today-anchored cycle check, shared with the list's sort).
        if (isPaidThisCycle) {
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

        paymentsProgress(subscription)?.let { progress ->
            Text(
                text = progress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSubscriptionDialog(
    subscription: SubscriptionEntity,
    accounts: List<AccountBalanceEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (merchantName: String, amount: BigDecimal, nextDate: LocalDate?, category: String?, account: AccountBalanceEntity?, accountChanged: Boolean, totalPayments: Int?, paymentsMade: Int) -> Unit
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
    // Blank = no end. "Already paid" starts at the count so far and must leave a payment to make.
    var totalPaymentsText by remember { mutableStateOf(subscription.totalPayments?.toString().orEmpty()) }
    var paymentsMadeText by remember {
        mutableStateOf(subscription.paymentsMade.takeIf { subscription.totalPayments != null }?.toString().orEmpty())
    }
    val totalPayments = totalPaymentsText.toIntOrNull()?.takeIf { it > 0 }
    val paymentsMade = paymentsMadeText.toIntOrNull() ?: 0
    val totalPaymentsError = totalPaymentsText.isNotEmpty() && totalPayments == null
    val paymentsMadeError = totalPayments != null && paymentsMade >= totalPayments
    val isValid = merchantName.isNotBlank() && parsedAmount != null && parsedAmount > BigDecimal.ZERO &&
        !totalPaymentsError && !paymentsMadeError

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subscriptions_edit_title)) },
        text = {
            // Scrolls so the last field stays reachable on a short screen with the keyboard up.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
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
                // Ends after N payments (#1061). Scheduled income is booked without counting, so expenses only.
                if (subscription.direction == SubscriptionDirection.EXPENSE) {
                    OutlinedTextField(
                        value = totalPaymentsText,
                        onValueChange = { totalPaymentsText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text(stringResource(R.string.subscriptions_edit_ends_after)) },
                        isError = totalPaymentsError,
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (totalPaymentsText.isNotEmpty()) {
                        OutlinedTextField(
                            value = paymentsMadeText,
                            onValueChange = { paymentsMadeText = it.filter { c -> c.isDigit() }.take(3) },
                            label = { Text(stringResource(R.string.add_sub_already_paid)) },
                            supportingText = if (paymentsMadeError) {
                                { Text(stringResource(R.string.add_sub_already_paid_error)) }
                            } else null,
                            isError = paymentsMadeError,
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

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
                        onSave(merchantName, amt, nextDate, category, selectedAccount, accountChanged, totalPayments, paymentsMade)
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
