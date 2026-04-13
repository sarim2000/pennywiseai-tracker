package com.pennywiseai.tracker.presentation.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanDetailScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToTransactionDetail: (Long) -> Unit = {},
    viewModel: LoanDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) onNavigateBack()
    }

    val loan = uiState.loan

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = loan?.personName ?: "Loan",
                hasBackButton = true,
                hasActionButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actionContent = {
                    if (loan != null) {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (loan.status == LoanStatus.ACTIVE) {
                                DropdownMenuItem(
                                    text = { Text("Set expected return") },
                                    onClick = { showMenu = false; viewModel.showEditAmountDialog() },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settle") },
                                    onClick = { showMenu = false; viewModel.showSettleDialog() },
                                    leadingIcon = { Icon(Icons.Default.CheckCircle, null) }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Reopen") },
                                    onClick = { showMenu = false; viewModel.reopenLoan() },
                                    leadingIcon = { Icon(Icons.Default.Refresh, null) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; viewModel.showDeleteDialog() },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                },
                hazeState = hazeState
            )
        },
        floatingActionButton = {
            if (loan?.status == LoanStatus.ACTIVE) {
                FloatingActionButton(
                    onClick = { viewModel.showRecordPayment() },
                    containerColor = if (isSystemInDarkTheme()) loan_dark else loan_light
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Record Payment")
                }
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading || loan == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

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

        val lazyListState = rememberLazyListState()

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
                bottom = paddingValues.calculateBottomPadding() + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
        ) {
            // Hero card — compact layout
            item {
                PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        // Top row: avatar + name + direction badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(directionColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    loan.personName.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = directionColor
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    loan.personName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (loan.direction == LoanDirection.LENT) "Lent" else "Borrowed",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = directionColor
                                )
                            }
                        }

                        // Amount
                        Text(
                            text = CurrencyFormatter.formatCurrency(loan.remainingAmount, loan.currency),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (loan.status == LoanStatus.SETTLED)
                                MaterialTheme.colorScheme.onSurfaceVariant else directionColor
                        )
                        Text(
                            text = if (loan.status == LoanStatus.SETTLED) "Settled"
                            else "remaining of ${CurrencyFormatter.formatCurrency(loan.originalAmount, loan.currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Progress bar
                        if (loan.status == LoanStatus.ACTIVE) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = progressColor,
                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                "${(progress * 100).toInt()}% repaid",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Transaction history
            if (uiState.linkedTransactions.isNotEmpty()) {
                item {
                    Text(
                        "History",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(uiState.linkedTransactions, key = { it.id }) { txn ->
                    // Original transaction type matches loan direction (LENT→EXPENSE, BORROWED→INCOME)
                    val isOriginal = if (loan.direction == LoanDirection.LENT)
                        txn.transactionType == TransactionType.EXPENSE
                    else txn.transactionType == TransactionType.INCOME
                    LoanTransactionItem(
                        transaction = txn,
                        isOriginal = isOriginal,
                        loanDirection = loan.direction,
                        onClick = { onNavigateToTransactionDetail(txn.id) }
                    )
                }
            }
        }
    }

    // Settle dialog
    if (uiState.showSettleDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideSettleDialog() },
            title = { Text("Settle Loan") },
            text = { Text("Mark this loan as settled? Any remaining balance will be forgiven.") },
            confirmButton = {
                TextButton(onClick = { viewModel.settleLoan() }) {
                    Text("Settle")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideSettleDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit amount dialog
    if (uiState.showEditAmountDialog && loan != null) {
        var editAmount by remember { mutableStateOf(loan.originalAmount.toPlainString()) }
        AlertDialog(
            onDismissRequest = { viewModel.hideEditAmountDialog() },
            title = { Text("Expected Return") },
            text = {
                TextField(
                    value = editAmount,
                    onValueChange = { value ->
                        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                            editAmount = value
                        }
                    },
                    label = { Text("Amount expected back") },
                    prefix = { Text(CurrencyFormatter.getCurrencySymbol(loan.currency)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editAmount.toBigDecimalOrNull()?.let { viewModel.updateLoanAmount(it) }
                    },
                    enabled = editAmount.toBigDecimalOrNull()?.let { it > java.math.BigDecimal.ZERO } == true
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideEditAmountDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete dialog
    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            title = { Text("Delete Loan") },
            text = { Text("Delete this loan? Linked transactions will be unlinked but not deleted.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteLoan() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Record Payment bottom sheet
    if (uiState.showRecordPaymentSheet && loan != null) {
        RecordPaymentBottomSheet(
            personName = loan.personName,
            remainingAmount = loan.remainingAmount,
            currency = loan.currency,
            recentUnlinkedTransactions = uiState.recentUnlinkedTransactions,
            onDismiss = { viewModel.hideRecordPayment() },
            onLinkTransaction = { viewModel.linkTransactionAsRepayment(it) },
            onManualPayment = { viewModel.recordManualRepayment(it) }
        )
    }
}

@Composable
private fun LoanTransactionItem(
    transaction: TransactionEntity,
    isOriginal: Boolean,
    loanDirection: LoanDirection,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    // Color reflects the actual money flow: red = money out, green = money in
    val color = when (transaction.transactionType) {
        TransactionType.EXPENSE -> if (isDark) expense_dark else expense_light
        TransactionType.INCOME -> if (isDark) income_dark else income_light
        else -> MaterialTheme.colorScheme.onSurface
    }
    val sign = when (transaction.transactionType) {
        TransactionType.EXPENSE -> "-"
        TransactionType.INCOME -> "+"
        else -> ""
    }

    PennyWiseCardV2(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        transaction.merchantName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (isOriginal) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Original", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp),
                            border = null,
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
                Text(
                    transaction.dateTime.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${sign}${CurrencyFormatter.formatCurrency(transaction.amount, transaction.currency)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordPaymentBottomSheet(
    personName: String,
    remainingAmount: BigDecimal,
    currency: String,
    recentUnlinkedTransactions: List<TransactionEntity>,
    onDismiss: () -> Unit,
    onLinkTransaction: (Long) -> Unit,
    onManualPayment: (BigDecimal) -> Unit
) {
    var manualAmount by remember { mutableStateOf("") }
    val isDark = isSystemInDarkTheme()
    val loanColor = if (isDark) loan_dark else loan_light
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Spacing.xl)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                "Record payment from $personName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${CurrencyFormatter.formatCurrency(remainingAmount, currency)} remaining",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Link existing transactions
            if (recentUnlinkedTransactions.isNotEmpty()) {
                Text(
                    "Link existing transaction",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                recentUnlinkedTransactions.take(5).forEach { txn ->
                    PennyWiseCardV2(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onLinkTransaction(txn.id) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(txn.merchantName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    txn.dateTime.format(DateTimeFormatter.ofPattern("d MMM")),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                CurrencyFormatter.formatCurrency(txn.amount, txn.currency),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
            }

            // Manual entry
            Text(
                if (recentUnlinkedTransactions.isNotEmpty()) "Or enter manually" else "Enter amount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = manualAmount,
                    onValueChange = { value ->
                        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                            manualAmount = value
                        }
                    },
                    label = { Text("Amount") },
                    prefix = { Text(CurrencyFormatter.getCurrencySymbol(currency)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Button(
                    onClick = {
                        manualAmount.toBigDecimalOrNull()?.let { onManualPayment(it) }
                    },
                    enabled = manualAmount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true,
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    colors = ButtonDefaults.buttonColors(containerColor = loanColor)
                ) {
                    Text("Add")
                }
            }
        }
    }
}
