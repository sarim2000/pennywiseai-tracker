package com.pennywiseai.tracker.presentation.accounts

import com.pennywiseai.tracker.ui.components.cards.ledgerRule
import androidx.compose.material3.Surface
import com.pennywiseai.tracker.utils.AppDates

import com.pennywiseai.tracker.domain.model.icon

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.pennywiseai.tracker.ui.theme.isAppInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.components.*
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.skeleton.TransactionItemSkeleton
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    onNavigateBack: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    viewModel: AccountDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedDateRange by viewModel.selectedDateRange.collectAsState()

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = AccountBalanceEntity.accountLabel(uiState.bankName, uiState.accountLast4),
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.accounts_back))
                    }
                },
            )
        }
    ) { paddingValues ->
        val lazyListState = rememberLazyListState()
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .overScrollVertical(),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = Spacing.Layout.scrollBottomPadding + paddingValues.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
        ) {
            // Current Balance Card
            item {
                CurrentBalanceCard(
                    balance = uiState.currentBalance?.balance ?: BigDecimal.ZERO,
                    creditLimit = uiState.currentBalance?.creditLimit,
                    isLoan = uiState.currentBalance?.isLoan == true,
                    bankName = uiState.bankName,
                    accountLast4 = uiState.accountLast4,
                    primaryCurrency = uiState.primaryCurrency,
                    billedOutstanding = uiState.billedOutstanding,
                    unbilledOutstanding = uiState.unbilledOutstanding
                )
            }

            // Date Range Filter
            item {
                DateRangeFilter(
                    selectedRange = selectedDateRange,
                    onRangeSelected = viewModel::selectDateRange
                )
            }

            // Balance Chart (Expandable) - Updates based on selected timeframe
            if (uiState.balanceChartData.isNotEmpty()) {
                item {
                    ExpandableBalanceChart(
                        primaryCurrency = uiState.primaryCurrency,
                        balanceHistory = uiState.balanceChartData,
                        selectedTimeframe = stringResource(selectedDateRange.labelRes)
                    )
                }
            }

            // Summary Statistics
            item {
                SummaryStatistics(
                    totalIncome = uiState.totalIncome,
                    totalExpenses = uiState.totalExpenses,
                    netBalance = uiState.netBalance,
                    period = stringResource(selectedDateRange.labelRes),
                    primaryCurrency = uiState.primaryCurrency,
                    hasMultipleCurrencies = uiState.hasMultipleCurrencies
                )
            }
            
            // Transactions Header
            item {
                SectionHeaderV2(
                    title = stringResource(R.string.account_detail_transactions_header, uiState.transactions.size)
                )
            }
            
            // Transaction List
            if (uiState.transactions.isEmpty() && !uiState.isLoading) {
                item {
                    PennyWiseEmptyState(
                        icon = Icons.Outlined.Receipt,
                        headline = stringResource(R.string.account_detail_empty_title),
                        description = stringResource(R.string.account_detail_empty_description)
                    )
                }
            } else {
                items(
                    items = uiState.transactions,
                    key = { it.id }
                ) { transaction ->
                    AccountTransactionItem(
                        transaction = transaction,
                        primaryCurrency = uiState.primaryCurrency,
                        onClick = { onTransactionClick(transaction.id) }
                    )
                }
            }
            
            // Loading State
            if (uiState.isLoading) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        repeat(5) {
                            TransactionItemSkeleton()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableBalanceChart(
    primaryCurrency: String,
    balanceHistory: List<BalancePoint>,
    selectedTimeframe: String
) {
    // Open by default: the trend is why you came to an account's page.
    var isExpanded by remember { mutableStateOf(true) }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .ledgerRule()
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.smd)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.account_detail_balance_trend),
                            style = PennyWiseText.rowTitle,
                        )
                    }
                }
                
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (isExpanded) stringResource(R.string.accounts_collapse) else stringResource(R.string.accounts_expand),
                    modifier = Modifier
                        .size(Dimensions.Icon.medium)
                        .rotate(if (isExpanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    BalanceChart(
                        primaryCurrency = primaryCurrency,
                        balanceHistory = balanceHistory,
                        height = 180,
                        seriesLabel = null
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentBalanceCard(
    balance: BigDecimal,
    creditLimit: BigDecimal? = null,
    isLoan: Boolean = false,
    bankName: String,
    accountLast4: String,
    primaryCurrency: String,
    billedOutstanding: BigDecimal? = null,
    unbilledOutstanding: BigDecimal? = null
) {
    // A limit left over from an account re-typed from credit card to loan is ignored (#792).
    val isCreditCard = creditLimit != null && !isLoan
    
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isCreditCard) {
                // Credit card layout
                Text(
                    text = stringResource(R.string.account_detail_available_credit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = PennyWiseText.heroFigure(CurrencyFormatter.formatCurrency(creditLimit, primaryCurrency), MaterialTheme.colorScheme.onSurfaceVariant),
                    style = PennyWiseText.heroAmount,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Show outstanding balance if any
                if (balance > BigDecimal.ZERO) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = stringResource(R.string.account_detail_outstanding, CurrencyFormatter.formatCurrency(balance, primaryCurrency)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Show billed/unbilled split when statement day is configured
                    if (billedOutstanding != null && unbilledOutstanding != null) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = CurrencyFormatter.formatCurrency(billedOutstanding, primaryCurrency),
                                    style = PennyWiseText.amountSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.account_detail_billed),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = CurrencyFormatter.formatCurrency(unbilledOutstanding, primaryCurrency),
                                    style = PennyWiseText.amountSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.account_detail_unbilled),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                // Regular account layout
                Text(
                    text = if (isLoan) stringResource(R.string.manage_accounts_outstanding)
                           else stringResource(R.string.account_detail_current_balance),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = PennyWiseText.heroFigure(CurrencyFormatter.formatCurrency(balance, primaryCurrency), MaterialTheme.colorScheme.onSurfaceVariant),
                    style = PennyWiseText.heroAmount,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    autoSize = PennyWiseText.fit(PennyWiseText.heroAmount),
                )
            }
            
        }
    }
}

@Composable
private fun SummaryStatistics(
    totalIncome: BigDecimal,
    totalExpenses: BigDecimal,
    netBalance: BigDecimal,
    period: String,
    primaryCurrency: String,
    hasMultipleCurrencies: Boolean = false
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().ledgerRule().padding(vertical = Spacing.smd)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
            ) {
                StatisticItem(
                    label = stringResource(R.string.account_detail_income),
                    value = formatWithEstimatedDisplay(totalIncome, primaryCurrency, hasMultipleCurrencies),
                    color = if (!isAppInDarkTheme()) income_light else income_dark,
                    modifier = Modifier.weight(1f)
                )
                StatisticItem(
                    label = stringResource(R.string.account_detail_expenses),
                    value = formatWithEstimatedDisplay(totalExpenses, primaryCurrency, hasMultipleCurrencies),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                StatisticItem(
                    label = stringResource(R.string.account_detail_net),
                    value = if (hasMultipleCurrencies) {
                        formatWithEstimatedDisplay(netBalance, primaryCurrency, true)
                    } else CurrencyFormatter.formatSigned(netBalance, primaryCurrency),
                    color = if (netBalance >= BigDecimal.ZERO) {
                        if (!isAppInDarkTheme()) income_light else income_dark
                    } else {
                        if (!isAppInDarkTheme()) expense_light else expense_dark
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Formats currency with estimated display for multi-currency accounts
 */
@Composable
private fun formatWithEstimatedDisplay(
    amount: BigDecimal,
    currency: String,
    hasMultipleCurrencies: Boolean
): String {
    val formattedAmount = CurrencyFormatter.formatCurrency(amount, currency)
    return if (hasMultipleCurrencies) {
        stringResource(R.string.account_detail_estimated_amount, formattedAmount)
    } else {
        formattedAmount
    }
}

@Composable
private fun StatisticItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = value,
            style = PennyWiseText.amountRow,
            color = color,
            maxLines = 1,
            autoSize = PennyWiseText.fit(PennyWiseText.amountRow),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeFilter(
    selectedRange: DateRange,
    onRangeSelected: (DateRange) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        items(DateRange.values().toList()) { range ->
            FilterChip(
                selected = selectedRange == range,
                shapes = FilterChipDefaults.shapes(),
                onClick = { onRangeSelected(range) },
                label = { Text(stringResource(range.labelRes)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                    selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            )
        }
    }
}

@Composable
private fun AccountTransactionItem(
    transaction: TransactionEntity,
    primaryCurrency: String,
    onClick: () -> Unit
) {
    // Same rule as the Transactions list: spending is ink plus a true minus.
    val amountColor = when (transaction.transactionType) {
        TransactionType.INCOME -> MaterialTheme.colorScheme.income
        TransactionType.EXPENSE -> MaterialTheme.colorScheme.onSurface
        TransactionType.CREDIT -> MaterialTheme.colorScheme.credit
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.transfer
        TransactionType.INVESTMENT -> MaterialTheme.colorScheme.investment
    }
    val amountPrefix = when (transaction.transactionType) {
        TransactionType.INCOME -> "+"
        TransactionType.EXPENSE, TransactionType.CREDIT, TransactionType.INVESTMENT -> "\u2212"
        TransactionType.TRANSFER -> ""
    }
    
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().ledgerRule(),
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimensions.Padding.listRowVertical),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandIcon(
                    merchantName = transaction.merchantName,
                    category = transaction.category,
                    size = Dimensions.Icon.list,
                    showBackground = true
                )
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = transaction.merchantName,
                        style = PennyWiseText.rowTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = transaction.dateTime.format(
                                AppDates.dayMonthTimeFormatter()
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Show balance after if available
                        transaction.balanceAfter?.let { balance ->
                            Text(
                                text = stringResource(R.string.account_detail_balance_after, CurrencyFormatter.formatCurrency(balance, primaryCurrency)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = CurrencyFormatter.signed(
                        amountPrefix,
                        CurrencyFormatter.formatCurrency(transaction.amount, transaction.currency)
                    ),
                    style = PennyWiseText.amountRow,
                    color = amountColor,
                    maxLines = 1,
                    autoSize = PennyWiseText.fit(PennyWiseText.amountRow)
                )
                
                // Transaction type indicator
                when (transaction.transactionType) {
                    TransactionType.CREDIT -> Icon(
                        Icons.Outlined.CreditCard,
                        contentDescription = stringResource(R.string.account_detail_type_credit),
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = amountColor
                    )
                    TransactionType.TRANSFER -> Icon(
                        Icons.Outlined.SwapHoriz,
                        contentDescription = stringResource(R.string.account_detail_type_transfer),
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = amountColor
                    )
                    TransactionType.INVESTMENT -> Icon(
                        Icons.AutoMirrored.Outlined.ShowChart,
                        contentDescription = stringResource(R.string.account_detail_type_investment),
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = amountColor
                    )
                    else -> {}
                }
            }
        }
    }
}

