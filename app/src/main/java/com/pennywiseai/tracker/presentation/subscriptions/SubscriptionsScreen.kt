package com.pennywiseai.tracker.presentation.subscriptions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.Dimensions
import androidx.hilt.navigation.compose.hiltViewModel
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.domain.model.getAccountType
import com.pennywiseai.tracker.presentation.accounts.AccountType
import com.pennywiseai.tracker.ui.components.*
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.cards.SummaryCardV2
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.theme.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.formatAmount
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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
    LaunchedEffect(markPaidTarget) {
        val target = markPaidTarget
        markPaidCandidates = if (target != null) viewModel.candidatesFor(target) else emptyList()
    }

    // Scroll behaviors for collapsible TopAppBar
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }
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

    // Show snackbar when subscription is hidden
    LaunchedEffect(uiState.lastHiddenSubscription) {
        uiState.lastHiddenSubscription?.let { subscription ->
            val result = snackbarHostState.showSnackbar(
                message = "${subscription.merchantName} hidden",
                actionLabel = "Undo",
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
        uiState.markPaidMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            viewModel.clearMarkPaidMessage()
        }
    }

    // Show snackbar with undo action when a subscription is ended.
    // ENDED is intentionally a "soft action" — there's the Cancelled section
    // for later recovery — but mirroring the hide-undo flow keeps parity
    // with the rest of the screen and gives an instant escape hatch.
    LaunchedEffect(uiState.lastEndedSubscription) {
        uiState.lastEndedSubscription?.let { subscription ->
            val result = snackbarHostState.showSnackbar(
                message = "${subscription.merchantName} ended",
                actionLabel = "Undo",
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
                title = "Subscriptions",
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
                    contentDescription = "Add Subscription"
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
                val visible = remember { mutableStateOf(hasAnimated) }
                LaunchedEffect(Unit) {
                    if (!hasAnimated) { delay(0); visible.value = true }
                }
                AnimatedVisibility(
                    visible = visible.value,
                    enter = fadeIn(tween(300)) + slideInVertically(
                        initialOffsetY = { slideOffsetPx },
                        animationSpec = tween(300)
                    )
                ) {
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

            // Active Subscriptions (staggered 50ms per item, starting at 50ms)
            if (uiState.activeSubscriptions.isNotEmpty()) {
                item {
                    SectionHeaderV2(title = "Active Subscriptions")
                }
                itemsIndexed(
                    items = uiState.activeSubscriptions,
                    key = { _, item -> item.id }
                ) { index, subscription ->
                    val visible = remember { mutableStateOf(hasAnimated) }
                    LaunchedEffect(Unit) {
                        if (!hasAnimated) { delay((index + 1) * 50L); visible.value = true }
                    }
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = fadeIn(tween(300)) + slideInVertically(
                            initialOffsetY = { slideOffsetPx },
                            animationSpec = tween(300)
                        )
                    ) {
                        SwipeableSubscriptionItem(
                            subscription = subscription,
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
                !uiState.isLoading
            ) {
                item {
                    PennyWiseEmptyState(
                        icon = Icons.Default.Subscriptions,
                        headline = "No subscriptions detected yet",
                        description = "Sync your SMS to detect subscriptions"
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
                text = "Cancelled (${endedSubscriptions.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
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
            TextButton(onClick = onReactivate) { Text("Reactivate") }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete subscription?") },
            text = {
                Text("Permanently delete '${subscription.merchantName}'? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
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
    val amountColor = if (!isSystemInDarkTheme()) expense_light else expense_dark
    val pluralActive = if (activeCount != 1) "s" else ""

    // Subtitle shape:
    //   no paid yet  → "5 active subscriptions"
    //   some paid    → "3 of 5 paid this cycle"
    //   all paid     → "All 5 paid this cycle ✓"
    val subtitle = when {
        activeCount == 0 -> "No active subscriptions"
        paidThisCycleCount == 0 -> "$activeCount active subscription$pluralActive"
        paidThisCycleCount == activeCount -> "All $activeCount paid this cycle"
        else -> "$paidThisCycleCount of $activeCount paid this cycle"
    }

    SummaryCardV2(
        title = "Monthly Subscriptions",
        // Unified mode: one converted figure. Native mode: per-currency so a
        // ₹ + $ mix shows "₹399 · $30" rather than dropping non-base subs.
        amount = when {
            !isUnified -> CurrencyFormatter.formatByCurrency(
                totalByCurrency,
                fallbackCurrency = currency ?: "INR"
            )
            currency != null -> CurrencyFormatter.formatCurrency(totalAmount, currency)
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
    
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onHide()
                    true
                }
                else -> false
            }
        }
    )
    
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                    else -> Color.Transparent
                },
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
                    contentDescription = "Hide",
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
                        BrandIcon(
                            merchantName = subscription.merchantName,
                            size = 48.dp,
                            showBackground = true
                        )
                        
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
                                        contentDescription = "SMS available",
                                        modifier = Modifier.size(Dimensions.Icon.small),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                val today = LocalDate.now()
                                val subscriptionDate = subscription.nextPaymentDate
                                if (subscriptionDate == null) {
                                    Text(
                                        text = "No date set",
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
                                            daysUntilNext == 0L -> "Due today"
                                            daysUntilNext == 1L -> "Due tomorrow"
                                            daysUntilNext in 2..7 -> "Due in $daysUntilNext days"
                                            else -> nextPaymentDate.format(
                                                DateTimeFormatter.ofPattern("MMM d")
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
                                        text = "Paid ${subscription.lastPaidAt?.format(DateTimeFormatter.ofPattern("MMM d"))}",
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
                                    text = category,
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
                                    text = CurrencyFormatter.formatCurrency(convertedAmount, displayCurrency),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (!isSystemInDarkTheme()) expense_light else expense_dark
                                )
                                Text(
                                    text = "(${subscription.formatAmount()})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                text = subscription.formatAmount(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!isSystemInDarkTheme()) expense_light else expense_dark
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(Dimensions.Component.minTouchTarget)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Mark as paid") },
                                    leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onTap()
                                    }
                                )
                                if (!subscription.smsBody.isNullOrBlank()) {
                                    DropdownMenuItem(
                                        text = { Text("View source") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            showSmsBody = !showSmsBody
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showEditDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("End subscription") },
                                    leadingIcon = { Icon(Icons.Default.Cancel, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onMarkAsEnded()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
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
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(Dimensions.Icon.medium)
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text(
                                    text = if (subscription.bankName == "Manual Entry") "Notes" else "Original SMS",
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
                                    text = subscription.smsBody,
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
            title = { Text("Delete subscription?") },
            text = {
                Text("\"${subscription.merchantName}\" will be permanently removed. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
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
        title = { Text("Edit subscription") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedTextField(
                    value = merchantName,
                    onValueChange = { merchantName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (${subscription.currency})") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    isError = parsedAmount == null || (parsedAmount != null && parsedAmount <= BigDecimal.ZERO)
                )
                OutlinedTextField(
                    value = nextDate?.format(DateTimeFormatter.ofPattern("d MMM yyyy")) ?: "Tap to set",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Next payment date") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    enabled = false,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Pick date")
                        }
                    }
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Funding account (#570). Marking this subscription paid moves
                // the chosen account's balance; "No account" keeps it unlinked.
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedAccount?.let {
                            AccountBalanceEntity.accountLabel(it.bankName, it.accountLast4)
                        } ?: "No account",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Paid from") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAccountMenu = true },
                        enabled = false,
                        trailingIcon = {
                            IconButton(onClick = { showAccountMenu = true }) {
                                Icon(Icons.Default.AccountBalance, contentDescription = "Pick account")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = showAccountMenu,
                        onDismissRequest = { showAccountMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("No account") },
                            onClick = {
                                selectedAccount = null
                                accountChanged = true
                                showAccountMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) }
                        )
                        HorizontalDivider()
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(AccountBalanceEntity.accountLabel(account.bankName, account.accountLast4))
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
                                        when (account.getAccountType()) {
                                            AccountType.CASH -> Icons.Default.Money
                                            AccountType.CREDIT -> Icons.Default.CreditCard
                                            else -> Icons.Default.AccountBalance
                                        },
                                        contentDescription = null
                                    )
                                },
                                trailingIcon = {
                                    if (selectedAccount?.id == account.id) {
                                        Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
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
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
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

