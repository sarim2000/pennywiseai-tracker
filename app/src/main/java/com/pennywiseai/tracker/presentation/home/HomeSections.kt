package com.pennywiseai.tracker.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.formatAmount
import com.pennywiseai.tracker.data.database.entity.formatBalance
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GroupSummary
import com.pennywiseai.tracker.domain.model.buildProfileAccountKeys
import com.pennywiseai.tracker.ui.components.BrandIcon
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.cards.BudgetCarousel
import com.pennywiseai.tracker.ui.components.cards.GroupCard
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.HomeGroupCard
import com.pennywiseai.tracker.ui.components.cards.IconTile
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.cards.TransactionAvatar
import com.pennywiseai.tracker.ui.components.cards.TransactionItem
import com.pennywiseai.tracker.ui.components.cards.toShape
import com.pennywiseai.tracker.ui.components.skeleton.TransactionItemSkeleton
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// What each Home section shows. HomeScreen owns the order, the data guards
// and the entrance stagger; these are the bodies it places in the list.

@Composable
internal fun HomeBudgetsSection(
    summary: BudgetOverallSummary,
    /** The budget the hero already shows. */
    excludeBudgetId: Long?,
    onNavigateToBudgets: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)
    ) {
        HomeSectionHeader(
            title = stringResource(R.string.home_section_budgets),
            onSeeAll = onNavigateToBudgets,
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
        )
        BudgetCarousel(
            summary = summary,
            onClick = onNavigateToBudgets,
            onCreateBudget = onNavigateToBudgets,
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
            excludeBudgetId = excludeBudgetId,
            compact = true
        )
    }
}

