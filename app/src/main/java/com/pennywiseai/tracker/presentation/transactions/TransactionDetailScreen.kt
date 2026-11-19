package com.pennywiseai.tracker.presentation.transactions

import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.formatAmount
import com.pennywiseai.tracker.domain.loan.LoanOverflow
import com.pennywiseai.tracker.presentation.goals.AddToGoalSheet
import com.pennywiseai.tracker.presentation.goals.TowardGoalsSection
import com.pennywiseai.tracker.presentation.goals.TransactionGoalsViewModel
import com.pennywiseai.tracker.presentation.split.SplitBillSheet
import com.pennywiseai.tracker.presentation.split.SplitBillViewModel
import com.pennywiseai.tracker.presentation.split.SplitSection
import com.pennywiseai.tracker.presentation.split.canSplit
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.LoanOverflowDialog
import com.pennywiseai.tracker.ui.components.QuickCategoryPickerSheet
import com.pennywiseai.tracker.ui.components.SETTLED_HOLD_MILLIS
import com.pennywiseai.tracker.ui.components.SplitItem
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transactionId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToLoanDetail: (Long) -> Unit = {},
    onDuplicateTransaction: (Long) -> Unit = {},
    onNavigateToGoal: (Long) -> Unit = {},
    onCreateGoal: () -> Unit = {},
    viewModel: TransactionDetailViewModel = hiltViewModel(),
    goalsViewModel: TransactionGoalsViewModel = hiltViewModel(),
    splitViewModel: SplitBillViewModel = hiltViewModel()
) {
    val transaction by viewModel.transaction.collectAsStateWithLifecycle()
    val isEditMode by viewModel.isEditMode.collectAsStateWithLifecycle()
    val editableTransaction by viewModel.editableTransaction.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val saveSuccess by viewModel.saveSuccess.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val applyToAllFromMerchant by viewModel.applyToAllFromMerchant.collectAsStateWithLifecycle()
    val updateExistingTransactions by viewModel.updateExistingTransactions.collectAsStateWithLifecycle()
    val existingTransactionCount by viewModel.existingTransactionCount.collectAsStateWithLifecycle()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsStateWithLifecycle()
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    val deleteSuccess by viewModel.deleteSuccess.collectAsStateWithLifecycle()
    val accountPrimaryCurrency by viewModel.primaryCurrency.collectAsStateWithLifecycle()
    val convertedAmount by viewModel.convertedAmount.collectAsStateWithLifecycle()

    // Split state
    val splits by viewModel.splits.collectAsStateWithLifecycle()
    val showSplitEditor by viewModel.showSplitEditor.collectAsStateWithLifecycle()
    val hasSplits by viewModel.hasSplits.collectAsStateWithLifecycle()

    // Loan state
    val loan by viewModel.loan.collectAsStateWithLifecycle()
    val showMarkAsLoanSheet by viewModel.showMarkAsLoanSheet.collectAsStateWithLifecycle()
    val loanOverflowPrompt by viewModel.loanOverflowPrompt.collectAsStateWithLifecycle()
    val unlinkReversal by viewModel.unlinkReversal.collectAsStateWithLifecycle()
    var showUnmarkLoanConfirm by remember { mutableStateOf(false) }
    val recentPersonNames by viewModel.recentPersonNames.collectAsStateWithLifecycle()
// Account profile state
    val accountProfileId by viewModel.accountProfileId.collectAsStateWithLifecycle()

    // Receipt state
    val receiptUri by viewModel.receiptUri.collectAsStateWithLifecycle()
    val pendingReceiptUri by viewModel.pendingReceiptUri.collectAsStateWithLifecycle()
    val showFullScreenReceipt by viewModel.showFullScreenReceipt.collectAsStateWithLifecycle()

    // Group state
    val currentGroup by viewModel.currentGroup.collectAsStateWithLifecycle()
    val availableGroups by viewModel.availableGroups.collectAsStateWithLifecycle()
    val showGroupSheet by viewModel.showGroupSheet.collectAsStateWithLifecycle()

    // Goals (#914)
    val linkedGoals by goalsViewModel.linked.collectAsStateWithLifecycle()
    val goals by goalsViewModel.goals.collectAsStateWithLifecycle()
    val addToGoal by goalsViewModel.sheet.collectAsStateWithLifecycle()
    var showAddToGoal by rememberSaveable { mutableStateOf(false) }
    var showWhatWasThis by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(transaction) { goalsViewModel.setTransaction(transaction) }
    // Split a bill (#346): reload after a save so lent_shares_total is fresh.
    LaunchedEffect(transaction) { splitViewModel.setTransaction(transaction) }
    LaunchedEffect(Unit) { splitViewModel.changed.collect { viewModel.loadTransaction(transactionId) } }
    fun closeAddToGoal() { showAddToGoal = false; goalsViewModel.closeSheet() }
    // Bumped by each confirmed user action that settles the receipt: Save, Add to goal.
    var settleRequests by remember { mutableIntStateOf(0) }
    LaunchedEffect(addToGoal.done) { if (addToGoal.done) { closeAddToGoal(); settleRequests++ } }
    // Loads what's left of the transaction whenever the sheet is open without it:
    // on open, and after process recreation restores an open sheet (its typed
    // fields are rememberSaveable and survive; the ViewModel's state doesn't).
    LaunchedEffect(showAddToGoal, transaction?.id) {
        if (showAddToGoal && transaction != null && goalsViewModel.sheet.value.left == null) goalsViewModel.openSheet()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Show success snackbar
    val updatedMessage = stringResource(R.string.txn_detail_updated)
    // "Receipt settled": only the user's own Save gets the check + haptic.
    var settled by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            // Consume the save first: rotating mid-snackbar must not replay the check.
            viewModel.clearSaveSuccess()
            scope.launch { snackbarHostState.showSnackbar(updatedMessage) }
            settleRequests++
        }
    }
    LaunchedEffect(settleRequests) {
        if (settleRequests > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            // With "Remove animations" the check is choreography too: haptic only.
            if (!ValueAnimator.areAnimatorsEnabled()) return@LaunchedEffect
            settled = true
            try { delay(SETTLED_HOLD_MILLIS) } finally { settled = false }
        }
    }

    // Show error snackbar
    val errorText = errorMessage?.asString()
    LaunchedEffect(errorText) {
        errorText?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
            }
        }
    }

    // Reload on every resume so external changes — e.g. the linked loan being
    // deleted from the Loan screen — are reflected when returning here (#444).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.loadTransaction(transactionId)
    }

    // Handle delete success
    LaunchedEffect(deleteSuccess) {
        if (deleteSuccess) {
            onNavigateBack()
        }
    }

    val context = LocalContext.current

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    // A one-off confirmation from the view model ("Got it, I'll remember").
    val noticeText = viewModel.notice.collectAsStateWithLifecycle().value?.asString()
    LaunchedEffect(noticeText) {
        noticeText?.let {
            // Launched apart: clearing the notice restarts this effect.
            scope.launch { snackbarHostState.showSnackbar(it) }
            viewModel.clearNotice()
        }
    }
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    val reportMistake: () -> Unit = {
        val reportUrl = viewModel.getReportUrl()
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(reportUrl))) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) { data -> Snackbar(data, shape = MaterialTheme.shapes.large) } },
        // Transaction actions live in the top-bar overflow menu and the rows at
        // the end of the page, not floating buttons: a stack of FABs overlapped
        // the receipt content (#451 follow-up).
        floatingActionButton = {},
        topBar = {
            if (isEditMode) {
                CustomTitleTopAppBar(
                    scrollBehaviorSmall = scrollBehaviorSmall,
                    scrollBehaviorLarge = scrollBehaviorLarge,
                    title = stringResource(R.string.txn_detail_title_edit),
                    hasBackButton = true,
                    navigationContent = {
                        IconButton(onClick = { viewModel.cancelEdit() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.txn_detail_action_cancel))
                        }
                    },
                    actionContent = {
                        TextButton(
                            onClick = { viewModel.saveChanges() },
                            enabled = !isSaving,
                            shapes = ButtonDefaults.shapes()
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(stringResource(R.string.txn_detail_action_save))
                            }
                        }
                    },
                    hazeState = hazeState
                )
            } else {
                DetailTopBar(
                    onBack = onNavigateBack,
                    showActions = transaction != null,
                    onEdit = { viewModel.enterEditMode() },
                    menu = { dismiss ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.txn_detail_menu_add_to_group)) },
                            leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                            onClick = {
                                dismiss()
                                viewModel.showGroupSheet()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.goal_add_to_goal)) },
                            leadingIcon = { Icon(Icons.Outlined.Savings, contentDescription = null) },
                            onClick = {
                                dismiss()
                                showAddToGoal = true
                            }
                        )
                        if (transaction?.let(::canSplit) == true) {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(if (transaction?.lentSharesTotal != null) R.string.split_menu_edit else R.string.split_menu_split))
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null) },
                                onClick = {
                                    dismiss()
                                    splitViewModel.openSheet()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.txn_detail_menu_duplicate)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                dismiss()
                                transaction?.let { onDuplicateTransaction(it.id) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.txn_detail_report_mistake)) },
                            leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                            onClick = {
                                dismiss()
                                reportMistake()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.txn_detail_delete_transaction), color = MaterialTheme.colorScheme.error)
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                dismiss()
                                viewModel.showDeleteDialog()
                            }
                        )
                    }
                )
            }
        }
    ) { paddingValues ->
        val displayTransaction = if (isEditMode) editableTransaction else transaction
        displayTransaction?.let { txn ->
            TransactionDetailContent(
                transaction = txn,
                isEditMode = isEditMode,
                settled = settled,
                applyToAllFromMerchant = applyToAllFromMerchant,
                updateExistingTransactions = updateExistingTransactions,
                existingTransactionCount = existingTransactionCount,
                viewModel = viewModel,
                accountPrimaryCurrency = accountPrimaryCurrency,
                convertedAmount = convertedAmount,
                splits = splits,
                showSplitEditor = showSplitEditor,
                hasSplits = hasSplits == true,
                loan = loan,
                onNavigateToLoanDetail = onNavigateToLoanDetail,
                onUnmarkLoanClick = {
                    viewModel.loadUnlinkReversal()
                    showUnmarkLoanConfirm = true
                },
                accountProfileId = accountProfileId,
                hazeState = hazeState,
                towardGoals = {
                    TowardGoalsSection(linkedGoals, onOpenGoal = onNavigateToGoal, onRemove = goalsViewModel::remove)
                },
                whatWasThis = {
                    if (transaction?.let { whatWasThisOptions(it, loan != null) }?.isNotEmpty() == true) {
                        WhatWasThisRow(onClick = { showWhatWasThis = true })
                    }
                },
                splitWith = {
                    transaction?.let { SplitSection(splitViewModel, it, onOpenLoan = onNavigateToLoanDetail) }
                },
                onCategoryClick = {
                    // A split row's categories live in the editor's split section.
                    // Unknown yet (still loading): do nothing rather than guess.
                    when (hasSplits) {
                        true -> viewModel.enterEditMode()
                        false -> showCategoryPicker = true
                        null -> Unit
                    }
                },
                hasClassificationAction = transaction?.let { whatWasThisOptions(it, loan != null) }?.isNotEmpty() == true,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }

    transaction?.let { SplitBillSheet(splitViewModel, it) }

    if (showCategoryPicker) {
        val categories by viewModel.categories.collectAsStateWithLifecycle()
        val mostUsed by viewModel.mostUsedCategories.collectAsStateWithLifecycle()
        transaction?.let { txn ->
            QuickCategoryPickerSheet(
                transaction = txn,
                categories = categories,
                onCategorySelected = { name ->
                    showCategoryPicker = false
                    viewModel.quickUpdateCategory(name)
                },
                onDismiss = { showCategoryPicker = false },
                mostUsed = mostUsed
            )
        }
    }

    if (showWhatWasThis) {
        transaction?.let { txn ->
            WhatWasThisSheet(
                options = whatWasThisOptions(txn, loan != null),
                onDismiss = { showWhatWasThis = false },
                onPick = { option ->
                    showWhatWasThis = false
                    when (option) {
                        WhatWasThis.LOAN -> viewModel.showMarkAsLoanSheet()
                        WhatWasThis.SPLIT -> splitViewModel.openSheet()
                        // Open the editor with the type set, so the existing save path
                        // handles accounts and balances; the user reviews and saves.
                        WhatWasThis.SAVING -> {
                            viewModel.enterEditMode()
                            viewModel.updateTransactionType(TransactionType.INVESTMENT)
                        }
                        // Same as picking Refund under Budget impact; the user picks the category.
                        WhatWasThis.REFUND -> {
                            viewModel.enterEditMode()
                            viewModel.updateBudgetImpactType(BudgetImpactType.DEDUCT_SPENT)
                        }
                        // Pairs the other leg if it arrived, else opens the editor.
                        WhatWasThis.OWN_TRANSFER -> viewModel.answerOwnTransfer()
                    }
                }
            )
        }
    }

    if (showAddToGoal) {
        transaction?.let { txn ->
            AddToGoalSheet(
                transaction = txn,
                goals = goals,
                state = addToGoal,
                onDismiss = ::closeAddToGoal,
                onSave = goalsViewModel::add,
                onCreateGoal = { closeAddToGoal(); onCreateGoal() },
                onEdited = goalsViewModel::clearError,
            )
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            title = { Text(stringResource(R.string.txn_detail_delete_title)) },
            text = {
                Text(stringResource(R.string.txn_detail_delete_message))
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteTransaction() },
                    enabled = !isDeleting,
                    shapes = ButtonDefaults.shapes()
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Dimensions.Icon.small),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            stringResource(R.string.txn_detail_action_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteDialog() }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.txn_detail_action_cancel))
                }
            }
        )
    }

    // "Between my accounts" found the other leg: confirm it before both rows change.
    val proposedPartner by viewModel.proposedTransferPartner.collectAsStateWithLifecycle()
    proposedPartner?.let { partner ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissTransferPartner() },
            title = { Text(stringResource(R.string.own_transfer_partner_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.own_transfer_partner_message,
                        partner.formatAmount(),
                        partner.merchantName,
                        partner.dateTime.format(com.pennywiseai.tracker.utils.AppDates.dateTimeFormatter())
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmTransferPartner() }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.own_transfer_partner_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.declineTransferPartner() }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.own_transfer_partner_decline))
                }
            }
        )
    }

    // Group Bottom Sheet
    if (showGroupSheet) {
        GroupBottomSheet(
            currentGroup = currentGroup,
            availableGroups = availableGroups,
            onDismiss = { viewModel.hideGroupSheet() },
            onAddToGroup = { groupId -> viewModel.addToGroup(groupId) },
            onRemoveFromGroup = { viewModel.removeFromGroup() },
            onCreateGroup = { name, note -> viewModel.createGroupAndAdd(name, note) }
        )
    }

    // Mark as Loan Bottom Sheet
    if (showMarkAsLoanSheet) {
        val txType = transaction?.transactionType
        val inferredDirection = if (txType == TransactionType.INCOME) LoanDirection.BORROWED else LoanDirection.LENT
        MarkAsLoanBottomSheet(
            transactionAmount = transaction?.amount ?: BigDecimal.ZERO,
            transactionCurrency = transaction?.currency ?: "INR",
            direction = inferredDirection,
            recentPersonNames = recentPersonNames,
            onDismiss = { viewModel.hideMarkAsLoanSheet() },
            onConfirm = { personName, note, loanAmount ->
                viewModel.createLoanFromTransaction(personName, inferredDirection, note, loanAmount)
            }
        )
    }

    // A repayment beyond what's owed: ask what the extra is
    loanOverflowPrompt?.let { prompt ->
        LoanOverflowDialog(
            personName = prompt.personName,
            youPaid = prompt.youPaid,
            extra = prompt.extra,
            currency = prompt.currency,
            onCountAsOwn = { viewModel.resolveLoanOverflow(LoanOverflow.COUNT_AS_OWN) },
            onFlipDebt = { viewModel.resolveLoanOverflow(LoanOverflow.FLIP_DEBT) },
            onDismiss = { viewModel.dismissLoanOverflow() }
        )
    }

    // Unmark-as-loan confirmation (#444)
    if (showUnmarkLoanConfirm) {
        // Unmarking the bill that turned a loan around can turn it back (keep).
        val reversal = unlinkReversal
        val reversalLoan = loan
        val keepText = if (reversal != null && reversalLoan != null) {
            val amount = CurrencyFormatter.formatCurrency(reversal, reversalLoan.currency)
            // The loan turns the other way: a lent loan becomes money you owe.
            if (reversalLoan.direction == LoanDirection.LENT) {
                stringResource(R.string.txn_detail_unmark_loan_keep_you_owe, reversalLoan.personName, amount)
            } else {
                stringResource(R.string.txn_detail_unmark_loan_keep_owes_you, reversalLoan.personName, amount)
            }
        } else {
            null
        }
        AlertDialog(
            onDismissRequest = { showUnmarkLoanConfirm = false },
            title = { Text(stringResource(R.string.txn_detail_unmark_loan_title)) },
            text = {
                Text(
                    if (keepText != null) {
                        stringResource(R.string.txn_detail_unmark_loan_reversal_message)
                    } else {
                        stringResource(R.string.txn_detail_unmark_loan_message)
                    }
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (keepText != null) {
                        TextButton(onClick = {
                            showUnmarkLoanConfirm = false
                            viewModel.unlinkLoan(keepReversal = true)
                        }, shapes = ButtonDefaults.shapes()) { Text(keepText) }
                    }
                    TextButton(onClick = {
                        showUnmarkLoanConfirm = false
                        viewModel.unlinkLoan()
                    }, shapes = ButtonDefaults.shapes()) {
                        Text(
                            stringResource(
                                if (keepText != null) R.string.txn_detail_unmark_loan_only else R.string.txn_detail_unmark_loan_confirm
                            )
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnmarkLoanConfirm = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.txn_detail_action_cancel)) }
            }
        )
    }

    // Full-screen Receipt Dialog
    if (showFullScreenReceipt && receiptUri != null) {
        Dialog(
            onDismissRequest = { viewModel.hideFullScreenReceipt() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = receiptUri,
                    contentDescription = stringResource(R.string.txn_detail_receipt_full_screen),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.md),
                    contentScale = ContentScale.Fit
                )
                FilledIconButton(
                    onClick = { viewModel.hideFullScreenReceipt() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.md)
                        .statusBarsPadding(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White
                    ),
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.txn_detail_close))
                }
            }
        }
    }
}

