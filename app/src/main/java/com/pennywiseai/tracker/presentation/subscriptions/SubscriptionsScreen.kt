package com.pennywiseai.tracker.presentation.subscriptions

import android.animation.ValueAnimator
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.formatAmount
import com.pennywiseai.tracker.domain.model.getAccountType
import com.pennywiseai.tracker.domain.spending.RecurringCharge
import com.pennywiseai.tracker.domain.spending.RecurringSuggestion
import com.pennywiseai.tracker.presentation.accounts.icon
import com.pennywiseai.tracker.presentation.home.rememberToday
import com.pennywiseai.tracker.presentation.plan.PlanSwitcher
import com.pennywiseai.tracker.presentation.plan.PlanTabHost
import com.pennywiseai.tracker.presentation.plan.planOverline
import com.pennywiseai.tracker.ui.animations.StaggeredEntrance
import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
import com.pennywiseai.tracker.ui.animations.rememberAnimationsEnabled
import com.pennywiseai.tracker.ui.animations.rememberEntranceDone
import com.pennywiseai.tracker.ui.animations.staggerDelayMs
import com.pennywiseai.tracker.ui.components.*
import com.pennywiseai.tracker.ui.components.AmountBarDialogHost
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.DockNavAction
import com.pennywiseai.tracker.ui.components.LocalNavDock
import com.pennywiseai.tracker.ui.components.NavDockAction
import com.pennywiseai.tracker.ui.components.cards.GroupedColumn
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.cards.toShape
import com.pennywiseai.tracker.ui.components.mascot.Mascot
import com.pennywiseai.tracker.ui.components.mascot.MascotShape
import com.pennywiseai.tracker.ui.components.orSupporting
import com.pennywiseai.tracker.ui.components.rememberAmountEntry
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
import com.pennywiseai.tracker.utils.Money
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.time.LocalDate
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
    /** The bill a Home row was tapped on: scrolled to and briefly tinted. */
    focusBillId: Long? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Subscription currently being marked-as-paid. Null = sheet closed.
    var markPaidTarget by remember { mutableStateOf<SubscriptionEntity?>(null) }
    var reviewTarget by remember { mutableStateOf<RecurringSuggestion?>(null) }
    var showSuggestions by rememberSaveable { mutableStateOf(false) }
    var showPaid by rememberSaveable { mutableStateOf(false) }
    var showIncome by rememberSaveable { mutableStateOf(false) }
    var showUndated by rememberSaveable { mutableStateOf(false) }
    var showEnded by rememberSaveable { mutableStateOf(false) }
    val today = rememberToday()
    val groups = remember(uiState.activeSubscriptions, uiState.paidThisCycleIds, today) {
        uiState.activeSubscriptions.groupBy { it.billSection(today, uiState.paidThisCycleIds) }
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

    // Where a bill row sits in the list below. Mirrors the items it emits, in order, so keep
    // the two in step.
    fun billIndex(section: BillSection, position: Int): Int {
        var index = 0
        if (planTab != null) index++
        if (uiState.activeSubscriptions.any { it.direction == SubscriptionDirection.EXPENSE }) index++
        for (shown in listOf(BillSection.OVERDUE, BillSection.UPCOMING)) {
            val bills = groups[shown].orEmpty()
            if (bills.isEmpty()) continue
            index++
            if (shown == section) return index + position
            index += bills.size
        }
        if (uiState.suggestions.isNotEmpty()) {
            index++
            if (showSuggestions) index += uiState.suggestions.size
        }
        for ((collapsible, expanded) in listOf(
            BillSection.PAID to showPaid,
            BillSection.INCOME to showIncome,
            BillSection.UNDATED to showUndated
        )) {
            val bills = groups[collapsible] ?: continue
            index++
            if (collapsible == section) return index + position
            if (expanded) index += bills.size
        }
        return index
    }

    // Opened on one bill: open its section, scroll to it and tint it for a moment, once the
    // bills are in. Saveable, so a rotation or a refreshed list doesn't do it again. Its
    // menu (edit, mark paid, …) is the bill's detail, so no sheet opens.
    var focusPending by rememberSaveable { mutableStateOf(focusBillId != null) }
    var highlightId by remember { mutableStateOf<Long?>(null) }
    // Its row as the list stands now: suggestions can still arrive above it once the bills are in.
    val focusIndex by rememberUpdatedState(
        if (!focusPending) {
            null
        } else {
            groups.entries.firstOrNull { (_, bills) -> bills.any { it.id == focusBillId } }
                ?.let { (section, bills) -> billIndex(section, bills.indexOfFirst { it.id == focusBillId }) }
        }
    )
    LaunchedEffect(uiState.isLoading) {
        if (!focusPending || uiState.isLoading) return@LaunchedEffect
        // An ended, hidden or deleted bill isn't here: just the list.
        val (section, bills) = groups.entries.firstOrNull { (_, bills) -> bills.any { it.id == focusBillId } }
            ?: run {
                focusPending = false
                return@LaunchedEffect
            }
        when (section) {
            BillSection.PAID -> showPaid = true
            BillSection.INCOME -> showIncome = true
            BillSection.UNDATED -> showUndated = true
            BillSection.OVERDUE, BillSection.UPCOMING -> Unit
        }
        // Wait out the layout from before its section opened: the row at its index has to be
        // this bill. Bounded, so a list that never settles still gets the scroll.
        val settled = withTimeoutOrNull(FOCUS_SETTLE_MILLIS) {
            snapshotFlow { focusIndex to lazyListState.layoutInfo }.first { (index, info) ->
                index != null && info.totalItemsCount > index &&
                    info.visibleItemsInfo.none { it.index == index && it.key != focusBillId }
            }
        }
        val index = settled?.first ?: focusIndex ?: billIndex(section, bills.indexOfFirst { it.id == focusBillId })
        val info = lazyListState.layoutInfo
        val row = info.visibleItemsInfo.firstOrNull { it.key == focusBillId }
        if (row == null || row.offset < 0 || row.offset + row.size > info.viewportEndOffset - info.afterContentPadding) {
            lazyListState.scrollToItem(index)
        }
        // Spent only once it's on screen, so a rotation midway still lands on it.
        focusPending = false
        highlightId = focusBillId
    }
    val animationsEnabled = rememberAnimationsEnabled()
    LaunchedEffect(highlightId, animationsEnabled) {
        if (highlightId == null) return@LaunchedEffect
        if (animationsEnabled) delay(FOCUS_HIGHLIGHT_MILLIS)
        highlightId = null
    }

    // With the floating nav bar, the Plan tab docks "Add" beside it instead of a FAB.
    val dockAdd = planTab != null && LocalNavDock.current != null
    val planBarClearance = if (planTab != null) Dimensions.Component.bottomBarHeight else Spacing.none
    if (dockAdd) {
        DockNavAction(
            NavDockAction(
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.subscriptions_add),
                onClick = onAddSubscriptionClick,
            )
        )
    }

    PennyWiseScaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        // In the Plan tab the nav bar is laid over this screen: keep Undo and Add above it.
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(bottom = planBarClearance)) { data ->
                Snackbar(data, shape = MaterialTheme.shapes.large)
            }
        },
        customTopBar = {
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
                modifier = Modifier.padding(bottom = planBarClearance),
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

            if (uiState.activeSubscriptions.any { it.direction == SubscriptionDirection.EXPENSE }) {
                item(key = "summary") {
                    TotalSubscriptionsSummary(
                        totalAmount = uiState.totalMonthlyAmount,
                        totalByCurrency = uiState.totalByCurrency,
                        isUnified = uiState.isUnifiedMode,
                        currency = uiState.displayCurrency
                    )
                }
            }

            fun bills(section: BillSection, hasDisclosure: Boolean = false) {
                val subscriptions = groups[section].orEmpty()
                val offset = if (hasDisclosure) 1 else 0
                itemsIndexed(subscriptions, key = { _, item -> item.id }) { index, subscription ->
                    StaggeredEntrance(delayMs = staggerDelayMs(index), skip = hasAnimated, modifier = Modifier.animateItem()) {
                        SwipeableSubscriptionItem(
                            subscription = subscription,
                            position = ListItemPosition.from(index + offset, subscriptions.size + offset),
                            accounts = accounts,
                            isPaidThisCycle = subscription.id in uiState.paidThisCycleIds,
                            settled = subscription.id == uiState.settledSubscriptionId,
                            highlighted = subscription.id == highlightId,
                            convertedAmount = uiState.convertedAmounts[subscription.id],
                            displayCurrency = uiState.displayCurrency,
                            today = today,
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

            for (section in listOf(BillSection.OVERDUE, BillSection.UPCOMING)) {
                if (groups[section].isNullOrEmpty()) continue
                item(key = section.name) {
                    SectionHeaderV2(
                        title = stringResource(section.title),
                        modifier = Modifier.padding(top = BlockGap, bottom = Spacing.Layout.headerToContent)
                    )
                }
                bills(section)
            }

            if (uiState.suggestions.isNotEmpty()) {
                item(key = "suggested-header") {
                    BillDisclosure(
                        title = pluralStringResource(
                            R.plurals.plan_bills_review_suggestions, uiState.suggestions.size, uiState.suggestions.size
                        ),
                        expanded = showSuggestions,
                        onToggle = { showSuggestions = !showSuggestions }
                    )
                }
                if (showSuggestions) {
                    itemsIndexed(
                        uiState.suggestions,
                        key = { _, suggestion -> "suggestion-${suggestion.key}" }
                    ) { index, suggestion ->
                        SuggestedSubscriptionItem(
                            suggestion = suggestion,
                            position = ListItemPosition.from(index + 1, uiState.suggestions.size + 1),
                            onReview = { reviewTarget = suggestion },
                            onDismiss = { viewModel.dismissSuggestion(suggestion) }
                        )
                    }
                }
            }

            fun collapsibleBills(section: BillSection, expanded: Boolean, onToggle: () -> Unit) {
                val count = groups[section]?.size ?: return
                item(key = section.name) {
                    BillDisclosure(title = stringResource(section.title, count), expanded = expanded, onToggle = onToggle)
                }
                if (expanded) bills(section, hasDisclosure = true)
            }
            collapsibleBills(BillSection.PAID, showPaid) { showPaid = !showPaid }
            collapsibleBills(BillSection.INCOME, showIncome) { showIncome = !showIncome }
            collapsibleBills(BillSection.UNDATED, showUndated) { showUndated = !showUndated }

            if (uiState.endedSubscriptions.isNotEmpty()) {
                item(key = "ended-header") {
                    BillDisclosure(
                        title = stringResource(R.string.plan_bills_ended, uiState.endedSubscriptions.size),
                        expanded = showEnded,
                        onToggle = { showEnded = !showEnded }
                    )
                }
                if (showEnded) {
                    itemsIndexed(
                        uiState.endedSubscriptions,
                        key = { _, subscription -> "ended-${subscription.id}" }
                    ) { index, subscription ->
                        EndedSubscriptionItem(
                            subscription = subscription,
                            position = ListItemPosition.from(index + 1, uiState.endedSubscriptions.size + 1),
                            onReactivate = { viewModel.reactivateSubscription(subscription.id) },
                            onDelete = { viewModel.deleteSubscription(subscription.id) }
                        )
                    }
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
        key(target.id) {
            var candidates by remember { mutableStateOf(emptyList<TransactionEntity>()) }
            LaunchedEffect(target) { candidates = viewModel.candidatesFor(target) }
            MarkAsPaidSheet(
                subscription = target,
                isPaidThisCycle = target.id in uiState.paidThisCycleIds,
                candidates = candidates,
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

/** How long the bill Plan was opened on stays tinted. */
private const val FOCUS_HIGHLIGHT_MILLIS = 1500L

/** How long a focused bill waits for its row to be laid out before scrolling anyway. */
private const val FOCUS_SETTLE_MILLIS = 2000L

internal enum class BillSection(@get:StringRes val title: Int) {
    OVERDUE(R.string.plan_bills_overdue),
    UPCOMING(R.string.plan_bills_upcoming),
    PAID(R.string.plan_bills_paid),
    INCOME(R.string.plan_bills_income),
    UNDATED(R.string.plan_bills_undated)
}

// The stored schedule is advanced when a payment is recorded. An unpaid date in the past is overdue.
internal fun SubscriptionEntity.billSection(today: LocalDate, paidIds: Set<Long>): BillSection = when {
    direction == SubscriptionDirection.INCOME -> BillSection.INCOME
    id in paidIds -> BillSection.PAID
    nextPaymentDate == null -> BillSection.UNDATED
    nextPaymentDate.isBefore(today) -> BillSection.OVERDUE
    else -> BillSection.UPCOMING
}

@Composable
private fun BillDisclosure(title: String, expanded: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (expanded) R.string.subscriptions_collapse else R.string.subscriptions_expand)
    GroupedRow(
        position = if (expanded) ListItemPosition.Top else ListItemPosition.Single,
        modifier = Modifier.padding(top = BlockGap).semantics { stateDescription = state },
        onClick = onToggle
    ) {
        RowLabels(title = title, titleMaxLines = Int.MAX_VALUE)
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

/** Recurring costs normalized to one month, displayed in the loaded currency or per native currency. */
@Composable
private fun TotalSubscriptionsSummary(
    totalAmount: BigDecimal,
    totalByCurrency: Map<String, Money>,
    isUnified: Boolean,
    currency: String?
) {
    GroupedColumn(position = ListItemPosition.Single, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.plan_bills_monthly_estimate), style = MaterialTheme.typography.titleSmall)
        when {
            AmountPrivacy.hidden -> Text(AmountPrivacy.MASK, style = PennyWiseText.amountLarge)
            isUnified && currency != null ->
                Text(CurrencyFormatter.formatCurrency(totalAmount, currency), style = PennyWiseText.amountLarge)
            else -> totalByCurrency.values.sortedBy { it.currency }.forEach { money ->
                Text(CurrencyFormatter.formatCurrency(money.amount, money.currency), style = PennyWiseText.amountLarge)
            }
        }
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
    highlighted: Boolean = false,
    convertedAmount: BigDecimal? = null,
    displayCurrency: String? = null,
    today: LocalDate,
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
    val markLabel = if (isIncome) R.string.subscriptions_menu_mark_received else R.string.subscriptions_menu_mark_paid
    val shape = position.toShape()
    // The bill Plan was opened on takes a raised tone for a moment. A neutral surface, so every
    // ink on the row still reads on it.
    val containerColor by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "focus highlight"
    )

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
            GroupedColumn(
                position = position,
                containerColor = containerColor,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettledCheck(settled = settled, size = Dimensions.Icon.avatar) {
                        BrandIcon(
                            merchantName = subscription.merchantName,
                            size = Dimensions.Icon.avatar,
                            showBackground = true
                        )
                    }
                    SubscriptionLabels(
                        subscription = subscription,
                        accounts = accounts,
                        isPaidThisCycle = isPaidThisCycle,
                        today = today,
                        modifier = Modifier.weight(1f)
                    )
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
                            shape = MaterialTheme.shapes.large,
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
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
                                text = { Text(stringResource(R.string.subscriptions_hide)) },
                                leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                                onClick = { showMenu = false; onHide() }
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
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    val amount = if (convertedAmount != null && displayCurrency != null) {
                        CurrencyFormatter.formatCurrency(convertedAmount, displayCurrency)
                    } else subscription.formatAmount()
                    Column {
                        Text(CurrencyFormatter.signed(amountPrefix, amount), style = PennyWiseText.amountRow, color = amountColor)
                        if (convertedAmount != null && displayCurrency != null) {
                            Text(subscription.formatAmount(), style = PennyWiseText.amountSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (!isPaidThisCycle) {
                        TextButton(onClick = onTap, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(markLabel))
                        }
                    }
                }
                paymentsProgress(subscription)?.let { progress ->
                    Text(progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            .padding(top = Spacing.sm),
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

@Composable
private fun SubscriptionLabels(
    subscription: SubscriptionEntity,
    accounts: List<AccountBalanceEntity>,
    isPaidThisCycle: Boolean,
    today: LocalDate,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(subscription.merchantName, style = PennyWiseText.rowTitle)
        val dueDate = subscription.nextPaymentDate
        val daysUntilDue = dueDate?.let { ChronoUnit.DAYS.between(today, it) }
        val overdue = daysUntilDue != null && daysUntilDue < 0
        val due = when {
            dueDate == null -> stringResource(R.string.subscriptions_no_date)
            daysUntilDue == 0L -> stringResource(R.string.subscriptions_due_today)
            daysUntilDue == 1L -> stringResource(R.string.subscriptions_due_tomorrow)
            else -> dueDate.format(AppDates.dateFormatter())
        }
        val source = accountLabelFor(subscription, accounts) ?: subscription.category?.let { categoryLabel(it) }
        val timing = if (isPaidThisCycle && subscription.lastPaidAt != null) {
            stringResource(R.string.subscriptions_paid_on, subscription.lastPaidAt.format(AppDates.dayMonthFormatter()))
        } else due
        Text(
            text = if (source != null) stringResource(R.string.plan_bills_due_from, timing, source) else timing,
            style = PennyWiseText.rowSubtitle,
            color = when {
                isPaidThisCycle -> MaterialTheme.colorScheme.success
                overdue && subscription.direction == SubscriptionDirection.EXPENSE -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/** A payment count: digits only, at most three. */
private val EDIT_PAYMENT_COUNT_INPUT = InputTransformation {
    val typed = asCharSequence().toString()
    val filtered = typed.filter { it.isDigit() }.take(3)
    if (filtered != typed) replace(0, length, filtered)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSubscriptionDialog(
    subscription: SubscriptionEntity,
    accounts: List<AccountBalanceEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (merchantName: String, amount: BigDecimal, nextDate: LocalDate?, category: String?, account: AccountBalanceEntity?, accountChanged: Boolean, totalPayments: Int?, paymentsMade: Int) -> Unit
) {
    val merchantNameState = rememberTextFieldState(subscription.merchantName)
    val merchantName = merchantNameState.text.toString()
    var amountText by remember { mutableStateOf(subscription.amount.toPlainString()) }
    val categoryState = rememberTextFieldState(subscription.category.orEmpty())
    val category = categoryState.text.toString()
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
    val totalPaymentsState = rememberTextFieldState(subscription.totalPayments?.toString().orEmpty())
    val totalPaymentsText = totalPaymentsState.text.toString()
    val paymentsMadeState = rememberTextFieldState(
        subscription.paymentsMade.takeIf { subscription.totalPayments != null }?.toString().orEmpty()
    )
    val paymentsMadeText = paymentsMadeState.text.toString()
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
            AmountBarDialogHost {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    OutlinedTextField(
                        shape = RoundedField.shape,
                        state = merchantNameState,
                        label = { Text(stringResource(R.string.subscriptions_edit_name)) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val amount = rememberAmountEntry(amountText, { amountText = it.filter { c -> c.isDigit() || c == '.' } }, subscription.currency)
                    val amountSum = amount.sumHint
                    OutlinedTextField(
                        shape = RoundedField.shape,
                        state = amount.state,
                        inputTransformation = amount.inputTransformation,
                        label = { Text(stringResource(R.string.subscriptions_edit_amount, subscription.currency)) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        supportingText = amountSum.orSupporting(),
                        modifier = Modifier.fillMaxWidth().then(amount.modifier),
                        isError = amountSum?.isError ?: (parsedAmount == null || (parsedAmount != null && parsedAmount <= BigDecimal.ZERO))
                    )
                    GroupedRow(position = ListItemPosition.Single, onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        RowLabels(
                            title = stringResource(R.string.subscriptions_edit_next_date),
                            subtitle = nextDate?.format(AppDates.dateFormatter()) ?: stringResource(R.string.subscriptions_edit_tap_to_set),
                            titleMaxLines = Int.MAX_VALUE
                        )
                    }
                    OutlinedTextField(
                        shape = RoundedField.shape,
                        state = categoryState,
                        label = { Text(stringResource(R.string.subscriptions_edit_category)) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Ends after N payments (#1061). Scheduled income is booked without counting, so expenses only.
                    if (subscription.direction == SubscriptionDirection.EXPENSE) {
                        OutlinedTextField(
                            shape = RoundedField.shape,
                            state = totalPaymentsState,
                            inputTransformation = EDIT_PAYMENT_COUNT_INPUT,
                            label = { Text(stringResource(R.string.subscriptions_edit_ends_after)) },
                            isError = totalPaymentsError,
                            lineLimits = TextFieldLineLimits.SingleLine,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (totalPaymentsText.isNotEmpty()) {
                            OutlinedTextField(
                                shape = RoundedField.shape,
                                state = paymentsMadeState,
                                inputTransformation = EDIT_PAYMENT_COUNT_INPUT,
                                label = { Text(stringResource(R.string.add_sub_already_paid)) },
                                supportingText = if (paymentsMadeError) {
                                    { Text(stringResource(R.string.add_sub_already_paid_error)) }
                                } else null,
                                isError = paymentsMadeError,
                                lineLimits = TextFieldLineLimits.SingleLine,
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
                        GroupedRow(position = ListItemPosition.Single, onClick = { showAccountMenu = true }) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            RowLabels(
                                title = stringResource(R.string.subscriptions_edit_paid_from),
                                subtitle = selectedAccount?.displayLabel ?: stringResource(R.string.subscriptions_edit_no_account),
                                titleMaxLines = Int.MAX_VALUE,
                                subtitleMaxLines = Int.MAX_VALUE
                            )
                        }
                        DropdownMenu(
                            shape = MaterialTheme.shapes.large,
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
