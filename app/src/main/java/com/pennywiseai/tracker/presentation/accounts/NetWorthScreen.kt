package com.pennywiseai.tracker.presentation.accounts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.spending.BalanceSource
import com.pennywiseai.tracker.domain.spending.NetWorthAccount
import com.pennywiseai.tracker.domain.spending.NetWorthGroup
import com.pennywiseai.tracker.domain.spending.NetWorthTotal
import com.pennywiseai.tracker.domain.spending.PeopleOwed
import com.pennywiseai.tracker.ui.components.BalanceChart
import com.pennywiseai.tracker.ui.components.BalancePoint
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.warning
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * What you own minus what you owe, one currency at a time, with where each
 * account's balance comes from and how it moved.
 */
@Composable
fun NetWorthScreen(
    onNavigateBack: () -> Unit,
    onAccountClick: (bankName: String, accountLast4: String) -> Unit,
    viewModel: NetWorthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PennyWiseScaffold(
        title = stringResource(R.string.net_worth_title),
        navigationIcon = {
            IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.net_worth_back))
            }
        },
    ) { padding ->
        val loaded = state
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                loaded == null -> LoadingIndicator(Modifier.align(Alignment.Center))
                loaded.isEmpty -> PennyWiseEmptyState(
                    icon = Icons.Default.AccountBalance,
                    headline = stringResource(R.string.net_worth_empty_headline),
                    description = stringResource(R.string.net_worth_empty_body),
                )
                else -> NetWorthContent(
                    state = loaded,
                    onSelectCurrency = viewModel::selectCurrency,
                    onSelectRange = viewModel::selectRange,
                    onAccountClick = onAccountClick,
                )
            }
        }
    }
}