@Composable
private fun TransactionDetailContent(
    transaction: TransactionEntity,
    isEditMode: Boolean,
    settled: Boolean,
    applyToAllFromMerchant: Boolean,
    updateExistingTransactions: Boolean,
    existingTransactionCount: Int,
    viewModel: TransactionDetailViewModel,
    accountPrimaryCurrency: String,
    convertedAmount: BigDecimal?,
    splits: List<SplitItem>,
    showSplitEditor: Boolean,
    hasSplits: Boolean,
    loan: LoanEntity?,
    onNavigateToLoanDetail: (Long) -> Unit,
    onUnmarkLoanClick: () -> Unit,
    accountProfileId: Long?,
    hazeState: HazeState,
    towardGoals: @Composable () -> Unit,
    splitWith: @Composable () -> Unit,
    whatWasThis: @Composable () -> Unit,
    onCategoryClick: () -> Unit,
    hasClassificationAction: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    // Bring the avatar back into view so the user actually sees the check.
    val scrollSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(settled) {
        if (settled) scrollState.animateScrollTo(0, scrollSpec)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .overScrollVertical()
            .verticalScroll(scrollState)
            .padding(horizontal = Dimensions.Padding.content)
            .padding(top = Spacing.xs, bottom = Spacing.xxl)
    ) {
        if (isEditMode) {
            EditableTransactionHeader(
                transaction = transaction,
                viewModel = viewModel
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            EditableExtractedInfoCard(
                transaction = transaction,
                applyToAllFromMerchant = applyToAllFromMerchant,
                updateExistingTransactions = updateExistingTransactions,
                existingTransactionCount = existingTransactionCount,
                accountProfileId = accountProfileId,
                viewModel = viewModel,
                splits = splits,
                showSplitEditor = showSplitEditor
            )
        } else {
            TransactionReceipt(
                transaction = transaction,
                settled = settled,
                primaryCurrency = accountPrimaryCurrency,
                convertedAmount = convertedAmount,
                viewModel = viewModel,
                splits = splits,
                hasSplits = hasSplits,
                loan = loan,
                onNavigateToLoanDetail = onNavigateToLoanDetail,
                onUnmarkLoanClick = onUnmarkLoanClick,
                accountProfileId = accountProfileId,
                towardGoals = towardGoals,
                splitWith = splitWith,
                whatWasThis = whatWasThis,
                onCategoryClick = onCategoryClick,
                onEditClick = { viewModel.enterEditMode() },
                hasClassificationAction = hasClassificationAction
            )
        }
    }
}

/**
 * The detail screen's own top row: a tonal round back button, then an "Edit"
 * tonal pill and the overflow menu ([menu] gets a dismiss callback).
 */
@Composable
private fun DetailTopBar(
    onBack: () -> Unit,
    showActions: Boolean,
    onEdit: () -> Unit,
    menu: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.size(Dimensions.Component.minTouchTarget),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            shapes = IconButtonDefaults.shapes()
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.txn_detail_back))
        }
        Spacer(Modifier.weight(1f))
        if (showActions) {
            FilledTonalButton(
                onClick = onEdit,
                modifier = Modifier.height(Dimensions.Component.minTouchTarget),
                shapes = ButtonDefaults.shapes()
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.Icon.inline)
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.txn_detail_action_edit), style = MaterialTheme.typography.labelLarge)
            }
            Box {
                IconButton(onClick = { showMenu = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.txn_detail_more_actions))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, shape = MaterialTheme.shapes.large) {
                    menu { showMenu = false }
                }
            }
        }
    }
}
