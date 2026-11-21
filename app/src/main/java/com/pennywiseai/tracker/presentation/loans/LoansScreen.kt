package com.pennywiseai.tracker.presentation.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.WavyMeter
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.math.BigDecimal

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
    val hazeState = remember { HazeState() }

    PennyWiseScaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        customTopBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.loans_title),
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accounts_back))
                    }
                },
                hazeState = hazeState
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
            return@PennyWiseScaffold
        }

        if (uiState.activeLoans.isEmpty() && uiState.settledLoans.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                PennyWiseEmptyState(
                    icon = Icons.Default.SwapHoriz,
                    headline = stringResource(R.string.loans_empty_title),
                    description = stringResource(R.string.loans_empty_description)
                )
            }
            return@PennyWiseScaffold
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
                    }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.loans_settle_up)) }
                },
                dismissButton = {
                    TextButton(onClick = { settleUpPerson = null }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.accounts_action_cancel)) }
                }
            )
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
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
                    Text(
                        stringResource(R.string.loans_section_active),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.xs)
                    )
                }
                personItems(activePeople, expandedPeople, onToggle = ::toggle, onNavigateToLoanDetail, onSettleUp = { settleUpPerson = it })
            }

            if (settledPeople.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    TextButton(onClick = { viewModel.toggleShowSettled() }, shapes = ButtonDefaults.shapes()) {
                        Icon(
                            if (uiState.showSettledLoans) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
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
            LoanPersonRow(person = person, expanded = isExpanded, onClick = { onToggle(person.name) })
        }
        if (isExpanded) {
            itemsIndexed(person.loans, key = { _, loan -> "loan-${loan.id}" }) { index, loan ->
                LoanListItem(
                    loan = loan,
                    position = ListItemPosition.from(index, person.loans.size),
                    onClick = { onNavigateToLoanDetail(loan.id) },
                    modifier = Modifier.animateItem().padding(start = Spacing.lg)
                )
            }
            if (person.hasActive) {
                item(key = "settle-${person.name}") {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        FilledTonalButton(onClick = { onSettleUp(person) }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.loans_settle_up))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoanPersonRow(
    person: LoanPerson,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val owedColor = MaterialTheme.colorScheme.loan
    val oweColor = MaterialTheme.colorScheme.income
    // Tint the avatar by the first open balance's direction; grey once all settled.
    val accent = person.net.values.firstOrNull()?.let { if (it.isOwedToYou) owedColor else oweColor }
        ?: MaterialTheme.colorScheme.onSurfaceVariant

    GroupedRow(position = ListItemPosition.Single, onClick = onClick) {
        Box(
            modifier = Modifier
                .size(Dimensions.Icon.avatar)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = person.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.inkOnWash(accent, 0.15f)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(Spacing.xs))
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
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (money.isOwedToYou) owedColor else oweColor
                    )
                }
            }
        }
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoanSummaryCard(
    totalLent: BigDecimal,
    totalBorrowed: BigDecimal,
    currency: String
) {
    val lentColor = MaterialTheme.colorScheme.loan
    val borrowedColor = MaterialTheme.colorScheme.income

    PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.loans_owed_to_you), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    CurrencyFormatter.formatCurrency(totalLent, currency),
                    style = PennyWiseText.amountMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = lentColor
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.loans_you_owe), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    CurrencyFormatter.formatCurrency(totalBorrowed, currency),
                    style = PennyWiseText.amountMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = borrowedColor
                )
            }
        }
    }
}

@Composable
fun LoanListItem(
    loan: LoanEntity,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    position: ListItemPosition = ListItemPosition.Single
) {
    val directionColor = if (loan.direction == LoanDirection.LENT) {
        MaterialTheme.colorScheme.loan
    } else {
        MaterialTheme.colorScheme.income
    }
    val isSettled = loan.status == LoanStatus.SETTLED
    val progress = if (loan.originalAmount.signum() > 0) {
        (BigDecimal.ONE - loan.remainingAmount.divide(loan.originalAmount, 2, java.math.RoundingMode.HALF_UP))
            .toFloat().coerceIn(0f, 1f)
    } else 0f

    GroupedRow(position = position, onClick = onClick, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(Dimensions.Icon.avatar)
                .clip(CircleShape)
                .background(directionColor.copy(alpha = Dimensions.Alpha.categoryTint)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = loan.personName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.inkOnWash(directionColor, Dimensions.Alpha.categoryTint)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(loan.personName, style = PennyWiseText.rowTitle)
            Text(
                text = CurrencyFormatter.formatCurrency(
                    if (isSettled) loan.originalAmount else loan.remainingAmount,
                    loan.currency
                ),
                style = PennyWiseText.amountRow,
                color = if (isSettled) MaterialTheme.colorScheme.onSurfaceVariant else directionColor
            )
            val direction = stringResource(
                if (loan.direction == LoanDirection.LENT) R.string.loans_direction_lent else R.string.loans_direction_borrowed
            )
            val status = if (isSettled) stringResource(R.string.loans_status_settled) else stringResource(
                R.string.loans_of_original,
                CurrencyFormatter.formatCurrency(loan.originalAmount, loan.currency)
            )
            Text("$direction · $status", style = PennyWiseText.rowSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (loan.status == LoanStatus.ACTIVE && !AmountPrivacy.hidden) {
                WavyMeter(
                    fraction = progress,
                    modifier = Modifier.heightIn(min = Dimensions.Component.progressBarHeight),
                    color = MaterialTheme.colorScheme.income,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    dense = true
                )
            }
        }
    }
}
