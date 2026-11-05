package com.pennywiseai.tracker.presentation.subscriptions

import com.pennywiseai.tracker.utils.AppDates

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.toShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.components.skeleton.TransactionItemSkeleton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.Dimensions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.domain.model.getAccountType
import com.pennywiseai.tracker.domain.model.icon
import com.pennywiseai.tracker.presentation.accounts.AccountType
import com.pennywiseai.tracker.ui.components.*
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.theme.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
    val lazyListState = rememberLazyListState()

    // Staggered entrance animation state — only animates on first composition
    var hasAnimated by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { 30.dp.roundToPx() }

    // Mark entrance animation as complete after all stagger delays have fired
    LaunchedEffect(Unit) {
        if (!hasAnimated) {
            delay(600) // slightly after the last possible stagger
            hasAnimated = true
        }
    }

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
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.accounts_back))
                    }
                },
                actionContent = {
                    IconButton(onClick = onAddSubscriptionClick, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.subscriptions_add))
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + Dimensions.Component.fabScrollClearance
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
        ) {
            // Total Monthly Subscriptions Summary (0ms delay)
            item {
                val visible = remember { mutableStateOf(hasAnimated) }
                LaunchedEffect(Unit) {
                    if (!hasAnimated) { delay(0); visible.value = true }
                }
                AnimatedVisibility(
                    visible = visible.value,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInVertically(
                        initialOffsetY = { slideOffsetPx },
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                    )
                ) {
                    // The total counts expenses only, so the line under it does too.
                    val expenses = uiState.activeSubscriptions.filter { it.direction == SubscriptionDirection.EXPENSE }
                    TotalSubscriptionsSummary(
                        totalAmount = uiState.totalMonthlyAmount,
                        totalByCurrency = uiState.totalByCurrency,
                        isUnified = uiState.isUnifiedMode,
                        activeCount = expenses.size,
                        recurringIncomeCount = uiState.activeSubscriptions.size - expenses.size,
                        paidThisCycleCount = expenses.count { it.id in uiState.paidThisCycleIds },
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
                                shapes = pennyWiseButtonShapes()
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

            // Active Subscriptions (staggered 50ms per item, starting at 50ms). Recurring
            // income gets its own section: Salary isn't a subscription or part of their total.
            listOf(
                R.string.subscriptions_active_section to
                    uiState.activeSubscriptions.filter { it.direction == SubscriptionDirection.EXPENSE },
                R.string.subscriptions_recurring_income_section to
                    uiState.activeSubscriptions.filter { it.direction != SubscriptionDirection.EXPENSE },
            ).forEach { (titleRes, active) ->
                if (active.isEmpty()) return@forEach
                item(key = "header-$titleRes") {
                    SectionHeaderV2(title = stringResource(titleRes))
                }
                item(key = "active-list-$titleRes") {
                    GroupedList {
                        active.forEachIndexed { index, subscription ->
                            // Keyed by id so a row's swipe state follows it when the list reorders.
                            androidx.compose.runtime.key(subscription.id) {
                                val visible = remember { mutableStateOf(hasAnimated) }
                                LaunchedEffect(Unit) {
                                    if (!hasAnimated) { delay((index + 1) * 50L); visible.value = true }
                                }
                                AnimatedVisibility(
                                    visible = visible.value,
                                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInVertically(
                                        initialOffsetY = { slideOffsetPx },
                                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                                    )
                                ) {
                                    SwipeableSubscriptionItem(
                                        subscription = subscription,
                                        position = ListItemPosition.from(index, active.size),
                                        accounts = accounts,
                                        isPaidThisCycle = subscription.id in uiState.paidThisCycleIds,
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
                        icon = Icons.Outlined.Subscriptions,
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
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.subscriptions_collapse else R.string.subscriptions_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = expanded) {
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

    PennyWiseCardV2(modifier = Modifier.fillMaxWidth(), flat = true) {
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
            TextButton(onClick = onReactivate, shapes = pennyWiseButtonShapes()) { Text(stringResource(R.string.subscriptions_reactivate)) }
            IconButton(onClick = { showDeleteConfirm = true }, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
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
                    shapes = pennyWiseButtonShapes()
                ) {
                    Text(stringResource(R.string.subscriptions_action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }, shapes = pennyWiseButtonShapes()) { Text(stringResource(R.string.subscriptions_action_cancel)) }
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
    recurringIncomeCount: Int = 0,
    paidThisCycleCount: Int = 0,
    currency: String? = null
) {
    // Subtitle shape (recurring income is listed but isn't in the total, so it's
    // named apart: "3 active subscriptions · 1 recurring income"):
    //   no paid yet  → "5 active subscriptions"
    //   some paid    → "3 of 5 paid this cycle"
    //   all paid     → "All 5 paid this cycle ✓"
    val expenseLine = when {
        activeCount == 0 && recurringIncomeCount > 0 -> null
        activeCount == 0 -> stringResource(R.string.subscriptions_summary_none_active)
        paidThisCycleCount == 0 -> pluralStringResource(R.plurals.subscriptions_summary_active, activeCount, activeCount)
        paidThisCycleCount == activeCount -> pluralStringResource(R.plurals.subscriptions_summary_all_paid, activeCount, activeCount)
        else -> stringResource(R.string.subscriptions_summary_some_paid, paidThisCycleCount, activeCount)
    }
    val incomeLine = if (recurringIncomeCount > 0) {
        pluralStringResource(R.plurals.subscriptions_summary_recurring_income, recurringIncomeCount, recurringIncomeCount)
    } else {
        null
    }

    PennyWiseCardV2(modifier = Modifier.fillMaxWidth(), flat = true) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = stringResource(R.string.subscriptions_summary_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                // Unified mode: one converted figure. Native mode: per-currency so a
                // ₹ + $ mix shows "₹399 · $30" rather than dropping non-base subs.
                text = PennyWiseText.heroFigure(when {
                    !isUnified -> CurrencyFormatter.formatByCurrency(
                        totalByCurrency,
                        fallbackCurrency = currency ?: "INR"
                    )
                    currency != null -> CurrencyFormatter.formatCurrency(totalAmount, currency)
                    AmountPrivacy.hidden -> AmountPrivacy.MASK
                    else -> totalAmount.toPlainString()
                }, MaterialTheme.colorScheme.onSurfaceVariant),
                style = PennyWiseText.heroAmount,
                maxLines = 1,
                autoSize = PennyWiseText.fit(PennyWiseText.heroAmount),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = listOfNotNull(expenseLine, incomeLine).joinToString(" · "),
                style = PennyWiseText.metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** "JD60/yr": an amount and how often it's billed. Cycle strings as in [SubscriptionCost]. */
@Composable
private fun amountPerCycle(amount: String, billingCycle: String): String = stringResource(
    when (billingCycle.uppercase()) {
        "WEEKLY" -> R.string.subscriptions_amount_weekly
        "QUARTERLY" -> R.string.subscriptions_amount_quarterly
        "SEMI-ANNUAL", "SEMI ANNUAL", "SEMIANNUAL" -> R.string.subscriptions_amount_semi_annual
        "ANNUAL", "YEARLY" -> R.string.subscriptions_amount_annual
        else -> R.string.subscriptions_amount_monthly
    },
    amount
)

/**
 * The next payment to show: a date that has passed rolls forward on the bill's own
 * cycle, so a weekly bill never reads as a month away. Display only; the stored
 * schedule moves when a cycle is paid. Same arithmetic as `SubscriptionRepository.advance`.
 */
private fun nextPaymentOnOrAfter(today: LocalDate, date: LocalDate, billingCycle: String): LocalDate {
    var next = date
    while (next.isBefore(today)) {
        next = when (billingCycle.uppercase()) {
            "WEEKLY" -> next.plusWeeks(1)
            "QUARTERLY" -> next.plusMonths(3)
            "SEMI-ANNUAL", "SEMI ANNUAL", "SEMIANNUAL" -> next.plusMonths(6)
            "ANNUAL", "YEARLY" -> next.plusYears(1)
            else -> next.plusMonths(1)
        }
    }
    return next
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableSubscriptionItem(
    subscription: SubscriptionEntity,
    position: ListItemPosition,
    accounts: List<AccountBalanceEntity> = emptyList(),
    isPaidThisCycle: Boolean = false,
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
    
    // Not rememberSaveable: a restored row (e.g. after Undo) must start at rest, or onDismiss re-fires.
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val dismissState = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    // M3 re-runs onDismiss whenever the lambda changes while dismissed, so keep it stable.
    val currentOnHide by rememberUpdatedState(onHide)
    val onDismiss = remember { { _: SwipeToDismissBoxValue -> currentOnHide() } }
    val rowShape = position.toShape()

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
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "background color"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(rowShape)
                    .background(color)
                    .padding(horizontal = Dimensions.Padding.content),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Outlined.VisibilityOff,
                    contentDescription = stringResource(R.string.subscriptions_hide),
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        },
        content = {
            Column(
                // See-through rows: the page travels with the swipe.
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Tap = mark as paid (#412). Existing SMS-body expand moved to the
                // kebab menu's "View source" item so the primary tap action is
                // meaningful for ALL subs, not only those that arrived via SMS.
                GroupedRow(
                    position = position,
                    onClick = onTap,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
                    // The overflow button's 48dp target supplies the end inset.
                    contentPadding = PaddingValues(
                        start = Spacing.none,
                        end = Spacing.none,
                        top = Dimensions.Padding.listRowVertical,
                        bottom = Dimensions.Padding.listRowVertical
                    )
                ) {
                    BrandIcon(
                        merchantName = subscription.merchantName,
                        category = subscription.category,
                        size = Dimensions.Icon.list,
                        showBackground = true
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                    ) {
                        // Name and amount share a line while both fit; at a large font
                        // the amount drops below the name rather than crushing it.
                        val isIncome = subscription.direction == SubscriptionDirection.INCOME
                        val sign = if (isIncome) "+" else ""
                        val amountColor = if (isIncome) MaterialTheme.colorScheme.income else MaterialTheme.colorScheme.onSurface
                        val converted = convertedAmount?.takeIf { displayCurrency != null }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                        ) {
                            Text(
                                text = subscription.merchantName,
                                style = PennyWiseText.rowTitle,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(min = Spacing.xxxl)
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = amountPerCycle(
                                        CurrencyFormatter.signed(
                                            sign,
                                            if (converted != null && displayCurrency != null) {
                                                CurrencyFormatter.formatCurrency(converted, displayCurrency)
                                            } else {
                                                subscription.formatAmount()
                                            }
                                        ),
                                        subscription.billingCycle
                                    ),
                                    style = PennyWiseText.amountRow,
                                    color = amountColor
                                )
                                if (converted != null) {
                                    Text(
                                        text = "(${subscription.formatAmount()})",
                                        style = PennyWiseText.amountSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!subscription.smsBody.isNullOrBlank()) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Chat,
                                    contentDescription = stringResource(R.string.subscriptions_sms_available),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // One format for every row: "Next: 10 Nov · in 5 days".
                            val today = LocalDate.now()
                            val nextDate = subscription.nextPaymentDate?.let {
                                nextPaymentOnOrAfter(today, it, subscription.billingCycle)
                            }
                            Text(
                                text = if (nextDate == null) {
                                    stringResource(R.string.subscriptions_no_date)
                                } else {
                                    val days = ChronoUnit.DAYS.between(today, nextDate).toInt()
                                    stringResource(
                                        R.string.home_subscriptions_next_due,
                                        nextDate.format(AppDates.dayMonthFormatter()),
                                        when (days) {
                                            0 -> stringResource(R.string.home_subscriptions_due_today)
                                            1 -> stringResource(R.string.home_subscriptions_due_tomorrow)
                                            else -> pluralStringResource(R.plurals.home_subscriptions_due_in_days, days, days)
                                        }
                                    )
                                },
                                style = PennyWiseText.rowSubtitle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // "Paid Mar 15" badge — shown when this cycle has
                        // already been marked. Computed in the VM
                        // (today-anchored cycle check, single source of
                        // truth shared with the partition sort). Sits
                        // ABOVE the category since payment state is more
                        // important than the category label.
                        if (isPaidThisCycle) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.subscriptions_paid_on,
                                        "${subscription.lastPaidAt?.format(AppDates.dayMonthFormatter())}"
                                    ),
                                    style = PennyWiseText.rowSubtitle,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }

                        // Category — its own line. No bullet, no row sharing.
                        subscription.category?.let { category ->
                            Text(
                                text = categoryLabel(category),
                                style = PennyWiseText.rowSubtitle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(Dimensions.Component.minTouchTarget),
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
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
                                leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onTap()
                                }
                            )
                            if (!subscription.smsBody.isNullOrBlank()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.subscriptions_menu_view_source)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showSmsBody = !showSmsBody
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.subscriptions_menu_edit)) },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showEditDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.subscriptions_menu_end)) },
                                leadingIcon = { Icon(Icons.Outlined.Cancel, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onMarkAsEnded()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.subscriptions_action_delete), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Delete,
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
                                    Icons.AutoMirrored.Outlined.Chat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
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
                                shape = RoundedCornerShape(8.dp)
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
                    shapes = pennyWiseButtonShapes()
                ) {
                    Text(stringResource(R.string.subscriptions_action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }, shapes = pennyWiseButtonShapes()) { Text(stringResource(R.string.subscriptions_action_cancel)) }
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
                            Icon(Icons.Outlined.CalendarToday, contentDescription = stringResource(R.string.subscriptions_edit_pick_date))
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
                                Icon(Icons.Outlined.AccountBalance, contentDescription = stringResource(R.string.subscriptions_edit_pick_account))
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
                            leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null) },
                            selectedLeadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) }
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
                                            color = MaterialTheme.colorScheme.primary
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
                                selectedLeadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) }
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
                shapes = pennyWiseButtonShapes()
            ) { Text(stringResource(R.string.subscriptions_edit_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = pennyWiseButtonShapes()) { Text(stringResource(R.string.subscriptions_action_cancel)) }
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
                }, shapes = pennyWiseButtonShapes()) { Text(stringResource(R.string.subscriptions_action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }, shapes = pennyWiseButtonShapes()) { Text(stringResource(R.string.subscriptions_action_cancel)) }
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

