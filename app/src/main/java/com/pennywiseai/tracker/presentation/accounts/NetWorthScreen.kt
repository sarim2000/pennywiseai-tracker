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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.domain.spending.NetWorthGroup
import com.pennywiseai.tracker.domain.spending.NetWorthTotal
import com.pennywiseai.tracker.domain.spending.netWorthGroup
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter

/**
 * What you own minus what you owe, one currency at a time: credit cards and loan
 * accounts are taken off what the other accounts hold.
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
    onAccountClick: (String, String) -> Unit,
) {
    val sections = listOf(
        NetWorthGroup.ASSETS to R.string.net_worth_assets,
        NetWorthGroup.CARDS to R.string.manage_accounts_section_credit_cards,
        NetWorthGroup.LOANS to R.string.manage_accounts_section_loan_accounts,
    ).mapNotNull { (group, title) ->
        state.accounts.filter { it.netWorthGroup == group }.takeIf { it.isNotEmpty() }?.let { group to (title to it) }
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
            item(key = "summary") { SummaryCard(total) }
        }
        sections.forEach { (group, section) ->
            val (title, accounts) = section
            item(key = "header_${group.name}") {
                SectionHeaderV2(title = stringResource(title), modifier = Modifier.animateItem())
            }
            items(accounts, key = { "account_${it.bankName}_${it.accountLast4}" }) { account ->
                AccountLine(
                    account,
                    onClick = { onAccountClick(account.bankName, account.accountLast4) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/** The one number, then what it's made of. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(total: NetWorthTotal) {
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
    }
}

/** An account and what it adds to net worth, or takes off it. */
@Composable
private fun AccountLine(account: AccountBalanceEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val signed = if (account.isLiability) account.balance.negate() else account.balance
    PennyWiseCardV2(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = account.displayLabel,
                style = PennyWiseText.rowTitle,
                // Two lines, so a large font still shows the digits that tell two accounts apart.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = CurrencyFormatter.formatCurrency(signed, account.currency),
                style = PennyWiseText.amountRow,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = PennyWiseText.amountRow, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
