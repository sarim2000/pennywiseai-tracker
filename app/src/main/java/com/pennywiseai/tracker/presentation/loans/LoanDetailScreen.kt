package com.pennywiseai.tracker.presentation.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.loan.LoanMath
import com.pennywiseai.tracker.domain.loan.LoanOverflow
import com.pennywiseai.tracker.ui.components.AmountBarDialogHost
import com.pennywiseai.tracker.ui.components.AmountBarHost
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.LoanOverflowDialog
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.RoundedField
import com.pennywiseai.tracker.ui.components.WavyMeter
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.orSupporting
import com.pennywiseai.tracker.ui.components.rememberAmountEntry
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.spending
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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

    PennyWiseScaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        customTopBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = loan?.personName ?: stringResource(R.string.loan_detail_title_fallback),
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accounts_back))
                    }
                },
                actionContent = {
                    if (loan != null) {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.loan_detail_more))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, shape = MaterialTheme.shapes.large) {
                            if (loan.status == LoanStatus.ACTIVE) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.loan_detail_menu_set_expected_return)) },
                                    onClick = { showMenu = false; viewModel.showEditAmountDialog() },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.loan_detail_settle)) },
                                    onClick = { showMenu = false; viewModel.showSettleDialog() },
                                    leadingIcon = { Icon(Icons.Default.CheckCircle, null) }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.loan_detail_menu_reopen)) },
                                    onClick = { showMenu = false; viewModel.reopenLoan() },
                                    leadingIcon = { Icon(Icons.Default.Refresh, null) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.accounts_action_delete), color = MaterialTheme.colorScheme.error) },
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
                    containerColor = MaterialTheme.colorScheme.loan,
                    // contentColorFor() can't infer ink for a custom role.
                    contentColor = MaterialTheme.colorScheme.loan.bestInk()
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.loan_detail_record_payment))
                }
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading || loan == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
            return@PennyWiseScaffold
        }

        val directionColor = if (loan.direction == LoanDirection.LENT) {
            MaterialTheme.colorScheme.loan
        } else {
            MaterialTheme.colorScheme.income
        }
        val progressColor = MaterialTheme.colorScheme.income
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
                bottom = paddingValues.calculateBottomPadding() + Dimensions.Component.fabScrollClearance
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
                                    .size(Dimensions.Icon.avatarLarge)
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
                                    if (loan.direction == LoanDirection.LENT) stringResource(R.string.loans_direction_lent) else stringResource(R.string.loans_direction_borrowed),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = directionColor
                                )
                            }
                        }

                        // Amount
                        Text(
                            text = CurrencyFormatter.formatCurrency(loan.remainingAmount, loan.currency),
                            style = MaterialTheme.typography.headlineMedium.money(),
                            fontWeight = FontWeight.Bold,
                            color = if (loan.status == LoanStatus.SETTLED)
                                MaterialTheme.colorScheme.onSurfaceVariant else directionColor
                        )
                        Text(
                            text = if (loan.status == LoanStatus.SETTLED) stringResource(R.string.loans_status_settled)
                            else stringResource(R.string.loan_detail_remaining_of, CurrencyFormatter.formatCurrency(loan.originalAmount, loan.currency)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // The note captured in "mark as loan" had no home until now
                        // (#754) — it is the only place the user says what the loan
                        // was for, so it sits with the amount rather than below the
                        // transaction history.
                        loan.note?.takeIf { it.isNotBlank() }?.let { note ->
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (loan.status == LoanStatus.ACTIVE && !AmountPrivacy.hidden) {
                            WavyMeter(
                                fraction = progress,
                                modifier = Modifier.heightIn(min = Dimensions.Component.progressBarHeight),
                                color = progressColor,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                    }
                }
            }

            // Transaction history
            if (uiState.linkedTransactions.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.loan_detail_history),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                itemsIndexed(uiState.linkedTransactions, key = { _, txn -> txn.id }) { index, txn ->
                    // Principal flows the loan's way (LENT→money out, BORROWED→money in).
                    val isOriginal = LoanMath.isPrincipal(loan.direction, txn.transactionType)
                    LoanTransactionItem(
                        transaction = txn,
                        isOriginal = isOriginal,
                        position = ListItemPosition.from(index, uiState.linkedTransactions.size),
                        onClick = { onNavigateToTransactionDetail(txn.id) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }

    // Settle dialog
    if (uiState.showSettleDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideSettleDialog() },
            title = { Text(stringResource(R.string.loan_detail_settle_title)) },
            text = { Text(stringResource(R.string.loan_detail_settle_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.settleLoan() }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.loan_detail_settle))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideSettleDialog() }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.accounts_action_cancel))
                }
            }
        )
    }

    // Edit amount dialog
    if (uiState.showEditAmountDialog && loan != null) {
        var editAmount by remember { mutableStateOf(loan.originalAmount.toPlainString()) }
        AlertDialog(
            onDismissRequest = { viewModel.hideEditAmountDialog() },
            title = { Text(stringResource(R.string.loan_detail_expected_return_title)) },
            text = {
                AmountBarDialogHost {
                    val entry = rememberAmountEntry(
                        editAmount,
                        { value ->
                            if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                                editAmount = value
                            }
                        },
                        loan.currency
                    )
                    val sum = entry.sumHint
                    TextField(
                        value = entry.value,
                        onValueChange = entry::onValueChange,
                        label = { Text(stringResource(R.string.loan_detail_expected_return_label)) },
                        prefix = { Text(CurrencyFormatter.getCurrencySymbol(loan.currency)) },
                        isError = sum?.isError == true,
                        supportingText = sum.orSupporting(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = entry.modifier,
                        shape = RoundedField.shape,
                        colors = RoundedField.colors()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editAmount.toBigDecimalOrNull()?.let { viewModel.updateLoanAmount(it) }
                    },
                    enabled = editAmount.toBigDecimalOrNull()?.let { it > java.math.BigDecimal.ZERO } == true,
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.accounts_action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideEditAmountDialog() }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.accounts_action_cancel))
                }
            }
        )
    }

    // Delete dialog
    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            title = { Text(stringResource(R.string.loan_detail_delete_title)) },
            text = { Text(stringResource(R.string.loan_detail_delete_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteLoan() }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.accounts_action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteDialog() }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.accounts_action_cancel))
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

    uiState.overflowPrompt?.let { prompt ->
        LoanOverflowDialog(
            personName = prompt.personName,
            youPaid = prompt.youPaid,
            extra = prompt.extra,
            currency = prompt.currency,
            onCountAsOwn = { viewModel.resolveOverflow(LoanOverflow.COUNT_AS_OWN) },
            onFlipDebt = { viewModel.resolveOverflow(LoanOverflow.FLIP_DEBT) },
            onDismiss = { viewModel.dismissOverflow() }
        )
    }
}

