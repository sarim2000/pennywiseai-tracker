package com.pennywiseai.tracker.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GroupSummary
import com.pennywiseai.tracker.domain.model.buildProfileAccountKeys
import com.pennywiseai.tracker.ui.components.BrandIcon
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.cards.AccountCarousel
import com.pennywiseai.tracker.ui.components.cards.BudgetCarousel
import com.pennywiseai.tracker.ui.components.cards.GroupCard
import com.pennywiseai.tracker.ui.components.cards.HomeGroupCard
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.cards.TransactionItem
import com.pennywiseai.tracker.ui.components.skeleton.TransactionItemSkeleton
import com.pennywiseai.tracker.ui.effects.frostedBlur
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.Money
import dev.chrisbanes.haze.HazeState
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// What each Home section shows. HomeScreen owns the order, the data guards
// and the entrance stagger; these are the bodies it places in the list.

@Composable
internal fun HomeBudgetsSection(
    summary: BudgetOverallSummary,
    onNavigateToBudgets: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)
    ) {
        SectionHeaderV2(
            title = stringResource(R.string.home_section_budgets),
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
            action = {
                TextButton(onClick = onNavigateToBudgets, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.home_view_all))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                }
            }
        )
        BudgetCarousel(
            summary = summary,
            onClick = onNavigateToBudgets,
            onCreateBudget = onNavigateToBudgets,
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
        )
    }
}

@Composable
internal fun HomeGoalsSection(
    goals: List<GoalProgress>,
    onNavigateToBudgets: () -> Unit,
    onGoalClick: (Long) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)
    ) {
        SectionHeaderV2(
            title = stringResource(R.string.home_section_goals),
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
            action = {
                TextButton(onClick = onNavigateToBudgets, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.home_view_all))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                }
            }
        )
        com.pennywiseai.tracker.presentation.goals.GoalsHomeCard(
            goals = goals,
            onOpenGoal = onGoalClick,
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
        )
    }
}

@Composable
internal fun HomeLoansSection(
    summary: LoanSummary,
    selectedCurrency: String,
    onNavigateToLoans: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)
    ) {
        SectionHeaderV2(
            title = stringResource(R.string.home_section_loans),
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
            action = {
                TextButton(onClick = onNavigateToLoans, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.home_view_all))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                }
            }
        )
        Box(modifier = Modifier.padding(horizontal = Dimensions.Padding.content)) {
            ActiveLoansSummaryCard(
                loans = summary.activeLoans,
                totalLentRemaining = summary.totalLentRemaining,
                totalBorrowedRemaining = summary.totalBorrowedRemaining,
                currency = selectedCurrency,
                onClick = onNavigateToLoans
            )
        }
    }
}

@Composable
private fun ActiveLoansSummaryCard(
    loans: List<com.pennywiseai.tracker.data.database.entity.LoanEntity>,
    totalLentRemaining: java.math.BigDecimal,
    totalBorrowedRemaining: java.math.BigDecimal,
    currency: String,
    onClick: () -> Unit = {}
) {
    val loanColor = MaterialTheme.colorScheme.loan

    PennyWiseCardV2(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Loan icon
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = loanColor,
                modifier = Modifier.size(Dimensions.Icon.medium)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(R.plurals.home_active_loans, loans.size, loans.size),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                // Both directions, what you owe first — a lender who also borrows
                // used to see only "owed to you". Nothing in this currency → no line.
                val subtitle = listOfNotNull(
                    totalBorrowedRemaining.takeIf { it > java.math.BigDecimal.ZERO }?.let {
                        stringResource(R.string.home_loans_you_owe, CurrencyFormatter.formatCurrency(it, currency))
                    },
                    totalLentRemaining.takeIf { it > java.math.BigDecimal.ZERO }?.let {
                        stringResource(R.string.home_loans_owed_to_you, CurrencyFormatter.formatCurrency(it, currency))
                    },
                ).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = stringResource(R.string.home_view),
                style = MaterialTheme.typography.labelLarge,
                color = loanColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
internal fun HomeGroupsSection(
    groupSummaries: List<GroupSummary>,
    onNavigateToTransactionGroups: () -> Unit,
    onGroupClick: (Long) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)
    ) {
        SectionHeaderV2(
            title = stringResource(R.string.home_section_groups),
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
            action = {
                TextButton(onClick = onNavigateToTransactionGroups, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.home_view_all))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                }
            }
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = Dimensions.Padding.content),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            items(groupSummaries, key = { it.group.id }) { summary ->
                HomeGroupCard(
                    summary = summary,
                    onClick = { onGroupClick(summary.group.id) }
                )
            }
        }
    }
}