@Composable
private fun NetWorthContent(
    state: NetWorthUiState,
    onSelectCurrency: (String) -> Unit,
    onSelectRange: (NetWorthRange) -> Unit,
    onAccountClick: (String, String) -> Unit,
) {
    val sections = listOf(
        NetWorthGroup.ASSETS to R.string.net_worth_assets,
        NetWorthGroup.CARDS to R.string.manage_accounts_section_credit_cards,
        NetWorthGroup.LOANS to R.string.manage_accounts_section_loan_accounts,
    ).mapNotNull { (group, title) ->
        state.accounts.filter { it.group == group }.takeIf { it.isNotEmpty() }?.let { title to it }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = Dimensions.Padding.content,
            end = Dimensions.Padding.content,
            bottom = Spacing.Layout.scrollBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (state.currencies.size > 1 && state.currency != null) {
            item(key = "currencies") {
                ConnectedChoiceRow(
                    options = state.currencies,
                    selected = state.currency,
                    onSelect = onSelectCurrency,
                    label = { it },
                    fillMaxWidth = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        }
        state.total?.let { total ->
            item(key = "summary") { SummaryCard(total, state.peopleOwed) }
            item(key = "trend") { TrendCard(state, total.currency, onSelectRange) }
        }
        sections.forEach { (title, accounts) ->
            item(key = "header_$title") { SectionHeaderV2(title = stringResource(title)) }
            items(accounts, key = { "${it.account.bankName}_${it.account.accountLast4}" }) { line ->
                AccountLine(line, onClick = { onAccountClick(line.account.bankName, line.account.accountLast4) })
            }
        }
    }
}

/** The one number, then what it's made of. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(total: NetWorthTotal, peopleOwed: PeopleOwed?) {
    val currency = total.currency
    PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.net_worth_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = CurrencyFormatter.formatCurrency(total.net, currency),
            style = PennyWiseText.amountLarge,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = PennyWiseText.autoSizeFloor,
                maxFontSize = PennyWiseText.amountLarge.fontSize,
            ),
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text(
            text = stringResource(R.string.net_worth_summary_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Figure(stringResource(R.string.net_worth_assets), CurrencyFormatter.formatCurrency(total.assets, currency))
            if (total.cards.signum() != 0) {
                Figure(stringResource(R.string.net_worth_cards), CurrencyFormatter.formatCurrency(total.cards.negate(), currency))
            }
            if (total.loans.signum() != 0) {
                Figure(stringResource(R.string.net_worth_loans), CurrencyFormatter.formatCurrency(total.loans.negate(), currency))
            }
        }
        if (total.hasEstimates) {
            Note(stringResource(R.string.net_worth_includes_estimates))
        }
        peopleOwed?.let { Note(peopleOwedText(it)) }
    }
}

@Composable
private fun peopleOwedText(owed: PeopleOwed): String {
    val toYou = CurrencyFormatter.formatCurrency(owed.owedToYou, owed.currency)
    val byYou = CurrencyFormatter.formatCurrency(owed.youOwe, owed.currency)
    return when {
        owed.youOwe.signum() == 0 -> stringResource(R.string.net_worth_people_owe_you, toYou)
        owed.owedToYou.signum() == 0 -> stringResource(R.string.net_worth_you_owe_people, byYou)
        else -> stringResource(R.string.net_worth_people_both, toYou, byYou)
    }
}

@Composable
private fun TrendCard(state: NetWorthUiState, currency: String, onSelectRange: (NetWorthRange) -> Unit) {
    PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.net_worth_trend),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        ConnectedChoiceRow(
            options = NetWorthRange.entries,
            selected = state.range,
            onSelect = onSelectRange,
            label = { stringResource(it.label) },
            spokenLabel = { stringResource(it.spokenLabel) },
            modifier = Modifier.padding(top = Spacing.sm),
        )
        val trend = state.trend
        when {
            trend == null -> Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            trend.isEmpty() -> Note(
                stringResource(if (state.trendWaitsOnCash) R.string.net_worth_trend_empty_cash else R.string.net_worth_trend_empty)
            )
            else -> {
                val points = remember(trend) { trend.map { BalancePoint(it.date.atStartOfDay(), it.net, currency) } }
                val first = trend.first()
                val last = trend.last()
                val description = stringResource(
                    R.string.net_worth_trend_description,
                    CurrencyFormatter.formatCurrency(first.net, currency),
                    date(first.date.atStartOfDay()),
                    CurrencyFormatter.formatCurrency(last.net, currency),
                    date(last.date.atStartOfDay()),
                )
                Box(Modifier.clearAndSetSemantics { contentDescription = description }) {
                    BalanceChart(
                        primaryCurrency = currency,
                        balanceHistory = points,
                        height = 200,
                        seriesLabel = stringResource(R.string.net_worth_title),
                    )
                }
            }
        }
    }
}

private val NetWorthRange.label: Int
    get() = when (this) {
        NetWorthRange.MONTH -> R.string.net_worth_range_month
        NetWorthRange.QUARTER -> R.string.net_worth_range_quarter
        NetWorthRange.HALF_YEAR -> R.string.net_worth_range_half_year
        NetWorthRange.YEAR -> R.string.net_worth_range_year
    }

private val NetWorthRange.spokenLabel: Int
    get() = when (this) {
        NetWorthRange.MONTH -> R.string.net_worth_range_month_spoken
        NetWorthRange.QUARTER -> R.string.net_worth_range_quarter_spoken
        NetWorthRange.HALF_YEAR -> R.string.net_worth_range_half_year_spoken
        NetWorthRange.YEAR -> R.string.net_worth_range_year_spoken
    }

/** An account, what it adds or takes off, and what its balance rests on. */
@Composable
private fun AccountLine(line: NetWorthAccount, onClick: () -> Unit) {
    val account = line.account
    val signed = if (account.isLiability) account.balance.negate() else account.balance
    PennyWiseCardV2(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = account.displayLabel,
                style = PennyWiseText.rowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = CurrencyFormatter.formatCurrency(signed, account.currency),
                style = PennyWiseText.amountRow,
                maxLines = 1,
            )
        }
        sourceText(line)?.let {
            Text(
                text = it,
                style = PennyWiseText.metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
        if (line.isStale) {
            Text(
                text = pluralStringResource(R.plurals.net_worth_stale, line.daysSinceUpdate.toInt(), line.daysSinceUpdate.toInt()),
                style = PennyWiseText.metadata,
                color = MaterialTheme.colorScheme.warning,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
    }
}

@Composable
private fun sourceText(line: NetWorthAccount): String? {
    val claim = when (val source = line.source) {
        is BalanceSource.Entered -> stringResource(
            if (source.fromStatement) R.string.net_worth_source_statement else R.string.net_worth_source_entered,
            CurrencyFormatter.formatCurrency(source.amount, line.account.currency),
            date(source.at),
        )
        is BalanceSource.Reported -> stringResource(R.string.net_worth_source_reported, date(source.at))
        BalanceSource.Estimated -> stringResource(R.string.net_worth_source_estimated)
        null -> return null
    }
    if (line.transactionsSince == 0) return claim
    return stringResource(
        R.string.net_worth_source_with_since,
        claim,
        pluralStringResource(R.plurals.net_worth_source_since, line.transactionsSince, line.transactionsSince),
    )
}

private fun date(at: LocalDateTime): String =
    at.format(if (at.year == LocalDate.now().year) AppDates.dayMonthFormatter() else AppDates.dateFormatter())

@Composable
private fun Figure(label: String, value: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = PennyWiseText.amountRow, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}