@Composable
private fun LoanTransactionItem(
    transaction: TransactionEntity,
    isOriginal: Boolean,
    position: ListItemPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Sign and colour follow the actual money flow: "+" in the income colour for
    // money in, "-" in the spending colour for money out.
    // Every type but INCOME is money out, as in the loan's own math (#876).
    val moneyIn = transaction.transactionType == TransactionType.INCOME
    val color = if (moneyIn) {
        MaterialTheme.colorScheme.income
    } else {
        MaterialTheme.colorScheme.spending
    }
    val sign = if (moneyIn) "+" else "-"

    GroupedRow(position = position, onClick = onClick, modifier = modifier) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(transaction.merchantName, style = PennyWiseText.rowTitle)
            val date = transaction.dateTime.format(AppDates.dateFormatter())
            val status = if (isOriginal) stringResource(R.string.loan_detail_original_chip) else null
            Text(
                listOfNotNull(date, status).joinToString(" · "),
                style = PennyWiseText.rowSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            transaction.description?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    CurrencyFormatter.maskAmountsIn(note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                CurrencyFormatter.signed(
                    sign,
                    CurrencyFormatter.formatCurrency(transaction.loanContribution ?: transaction.amount, transaction.currency)
                ),
                style = PennyWiseText.amountRow,
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
    val loanColor = MaterialTheme.colorScheme.loan
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        AmountBarHost {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.Padding.content)
                    .padding(bottom = Spacing.xl)
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    stringResource(R.string.loan_detail_record_payment_from, personName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.loan_detail_amount_remaining, CurrencyFormatter.formatCurrency(remainingAmount, currency)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Link existing transactions
                if (recentUnlinkedTransactions.isNotEmpty()) {
                    Text(
                        stringResource(R.string.loan_detail_link_existing),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Every candidate, scrolling in its own window so older ones are reachable (#1308)
                    // and manual entry below stays in view.
                    LazyColumn(
                        modifier = Modifier.heightIn(max = Dimensions.Component.dialogListMaxHeight),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.groupedListGap)
                    ) {
                        itemsIndexed(recentUnlinkedTransactions, key = { _, txn -> txn.id }) { index, txn ->
                            GroupedRow(
                                position = ListItemPosition.from(index, recentUnlinkedTransactions.size),
                                onClick = { onLinkTransaction(txn.id) }
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Text(txn.merchantName, style = PennyWiseText.rowTitle)
                                    Text(
                                        txn.dateTime.format(AppDates.dayMonthFormatter()),
                                        style = PennyWiseText.rowSubtitle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(CurrencyFormatter.formatCurrency(txn.amount, txn.currency), style = PennyWiseText.amountRow)
                                }
                            }
                        }
                    }
                }

                // Manual entry
                Text(
                    if (recentUnlinkedTransactions.isNotEmpty()) stringResource(R.string.loan_detail_or_enter_manually) else stringResource(R.string.loan_detail_enter_amount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val entry = rememberAmountEntry(
                        manualAmount,
                        { value ->
                            if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                                manualAmount = value
                            }
                        },
                        currency
                    )
                    val sum = entry.sumHint
                    TextField(
                        value = entry.value,
                        onValueChange = entry::onValueChange,
                        label = { Text(stringResource(R.string.loan_detail_amount_label)) },
                        prefix = { Text(CurrencyFormatter.getCurrencySymbol(currency)) },
                        isError = sum?.isError == true,
                        supportingText = sum.orSupporting(),
                        singleLine = true,
                        modifier = Modifier.weight(1f).then(entry.modifier),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedField.shape,
                        colors = RoundedField.colors()
                    )
                    Button(
                        onClick = {
                            manualAmount.toBigDecimalOrNull()?.let { onManualPayment(it) }
                        },
                        enabled = manualAmount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true,
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(containerColor = loanColor)
                    ) {
                        Text(stringResource(R.string.loan_detail_add))
                    }
                }
            }
        }
    }
}
