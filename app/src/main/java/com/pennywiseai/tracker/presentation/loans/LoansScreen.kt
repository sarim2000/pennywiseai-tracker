package com.pennywiseai.tracker.presentation.loans

import com.pennywiseai.tracker.ui.components.cards.StatementLine
import com.pennywiseai.tracker.ui.components.cards.ledgerRule
import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import java.math.BigDecimal

/**
 * Money owed to you is on its way in, so it wears income's colour. Money you owe
 * is plain ink: it's a fact, not an alarm. One rule for the summary, rows and detail.
 */
internal fun ColorScheme.loanAmount(owedToYou: Boolean): Color = if (owedToYou) income else onSurface

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoansScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToLoanDetail: (Long) -> Unit = {},
    viewModel: LoansViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.loans_title),
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

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
            return@Scaffold
        }

        if (uiState.activeLoans.isEmpty() && uiState.settledLoans.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                PennyWiseEmptyState(
                    icon = Icons.Outlined.SwapHoriz,
                    headline = stringResource(R.string.loans_empty_title),
                    description = stringResource(R.string.loans_empty_description)
                )
            }
            return@Scaffold
        }

        var expandedPeople by remember { mutableStateOf(setOf<String>()) }
        fun toggle(name: String) {
            expandedPeople = if (name in expandedPeople) expandedPeople - name else expandedPeople + name
        }
        var settleUpPerson by remember { mutableStateOf<LoanPerson?>(null) }
        settleUpPerson?.let { person ->
            AlertDialog(
                onDismissRequest = { settleUpPerson = null },
                title = { Text(stringResource(R.string.loans_settle_up_title, person.name)) },
                text = { Text(stringResource(R.string.loans_settle_up_message, person.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.settleUp(person)
                        settleUpPerson = null
                    }, shapes = pennyWiseButtonShapes()) { Text(stringResource(R.string.loans_settle_up)) }
                },
                dismissButton = {
                    TextButton(onClick = { settleUpPerson = null }, shapes = pennyWiseButtonShapes()) { Text(stringResource(R.string.accounts_action_cancel)) }
                }
            )
        }

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
                bottom = paddingValues.calculateBottomPadding() + Spacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
        ) {
            // Summary card
            item {
                LoanSummaryCard(
                    totalLent = uiState.totalLentRemaining,
                    totalBorrowed = uiState.totalBorrowedRemaining,
                    currency = uiState.summaryCurrency
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            // One row per person; tap to see their loans and settle up.
            val activePeople = uiState.people.filter { it.hasActive }
            val settledPeople = uiState.people.filterNot { it.hasActive }

            if (activePeople.isNotEmpty()) {
                item {
                    SectionHeaderV2(title = stringResource(R.string.loans_section_active), topSpacing = Spacing.none)
                }
                personItems(activePeople, expandedPeople, onToggle = ::toggle, onNavigateToLoanDetail, onSettleUp = { settleUpPerson = it })
            }

            if (settledPeople.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    TextButton(onClick = { viewModel.toggleShowSettled() }, shapes = pennyWiseButtonShapes()) {
                        Icon(
                            if (uiState.showSettledLoans) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.loans_settled_toggle, settledPeople.size))
                    }
                }
                if (uiState.showSettledLoans) {
                    personItems(settledPeople, expandedPeople, onToggle = ::toggle, onNavigateToLoanDetail, onSettleUp = {})
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.personItems(
    people: List<LoanPerson>,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    onNavigateToLoanDetail: (Long) -> Unit,
    onSettleUp: (LoanPerson) -> Unit
) {
    people.forEach { person ->
        val isExpanded = person.name in expanded
        item(key = "person-${person.name}") {
            LoanPersonRow(person = person, expanded = isExpanded, onClick = { onToggle(person.name) }, onSettleUp = { onSettleUp(person) })
        }
        if (isExpanded) {
            items(person.loans, key = { "loan-${it.id}" }) { loan ->
                LoanListItem(
                    loan = loan,
                    onClick = { onNavigateToLoanDetail(loan.id) },
                    modifier = Modifier.padding(start = Spacing.lg)
                )
            }
        }
    }
}

@Composable
private fun LoanPersonRow(
    person: LoanPerson,
    expanded: Boolean,
    onClick: () -> Unit,
    onSettleUp: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().ledgerRule(),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
      Column(Modifier.fillMaxWidth().padding(vertical = Spacing.smd), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(Dimensions.Icon.avatar)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = person.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(person.name, style = PennyWiseText.rowTitle)
                Spacer(modifier = Modifier.height(Spacing.xxs))
                if (person.net.isEmpty()) {
                    // Empty net with open loans = lent and borrowed cancel out.
                    Text(
                        stringResource(if (person.hasActive) R.string.loans_person_even else R.string.loans_person_all_settled),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // One line per currency — balances in different currencies are never added.
                    person.net.values.forEach { money ->
                        val formatted = CurrencyFormatter.formatCurrency(money.amount.abs(), money.currency)
                        Text(
                            if (money.isOwedToYou) stringResource(R.string.loans_person_owes_you, formatted)
                            else stringResource(R.string.loans_person_you_owe, formatted),
                            style = PennyWiseText.amountSmall,
                            color = MaterialTheme.colorScheme.loanAmount(money.isOwedToYou)
                        )
                    }
                }
            }
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded && person.hasActive) {
            FilledTonalButton(
                onClick = onSettleUp,
                modifier = Modifier.align(Alignment.End),
                shapes = pennyWiseButtonShapes()
            ) {
                Text(stringResource(R.string.loans_settle_up))
            }
        }
      }
    }
}

@Composable
private fun LoanSummaryCard(
    totalLent: BigDecimal,
    totalBorrowed: BigDecimal,
    currency: String
) {
    Column(Modifier.fillMaxWidth()) {
        StatementLine(
            label = stringResource(R.string.loans_owed_to_you),
            value = CurrencyFormatter.formatCurrency(totalLent, currency),
            valueColor = MaterialTheme.colorScheme.loanAmount(owedToYou = true),
        )
        StatementLine(
            label = stringResource(R.string.loans_you_owe),
            value = CurrencyFormatter.formatCurrency(totalBorrowed, currency),
            valueColor = MaterialTheme.colorScheme.loanAmount(owedToYou = false),
        )
    }
}

/**
 * One loan under its person's expanded row. The person is the parent row's name, so this
 * says how the loan differs from their others: direction, when, and any note.
 */
@Composable
fun LoanListItem(
    loan: LoanEntity,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val progress = if (loan.originalAmount > BigDecimal.ZERO) {
        (BigDecimal.ONE - loan.remainingAmount.divide(loan.originalAmount, 2, java.math.RoundingMode.HALF_UP))
            .toFloat().coerceIn(0f, 1f)
    } else 0f

    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // The amount drops below the direction at a large font rather than crushing it.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    if (loan.direction == LoanDirection.LENT) stringResource(R.string.loans_direction_lent) else stringResource(R.string.loans_direction_borrowed),
                    style = PennyWiseText.rowTitle,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = Spacing.xxxl)
                )
                // Settled loans show what the loan was; ₹0 remaining says nothing.
                Text(
                    CurrencyFormatter.formatCurrency(
                        if (loan.status == LoanStatus.SETTLED) loan.originalAmount else loan.remainingAmount,
                        loan.currency
                    ),
                    style = PennyWiseText.amountRow,
                    color = if (loan.status == LoanStatus.SETTLED) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.loanAmount(loan.direction == LoanDirection.LENT)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.End),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                val date = loan.createdAt.format(AppDates.dateFormatter())
                Text(
                    // A note can quote a figure, so it hides along with the amounts.
                    loan.note?.takeIf { it.isNotBlank() }
                        ?.let { "$date · ${CurrencyFormatter.maskAmountsIn(it)}" }
                        ?: date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = Spacing.xxxl)
                )
                if (loan.status == LoanStatus.SETTLED) {
                    Text(
                        stringResource(R.string.loans_status_settled),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        stringResource(R.string.loan_detail_remaining_of, CurrencyFormatter.formatCurrency(loan.originalAmount, loan.currency)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (loan.status == LoanStatus.ACTIVE) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Spacing.xs)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    // Fills as it's repaid. A stop dot at the end made an unpaid loan's
                    // empty track look like a full bar.
                    drawStopIndicator = {}
                )
            }
        }
    }
}