@Composable
internal fun HomeRecentTransactionsHeader(
    onNavigateToTransactionsWithSearch: () -> Unit,
    onNavigateToTransactions: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = Dimensions.Padding.content)) {
        SectionHeaderV2(
            title = stringResource(R.string.home_section_recent_transactions),
            action = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Search button
                    IconButton(
                        onClick = onNavigateToTransactionsWithSearch,
                        modifier = Modifier.size(Dimensions.Component.iconButton),
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.home_search_transactions),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // View All button
                    TextButton(onClick = onNavigateToTransactions, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.home_view_all))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                    }
                }
            }
        )
    }
}

@Composable
internal fun HomeRecentTransactionsEmptyState(
    onScanSms: () -> Unit
) {
    PennyWiseEmptyState(
        icon = Icons.Default.Sync,
        headline = stringResource(R.string.home_empty_headline),
        description = stringResource(R.string.home_empty_description),
        actionLabel = stringResource(R.string.home_empty_action),
        onAction = { onScanSms() },
        modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
        ghostContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.Layout.listGap)
            ) {
                repeat(3) {
                    TransactionItemSkeleton()
                }
            }
        }
    )
}

@Composable
internal fun HomeRecentTransactionsList(
    recentItems: List<HomeRecentItem>,
    accountBalances: List<AccountBalanceEntity>,
    isUnifiedMode: Boolean,
    selectedCurrency: String,
    onTransactionClick: (Long) -> Unit,
    onGroupClick: (Long) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.listGap)
    ) {
        val profileAccountKeys = remember(accountBalances) {
            buildProfileAccountKeys(accountBalances)
        }
        recentItems.forEach { item ->
            when (item) {
                is HomeRecentItem.SingleTransaction -> TransactionItem(
                    transaction = item.transaction,
                    convertedAmount = item.convertedAmount,
                    displayCurrency = if (isUnifiedMode) selectedCurrency else null,
                    showTypeLabel = false,
                    profileAccountKeys = profileAccountKeys,
                    onClick = { onTransactionClick(item.transaction.id) }
                )
                is HomeRecentItem.GroupItem -> GroupCard(
                    group = item.group,
                    transactions = item.transactions,
                    convertedAmounts = item.convertedAmounts,
                    displayCurrency = if (isUnifiedMode) selectedCurrency else null,
                    onClick = { onGroupClick(item.group.id) }
                )
            }
        }
    }
}

@Composable
internal fun HomeAccountsSection(
    accountBalances: List<AccountBalanceEntity>,
    creditCards: List<AccountBalanceEntity>,
    isUnifiedMode: Boolean,
    selectedCurrency: String,
    isBalanceHidden: Boolean,
    blurEffects: Boolean,
    hazeStateBanner: HazeState,
    onNavigateToManageAccounts: () -> Unit,
    onAccountClick: (bankName: String, accountLast4: String) -> Unit,
    onToggleBalanceVisibility: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)
    ) {
        SectionHeaderV2(
            title = stringResource(R.string.home_section_accounts),
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
            action = {
                TextButton(onClick = onNavigateToManageAccounts, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.home_manage))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                }
            }
        )
        AccountCarousel(
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
            bankAccounts = accountBalances,
            creditCards = creditCards,
            onAccountClick = onAccountClick,
            isUnifiedMode = isUnifiedMode,
            selectedCurrency = selectedCurrency,
            blurEffects = blurEffects,
            hazeState = hazeStateBanner,
            isAmountHidden = isBalanceHidden,
            onToggleAmountsHidden = { onToggleBalanceVisibility() }
        )
    }
}

@Composable
internal fun HomeSubscriptionsSection(
    upcomingSubscriptions: List<SubscriptionEntity>,
    upcomingSubscriptionsTotal: BigDecimal,
    upcomingSubscriptionsByCurrency: Map<String, Money>,
    nextDueSubscription: SubscriptionEntity?,
    isUnifiedMode: Boolean,
    selectedCurrency: String,
    blurEffects: Boolean,
    hazeStateBanner: HazeState,
    onNavigateToSubscriptions: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)
    ) {
        SectionHeaderV2(
            title = stringResource(R.string.home_section_subscriptions),
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
            action = {
                TextButton(onClick = onNavigateToSubscriptions, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.home_view_all))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                }
            }
        )
        Box(modifier = Modifier.padding(horizontal = Dimensions.Padding.content)) {
            UpcomingSubscriptionsCard(
                subscriptions = upcomingSubscriptions,
                totalAmount = upcomingSubscriptionsTotal,
                totalByCurrency = upcomingSubscriptionsByCurrency,
                nextDue = nextDueSubscription,
                isUnified = isUnifiedMode,
                currency = selectedCurrency,
                onClick = onNavigateToSubscriptions,
                blurEffects = blurEffects,
                hazeState = hazeStateBanner
            )
        }
    }
}

/** Diameter of one avatar in the overlapping subscription stack. */
private val STACKED_AVATAR_SIZE = Dimensions.Icon.large

