package com.pennywiseai.tracker.presentation.loans

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = "Loans",
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (uiState.activeLoans.isEmpty() && uiState.settledLoans.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.extraLarge),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Text(
                        "No loans yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        "Mark a transaction as \"Lent\" or \"Borrowed\" to start tracking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            return@Scaffold
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

            // Active loans
            if (uiState.activeLoans.isNotEmpty()) {
                item {
                    Text(
                        "Active",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.xs)
                    )
                }
                items(uiState.activeLoans, key = { it.id }) { loan ->
                    LoanListItem(loan = loan, onClick = { onNavigateToLoanDetail(loan.id) })
                }
            }

            // Settled loans toggle
            if (uiState.settledLoans.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    TextButton(onClick = { viewModel.toggleShowSettled() }) {
                        Icon(
                            if (uiState.showSettledLoans) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Settled (${uiState.settledLoans.size})")
                    }
                }
                if (uiState.showSettledLoans) {
                    items(uiState.settledLoans, key = { it.id }) { loan ->
                        LoanListItem(loan = loan, onClick = { onNavigateToLoanDetail(loan.id) })
                    }
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
    val isDark = isSystemInDarkTheme()
    val lentColor = if (isDark) loan_dark else loan_light
    val borrowedColor = if (isDark) income_dark else income_light

    PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Owed to you", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    CurrencyFormatter.formatCurrency(totalLent, currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = lentColor
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("You owe", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    CurrencyFormatter.formatCurrency(totalBorrowed, currency),
                    style = MaterialTheme.typography.titleMedium,
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
    onClick: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val directionColor = if (loan.direction == LoanDirection.LENT) {
        if (isDark) loan_dark else loan_light
    } else {
        if (isDark) income_dark else income_light
    }
    val progressColor = if (isDark) income_dark else income_light
    val progress = if (loan.originalAmount > BigDecimal.ZERO) {
        (BigDecimal.ONE - loan.remainingAmount.divide(loan.originalAmount, 2, java.math.RoundingMode.HALF_UP))
            .toFloat().coerceIn(0f, 1f)
    } else 0f

    PennyWiseCardV2(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Person initial avatar — colored by direction
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(directionColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = loan.personName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = directionColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        loan.personName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        CurrencyFormatter.formatCurrency(loan.remainingAmount, loan.currency),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (loan.status == LoanStatus.SETTLED)
                            MaterialTheme.colorScheme.onSurfaceVariant else directionColor
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xs))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (loan.direction == LoanDirection.LENT) "Lent" else "Borrowed",
                        style = MaterialTheme.typography.labelSmall,
                        color = directionColor
                    )
                    if (loan.status == LoanStatus.SETTLED) {
                        Text(
                            "Settled",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "of ${CurrencyFormatter.formatCurrency(loan.originalAmount, loan.currency)}",
                            style = MaterialTheme.typography.labelSmall,
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
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