@Composable
internal fun HomeGoalsSection(
    goals: List<GoalProgress>,
    onViewAll: () -> Unit,
    onGoalClick: (Long) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)
    ) {
        HomeSectionHeader(
            title = stringResource(R.string.home_section_goals),
            onSeeAll = onViewAll,
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
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
        HomeSectionHeader(
            title = stringResource(R.string.home_section_loans),
            onSeeAll = onNavigateToLoans,
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
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
        HomeSectionHeader(
            title = stringResource(R.string.home_section_groups),
            onSeeAll = onNavigateToTransactionGroups,
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
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

/** Every Home section's heading: the shared section title, and "See all" when there's a fuller screen behind it. */
@Composable
internal fun HomeSectionHeader(
    title: String,
    onSeeAll: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actionLabel: String = stringResource(R.string.home_see_all),
) {
    SectionHeaderV2(
        title = title,
        modifier = modifier,
        action = onSeeAll?.let { onClick ->
            {
                TextButton(onClick = onClick, shapes = ButtonDefaults.shapes()) {
                    Text(actionLabel)
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

/**
 * The latest transactions as one grouped block: category badge, merchant, "Category ·
 * time" (a row still in Others says it needs a category), amount at the end.
 */
@Composable
internal fun HomeRecentTransactionsList(
    recentItems: List<HomeRecentItem>,
    accountBalances: List<AccountBalanceEntity>,
    isUnifiedMode: Boolean,
    selectedCurrency: String,
    onTransactionClick: (Long) -> Unit,
    onGroupClick: (Long) -> Unit
) {
    val profileAccountKeys = remember(accountBalances) {
        buildProfileAccountKeys(accountBalances)
    }
    val today = rememberToday()
    GroupedList(modifier = Modifier.padding(horizontal = Dimensions.Padding.content)) {
        recentItems.forEachIndexed { index, item ->
            val position = ListItemPosition.from(index, recentItems.size)
            when (item) {
                is HomeRecentItem.SingleTransaction -> TransactionItem(
                    transaction = item.transaction,
                    convertedAmount = item.convertedAmount,
                    displayCurrency = if (isUnifiedMode) selectedCurrency else null,
                    // Today's rows just say when; older ones say which day too.
                    showDate = item.transaction.dateTime.toLocalDate() != today,
                    showTypeLabel = false,
                    listItemPosition = position,
                    profileAccountKeys = profileAccountKeys,
                    onClick = { onTransactionClick(item.transaction.id) }
                )
                is HomeRecentItem.GroupItem -> GroupCard(
                    group = item.group,
                    transactions = item.transactions,
                    convertedAmounts = item.convertedAmounts,
                    displayCurrency = if (isUnifiedMode) selectedCurrency else null,
                    shape = position.toShape(),
                    onClick = { onGroupClick(item.group.id) }
                )
            }
        }
    }
}

/**
 * The accounts as compact rows: bank, last 4 and type, and the balance in the account's own
 * currency (pre-converted in unified mode when a rate was found). Low balances say so in error.
 * Hidden amounts follow Home's one eye, on the hero.
 */
@Composable
internal fun HomeAccountsSection(
    accountBalances: List<AccountBalanceEntity>,
    creditCards: List<AccountBalanceEntity>,
    onNavigateToManageAccounts: () -> Unit,
    onAccountClick: (bankName: String, accountLast4: String) -> Unit,
) {
    val accounts = accountBalances + creditCards
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)
    ) {
        HomeSectionHeader(
            title = stringResource(R.string.home_section_accounts),
            onSeeAll = onNavigateToManageAccounts,
            actionLabel = stringResource(R.string.home_manage),
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
        )
        GroupedList(modifier = Modifier.padding(horizontal = Dimensions.Padding.content)) {
            accounts.forEachIndexed { index, account ->
                val isCard = index >= accountBalances.size
                // Matches Manage Accounts' low-balance alert (#509).
                val isLow = !isCard && account.lowBalanceThreshold?.let { account.balance <= it } == true
                val subtitle = listOfNotNull(
                    "••${account.accountLast4}".takeIf { account.accountLast4 != AccountBalanceEntity.WALLET_ACCOUNT_MARKER },
                    stringResource(
                        when {
                            isLow -> R.string.account_card_low_balance
                            isCard -> R.string.account_card_type_credit
                            else -> R.string.account_card_type_savings
                        }
                    )
                ).joinToString(" · ")
                GroupedRow(
                    position = ListItemPosition.from(index, accounts.size),
                    onClick = { onAccountClick(account.bankName, account.accountLast4) },
                    horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
                ) {
                    BrandIcon(merchantName = account.bankName, size = Dimensions.Icon.list, showBackground = true)
                    RowLabels(
                        title = account.bankName,
                        subtitle = subtitle,
                        subtitleColor = if (isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        subtitleMaxLines = 1
                    )
                    // A card's figure is what's owed on it, not its limit.
                    Text(
                        text = account.formatBalance(),
                        style = PennyWiseText.amountRow,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * "Coming up": the next few bills, soonest first, each in its own currency. Overdue ones
 * say so in error; today's and tomorrow's in warning. "See all" opens Bills.
 */
@Composable
internal fun HomeComingUpSection(
    upcomingSubscriptions: List<SubscriptionEntity>,
    onNavigateToSubscriptions: () -> Unit
) {
    val today = rememberToday()
    val next = remember(upcomingSubscriptions) {
        upcomingSubscriptions
            .sortedWith(compareBy(nullsLast()) { it.nextPaymentDate })
            .take(COMING_UP_ROWS)
    }
    Column(
        modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)
    ) {
        HomeSectionHeader(
            title = stringResource(R.string.home_section_coming_up),
            onSeeAll = onNavigateToSubscriptions
        )
        GroupedList {
            next.forEachIndexed { index, sub ->
                ComingUpRow(
                    subscription = sub,
                    today = today,
                    position = ListItemPosition.from(index, next.size),
                    onClick = onNavigateToSubscriptions
                )
            }
        }
    }
}

@Composable
private fun ComingUpRow(
    subscription: SubscriptionEntity,
    today: LocalDate,
    position: ListItemPosition,
    onClick: () -> Unit,
) {
    val date = subscription.nextPaymentDate
    val days = date?.let { ChronoUnit.DAYS.between(today, it).toInt() }
    val due = when {
        date == null || days == null -> null
        days < 0 -> stringResource(R.string.home_due_overdue)
        days == 0 -> stringResource(R.string.home_due_today)
        days == 1 -> stringResource(R.string.home_due_tomorrow)
        else -> "${date.format(AppDates.pattern("EEE"))}, ${date.format(AppDates.dayMonthFormatter())}"
    }
    val account = subscription.bankName?.let { bank ->
        subscription.accountLast4?.let { "$bank ••$it" } ?: bank
    }
    val subtitle = listOfNotNull(due, account).joinToString(" · ")
    val subtitleColor = when {
        days != null && days < 0 -> MaterialTheme.colorScheme.error
        days != null && days <= 1 -> MaterialTheme.colorScheme.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    GroupedRow(
        position = position,
        onClick = onClick,
        horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
    ) {
        val category = subscription.category
        if (category.isNullOrBlank()) {
            BrandIcon(merchantName = subscription.merchantName, size = Dimensions.Icon.list)
        } else {
            TransactionAvatar(merchantName = subscription.merchantName, category = category)
        }
        RowLabels(
            title = subscription.merchantName,
            subtitle = subtitle.ifEmpty { null },
            subtitleColor = subtitleColor,
            subtitleMaxLines = 1
        )
        Text(
            text = subscription.formatAmount(),
            style = PennyWiseText.amountRow,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/** How many bills "Coming up" lists; the rest are a "See all" away. */
private const val COMING_UP_ROWS = 3

@Composable
internal fun HomeActivitySection(
    transactionHeatmap: Map<Long, Int>
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)
    ) {
        HomeSectionHeader(
            title = stringResource(R.string.home_section_activity),
            onSeeAll = null,
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
        )
        com.pennywiseai.tracker.ui.components.cards.HeatmapWidget(
            transactionHeatmap = transactionHeatmap,
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
        )
    }
}

/** How many unsorted rows one tap on the "Needs a category" card walks, oldest first. */
internal const val UNSORTED_WALK_SIZE = 20

/**
 * "7 need a category": one compact row, a count only, no amounts. Tapping walks the
 * oldest [UNSORTED_WALK_SIZE] in the category picker, as the evening recap does.
 */
@Composable
internal fun HomeUnsortedRow(
    count: Int,
    onSort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val action = when {
        count > UNSORTED_WALK_SIZE -> stringResource(R.string.home_unsorted_action_batch, UNSORTED_WALK_SIZE)
        count > 1 -> stringResource(R.string.notif_daily_action_sort_all)
        else -> stringResource(R.string.notif_daily_action_sort)
    }
    GroupedRow(
        position = ListItemPosition.Single,
        onClick = onSort,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
    ) {
        IconTile(
            icon = Icons.Default.Category,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            size = Dimensions.Icon.list,
            glyphSize = Dimensions.Icon.inline
        )
        RowLabels(title = pluralStringResource(R.plurals.home_unsorted_row, count, count))
        Text(
            text = action,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** Today, read again on every resume, so "Today" and "Tomorrow" move on past midnight. */
@Composable
internal fun rememberToday(): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now()) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        today = LocalDate.now()
        onPauseOrDispose { }
    }
    // And at midnight while Home stays open, so "Due today" turns into "Overdue" on time.
    LaunchedEffect(today) {
        val untilTomorrow = java.time.Duration.between(java.time.LocalDateTime.now(), today.plusDays(1).atStartOfDay())
        kotlinx.coroutines.delay(untilTomorrow.toMillis().coerceAtLeast(0) + MIDNIGHT_SLACK_MILLIS)
        today = LocalDate.now()
    }
    return today
}

/** A second past midnight, so the clock has surely turned when the date is read again. */
private const val MIDNIGHT_SLACK_MILLIS = 1_000L