/** Horizontal step between stacked avatars — less than the diameter, so they
 *  overlap and read as a group. */
private val STACKED_AVATAR_STEP = Spacing.lg - Spacing.xs

/** The surface-coloured ring that separates one stacked avatar from the next. */
private val STACKED_AVATAR_RING = Spacing.xxs

@Composable
private fun UpcomingSubscriptionsCard(
    subscriptions: List<SubscriptionEntity>,
    totalAmount: BigDecimal,
    totalByCurrency: Map<String, com.pennywiseai.tracker.utils.Money> = emptyMap(),
    nextDue: SubscriptionEntity? = null,
    isUnified: Boolean = false,
    currency: String,
    onClick: () -> Unit = {},
    blurEffects: Boolean = false,
    hazeState: HazeState? = null
) {
    val containerColor = if (blurEffects)
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.secondaryContainer

    PennyWiseCardV2(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (blurEffects && hazeState != null) Modifier
                    .clip(RoundedCornerShape(Dimensions.CornerRadius.large))
                    .frostedBlur(hazeState, tint = containerColor)
                else Modifier
            ),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        contentPadding = Dimensions.Padding.content
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (subscriptions.isNotEmpty()) {
                    val maxIcons = 4
                    val visibleSubs = subscriptions.take(maxIcons)
                    val extraCount = subscriptions.size - maxIcons
                    // Overlapping avatar stack. The trailing Spacer reserves the
                    // stack's true width, since the offset children don't
                    // contribute to the Box's measured size — derive it from the
                    // same constants rather than restating the numbers.
                    val stackedCount = visibleSubs.size + if (extraCount > 0) 1 else 0
                    Box {
                        visibleSubs.forEachIndexed { index, sub ->
                            BrandIcon(
                                merchantName = sub.merchantName,
                                size = STACKED_AVATAR_SIZE,
                                modifier = Modifier
                                    .offset(x = STACKED_AVATAR_STEP * index)
                                    .zIndex((maxIcons - index).toFloat())
                                    .border(
                                        width = STACKED_AVATAR_RING,
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = CircleShape
                                    )
                                    .clip(CircleShape)
                            )
                        }
                        if (extraCount > 0) {
                            Box(
                                modifier = Modifier
                                    .offset(x = STACKED_AVATAR_STEP * visibleSubs.size)
                                    .zIndex(0f)
                                    .size(STACKED_AVATAR_SIZE)
                                    .border(
                                        width = STACKED_AVATAR_RING,
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = CircleShape
                                    )
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+$extraCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(
                            modifier = Modifier
                                .width(
                                    STACKED_AVATAR_SIZE +
                                        STACKED_AVATAR_STEP * (stackedCount - 1).coerceAtLeast(0)
                                )
                                .height(STACKED_AVATAR_SIZE)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(Dimensions.Icon.medium)
                    )
                }
                Column {
                    Text(
                        text = pluralStringResource(R.plurals.home_active_subscriptions, subscriptions.size, subscriptions.size),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        // Unified mode: one converted figure. Native mode: per-currency
                        // ("₹499 · $10") so a mixed set isn't summed into a mislabel.
                        text = stringResource(
                            R.string.home_subscriptions_monthly_total,
                            if (isUnified) {
                                CurrencyFormatter.formatCurrency(totalAmount, currency)
                            } else {
                                CurrencyFormatter.formatByCurrency(
                                    totalByCurrency,
                                    fallbackCurrency = currency
                                )
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = Dimensions.Alpha.subtitle)
                    )
                    val nextDate = nextDue?.nextPaymentDate
                    if (nextDue != null && nextDate != null) {
                        val days = ChronoUnit.DAYS.between(LocalDate.now(), nextDate).toInt()
                        val due = when {
                            days < 0 -> stringResource(R.string.home_subscriptions_due_overdue)
                            days == 0 -> stringResource(R.string.home_subscriptions_due_today)
                            days == 1 -> stringResource(R.string.home_subscriptions_due_tomorrow)
                            else -> pluralStringResource(R.plurals.home_subscriptions_due_in_days, days, days)
                        }
                        Text(
                            text = stringResource(R.string.home_subscriptions_next_due, nextDue.merchantName, due),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (days < 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = Dimensions.Alpha.subtitle),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.home_view),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
internal fun HomeActivitySection(
    transactionHeatmap: Map<Long, Int>,
    blurEffects: Boolean,
    hazeStateBanner: HazeState
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)
    ) {
        SectionHeaderV2(
            title = stringResource(R.string.home_section_activity),
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
        )
        com.pennywiseai.tracker.ui.components.cards.HeatmapWidget(
            transactionHeatmap = transactionHeatmap,
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
            blurEffects = blurEffects,
            hazeState = hazeStateBanner
        )
    }
}
