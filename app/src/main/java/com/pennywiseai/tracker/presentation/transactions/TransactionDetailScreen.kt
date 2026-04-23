package com.pennywiseai.tracker.presentation.transactions

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.pennywiseai.tracker.presentation.add.ReceiptPickerSection
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.LocalNavAnimatedVisibilityScope
import com.pennywiseai.tracker.ui.LocalSharedTransitionScope
import com.pennywiseai.tracker.ui.components.BrandIcon
import com.pennywiseai.tracker.ui.components.CategoryChip
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.PennyWiseCard
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.SplitBreakdownCard
import com.pennywiseai.tracker.ui.components.SplitEditor
import com.pennywiseai.tracker.ui.components.SplitItem
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.pennywiseai.tracker.utils.formatAmount
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Reusable filled field colors for edit mode
@Composable
private fun editFilledColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    disabledIndicatorColor = Color.Transparent,
    disabledLabelColor = MaterialTheme.colorScheme.primary,
    disabledTextColor = MaterialTheme.colorScheme.onSurface,
    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
)

private val editTopShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
private val editBottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
private val editFullShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transactionId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToLoanDetail: (Long) -> Unit = {},
    viewModel: TransactionDetailViewModel = hiltViewModel()
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
    val recentPersonNames by viewModel.recentPersonNames.collectAsStateWithLifecycle()
// Account profile state
    val accountProfileId by viewModel.accountProfileId.collectAsStateWithLifecycle()

    // Receipt state
    val receiptUri by viewModel.receiptUri.collectAsStateWithLifecycle()
    val pendingReceiptUri by viewModel.pendingReceiptUri.collectAsStateWithLifecycle()
    val showFullScreenReceipt by viewModel.showFullScreenReceipt.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Show success snackbar
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            scope.launch {
                snackbarHostState.showSnackbar("Transaction updated successfully")
                viewModel.clearSaveSuccess()
            }
        }
    }
    
    // Show error snackbar
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
            }
        }
    }
    
    LaunchedEffect(transactionId) {
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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            // Show FABs only when not in edit mode and transaction exists
            if (!isEditMode && transaction != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Delete FAB
                    SmallFloatingActionButton(
                        onClick = { viewModel.showDeleteDialog() },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Transaction"
                        )
                    }
                    
                    // Report Issue FAB
                    FloatingActionButton(
                        onClick = {
                            val reportUrl = viewModel.getReportUrl()
                            android.util.Log.d("TransactionDetail", "Report FAB clicked, opening URL: ${reportUrl.take(200)}...")
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(reportUrl))
                            try {
                                context.startActivity(intent)
                                android.util.Log.d("TransactionDetail", "Successfully launched browser intent")
                            } catch (e: Exception) {
                                android.util.Log.e("TransactionDetail", "Error launching browser", e)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Report Issue"
                        )
                    }
                }
            }
        },
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = if (isEditMode) "Edit Transaction" else "Transaction Details",
                hasBackButton = true,
                hasActionButton = true,
                navigationContent = {
                    IconButton(onClick = {
                        if (isEditMode) {
                            viewModel.cancelEdit()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            if (isEditMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEditMode) "Cancel" else "Back"
                        )
                    }
                },
                actionContent = {
                    if (!isEditMode && transaction != null) {
                        IconButton(onClick = { viewModel.enterEditMode() }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit"
                            )
                        }
                    } else if (isEditMode) {
                        TextButton(
                            onClick = { viewModel.saveChanges() },
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text("Save")
                            }
                        }
                    }
                },
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
        val displayTransaction = if (isEditMode) editableTransaction else transaction
        displayTransaction?.let { txn ->
            TransactionDetailContent(
                transaction = txn,
                isEditMode = isEditMode,
                applyToAllFromMerchant = applyToAllFromMerchant,
                updateExistingTransactions = updateExistingTransactions,
                existingTransactionCount = existingTransactionCount,
                viewModel = viewModel,
                accountPrimaryCurrency = accountPrimaryCurrency,
                convertedAmount = convertedAmount,
                splits = splits,
                showSplitEditor = showSplitEditor,
                hasSplits = hasSplits,
                loan = loan,
                onNavigateToLoanDetail = onNavigateToLoanDetail,
                accountProfileId = accountProfileId,
                hazeState = hazeState,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
    
    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            title = { Text("Delete Transaction") },
            text = { 
                Text("Are you sure you want to delete this transaction? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteTransaction() },
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Dimensions.Icon.small),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            "Delete",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteDialog() }) {
                    Text("Cancel")
                }
            }
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
            onConfirm = { personName, note ->
                viewModel.createLoanFromTransaction(personName, inferredDirection, note)
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
                    contentDescription = "Receipt full screen",
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
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        }
    }
}

@Composable
private fun TransactionDetailContent(
    transaction: TransactionEntity,
    isEditMode: Boolean,
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
    accountProfileId: Long?,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .overScrollVertical()
            .verticalScroll(scrollState)
            .padding(horizontal = Dimensions.Padding.content)
            .padding(top = Spacing.sm, bottom = Dimensions.Padding.content)
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
                primaryCurrency = accountPrimaryCurrency,
                convertedAmount = convertedAmount,
                viewModel = viewModel,
                splits = splits,
                hasSplits = hasSplits,
                loan = loan,
                onNavigateToLoanDetail = onNavigateToLoanDetail,
                accountProfileId = accountProfileId
            )
        }
    }
}

// ==================== Clean detail read-only view ====================

@Composable
private fun TransactionReceipt(
    transaction: TransactionEntity,
    primaryCurrency: String,
    convertedAmount: BigDecimal?,
    viewModel: TransactionDetailViewModel,
    splits: List<SplitItem>,
    hasSplits: Boolean,
    loan: LoanEntity?,
    onNavigateToLoanDetail: (Long) -> Unit,
    accountProfileId: Long? = null
) {
    val isDark = isSystemInDarkTheme()
    val typeColor = when (transaction.transactionType) {
        TransactionType.INCOME -> if (isDark) income_dark else income_light
        TransactionType.EXPENSE -> if (isDark) expense_dark else expense_light
        TransactionType.CREDIT -> if (isDark) credit_dark else credit_light
        TransactionType.TRANSFER -> if (isDark) transfer_dark else transfer_light
        TransactionType.INVESTMENT -> if (isDark) investment_dark else investment_light
    }
    val sign = when (transaction.transactionType) {
        TransactionType.INCOME -> "+"
        TransactionType.EXPENSE -> "-"
        TransactionType.CREDIT -> ""
        TransactionType.TRANSFER -> ""
        TransactionType.INVESTMENT -> ""
    }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // ── Hero Header ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val heroIconModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            sharedTransitionScope.rememberSharedContentState(
                                key = "brand_icon_${transaction.id}"
                            ),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                } else {
                    Modifier
                }
                BrandIcon(
                    merchantName = transaction.merchantName,
                    modifier = heroIconModifier,
                    size = 56.dp,
                    showBackground = true
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                Text(
                    text = transaction.merchantName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                // Transaction type chip
                val typeLabel = transaction.transactionType.name.lowercase()
                    .replaceFirstChar { it.uppercase() }
                val typeIcon = when (transaction.transactionType) {
                    TransactionType.INCOME -> Icons.AutoMirrored.Filled.TrendingUp
                    TransactionType.EXPENSE -> Icons.AutoMirrored.Filled.TrendingDown
                    TransactionType.CREDIT -> Icons.Default.CreditCard
                    TransactionType.TRANSFER -> Icons.Default.SwapHoriz
                    TransactionType.INVESTMENT -> Icons.AutoMirrored.Filled.ShowChart
                }
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    icon = {
                        Icon(
                            typeIcon,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small),
                            tint = typeColor
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = typeColor.copy(alpha = 0.12f),
                        labelColor = typeColor,
                        iconContentColor = typeColor
                    ),
                    border = null
                )

                // Loan chip
                val isDarkTheme = isSystemInDarkTheme()
                val loanColor = if (isDarkTheme) loan_dark else loan_light
                Spacer(modifier = Modifier.height(Spacing.xs))
                if (loan != null) {
                    SuggestionChip(
                        onClick = { onNavigateToLoanDetail(loan.id) },
                        label = {
                            Text(
                                text = if (loan.direction == LoanDirection.LENT)
                                    "Lent to ${loan.personName}" else "Borrowed from ${loan.personName}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.Icon.small),
                                tint = loanColor
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = loanColor.copy(alpha = 0.12f),
                            labelColor = loanColor,
                            iconContentColor = loanColor
                        ),
                        border = null
                    )
                } else {
                    SuggestionChip(
                        onClick = { viewModel.showMarkAsLoanSheet() },
                        label = {
                            Text(
                                text = "Mark as loan",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.Icon.small)
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = null
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                Text(
                    text = "$sign${transaction.formatAmount()}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = typeColor,
                    textAlign = TextAlign.Center
                )

                if (transaction.currency.isNotEmpty() &&
                    !transaction.currency.equals(primaryCurrency, ignoreCase = true) &&
                    convertedAmount != null
                ) {
                    Text(
                        text = "\u2248 ${CurrencyFormatter.formatCurrency(convertedAmount, primaryCurrency)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Details Section ──
        Column(modifier = Modifier.fillMaxWidth()) {
            // Date & Time
            DetailInfoRow(
                icon = Icons.Default.CalendarToday,
                label = "Date & Time",
                value = transaction.dateTime.format(
                    DateTimeFormatter.ofPattern("EEE, MMM d, yyyy \u00b7 h:mm a")
                )
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Category
            val categoryValue = if (hasSplits && splits.isNotEmpty()) {
                "Split (${splits.size} categories)"
            } else {
                transaction.category
            }
            DetailInfoRow(
                icon = Icons.Default.Category,
                label = "Category",
                value = categoryValue
            )

            // Bank
            transaction.bankName?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailInfoRow(
                    icon = Icons.Default.AccountBalance,
                    label = "Bank",
                    value = it
                )
            }

            // Description
            transaction.description?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailInfoRow(
                    icon = Icons.Default.Description,
                    label = "Description",
                    value = it
                )
            }

            // Recurring
            if (transaction.isRecurring) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailInfoRow(
                    icon = Icons.Default.Repeat,
                    label = "Status",
                    value = "Recurring"
                )
            }

            // Classification
            val effectiveProfileId = transaction.profileId ?: accountProfileId
            val isEffectivelyBusiness = effectiveProfileId == ProfileEntity.BUSINESS_ID
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            DetailInfoRow(
                icon = if (isEffectivelyBusiness) Icons.Default.Business else Icons.Default.Person,
                label = "Classification",
                value = if (isEffectivelyBusiness) "Business" else "Personal"
            )

            // Account info
            if (transaction.fromAccount != null && transaction.toAccount != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                val maskedFrom = transaction.fromAccount.let { from ->
                    if (from.length > 4) "*".repeat(from.length - 4) + from.takeLast(4) else from
                }
                val maskedTo = transaction.toAccount.let { to ->
                    if (to.length > 4) "*".repeat(to.length - 4) + to.takeLast(4) else to
                }
                TransferFlowRow(fromValue = maskedFrom, toValue = maskedTo)
            } else {
                transaction.accountNumber?.let {
                    if (transaction.fromAccount == null && transaction.toAccount == null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        val masked = if (it.length > 4) {
                            "*".repeat(it.length - 4) + it.takeLast(4)
                        } else it
                        DetailInfoRow(
                            icon = Icons.Default.AccountBalanceWallet,
                            label = "Account",
                            value = masked
                        )
                    }
                }
                transaction.fromAccount?.let { from ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    val masked = if (from.length > 4) {
                        "*".repeat(from.length - 4) + from.takeLast(4)
                    } else from
                    DetailInfoRow(
                        icon = Icons.Default.Output,
                        label = "From",
                        value = masked
                    )
                }
                transaction.toAccount?.let { to ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    val masked = if (to.length > 4) {
                        "*".repeat(to.length - 4) + to.takeLast(4)
                    } else to
                    DetailInfoRow(
                        icon = Icons.Default.Input,
                        label = "To",
                        value = masked
                    )
                }
            }

            // Balance
            transaction.balanceAfter?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailInfoRow(
                    icon = Icons.Default.AccountBalanceWallet,
                    label = "Balance",
                    value = CurrencyFormatter.formatCurrency(it, viewModel.primaryCurrency.value)
                )
            }

            // Reference number (prefer extracted reference, fallback to SMS sender)
            val referenceValue = transaction.reference ?: transaction.smsSender
            referenceValue?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailInfoRow(
                    icon = Icons.Default.Tag,
                    label = "Reference",
                    value = it
                )
            }
        }

        // ── Receipt Section ──
        val receiptUri by viewModel.receiptUri.collectAsStateWithLifecycle()
        receiptUri?.let { uri ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { viewModel.showFullScreenReceipt() },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.medium),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Receipt",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Tap to view",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    AsyncImage(
                        model = uri,
                        contentDescription = "Receipt",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // ── SMS Section ──
        if (!transaction.smsBody.isNullOrBlank()) {
            ExpandableSmsSection(smsBody = transaction.smsBody)
        }

        // ── Split Breakdown ──
        if (hasSplits && splits.isNotEmpty()) {
            SplitBreakdownCard(
                splits = splits,
                currency = transaction.currency
            )
        }
    }
}

@Composable
private fun DetailInfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.medium),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TransferFlowRow(
    fromValue: String,
    toValue: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(
            Icons.Default.SwapHoriz,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.medium),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Text(
                text = fromValue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = Spacing.sm)
            )
        }

        Icon(
            Icons.Default.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.small),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Text(
                text = toValue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = Spacing.sm)
            )
        }
    }
}

@Composable
private fun ExpandableSmsSection(smsBody: String) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Text(
                        text = if (expanded) "Hide SMS" else "Show original SMS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimensions.Icon.medium)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Text(
                    text = smsBody,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditableTransactionHeader(
    transaction: TransactionEntity,
    viewModel: TransactionDetailViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Amount and Currency
        val primaryCurrency by viewModel.primaryCurrency.collectAsStateWithLifecycle()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            CurrencyDropdown(
                selectedCurrency = transaction.currency.ifEmpty { primaryCurrency },
                onCurrencySelected = { viewModel.updateCurrency(it) },
                modifier = Modifier.width(130.dp)
            )

            TextField(
                value = transaction.amount.stripTrailingZeros().toPlainString(),
                onValueChange = { viewModel.updateAmount(it) },
                label = { Text("Amount", fontWeight = FontWeight.SemiBold) },
                textStyle = MaterialTheme.typography.headlineSmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = editFullShape,
                colors = editFilledColors()
            )
        }

        // Transaction Type
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            TransactionType.values().forEach { type ->
                FilterChip(
                    selected = transaction.transactionType == type,
                    onClick = { viewModel.updateTransactionType(type) },
                    label = { Text(type.name.lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }, maxLines = 1) },
                    leadingIcon = if (transaction.transactionType == type) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(0.7f),
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderWidth = 0.dp,
                        selected = transaction.transactionType == type,
                        enabled = true
                    )
                )
            }
        }

        // Date and Time
        DateTimeField(
            dateTime = transaction.dateTime,
            onDateTimeChange = { viewModel.updateDateTime(it) }
        )

        // Merchant + Description (connected group)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.5.dp)
        ) {
            TextField(
                value = transaction.merchantName,
                onValueChange = { viewModel.updateMerchantName(it) },
                label = { Text("Merchant", fontWeight = FontWeight.SemiBold) },
                leadingIcon = {
                    BrandIcon(
                        merchantName = transaction.merchantName,
                        size = 24.dp,
                        showBackground = false
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = editTopShape,
                isError = transaction.merchantName.isBlank(),
                colors = editFilledColors()
            )

            TextField(
                value = transaction.description ?: "",
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text("Description (Optional)", fontWeight = FontWeight.SemiBold) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.medium)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = editBottomShape,
                colors = editFilledColors()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditableExtractedInfoCard(
    transaction: TransactionEntity,
    applyToAllFromMerchant: Boolean,
    updateExistingTransactions: Boolean,
    existingTransactionCount: Int,
    accountProfileId: Long?,
    viewModel: TransactionDetailViewModel,
    splits: List<SplitItem>,
    showSplitEditor: Boolean
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Account + Category (connected group)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.5.dp)
        ) {
            if (transaction.transactionType == TransactionType.TRANSFER) {
                AccountNumberField(
                    accountNumber = transaction.fromAccount,
                    onAccountNumberChange = { viewModel.updateFromAccount(it) },
                    viewModel = viewModel,
                    label = "From Account",
                    placeholder = "Select or enter source account",
                    excludeAccount = transaction.toAccount
                )
                AccountNumberField(
                    accountNumber = transaction.toAccount,
                    onAccountNumberChange = { viewModel.updateToAccount(it) },
                    viewModel = viewModel,
                    label = "To Account",
                    placeholder = "Select or enter destination account",
                    excludeAccount = transaction.fromAccount
                )
            } else {
                AccountNumberField(
                    accountNumber = transaction.accountNumber,
                    onAccountNumberChange = { viewModel.updateAccountNumber(it) },
                    viewModel = viewModel
                )
            }

            if (!showSplitEditor) {
                CategoryDropdown(
                    selectedCategory = transaction.category,
                    onCategorySelected = { viewModel.updateCategory(it) },
                    viewModel = viewModel
                )
            }
        }

        // Split button
        if (!showSplitEditor && transaction.transactionType == TransactionType.EXPENSE) {
            OutlinedButton(
                onClick = { viewModel.enableSplitMode() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.CallSplit,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.Icon.small)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("Split into categories")
            }
        }

        // Checkboxes
        if (!showSplitEditor) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = applyToAllFromMerchant,
                    onCheckedChange = { viewModel.toggleApplyToAllFromMerchant() }
                )
                Text(
                    text = "Apply category to all from ${transaction.merchantName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (existingTransactionCount > 0) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = updateExistingTransactions,
                        onCheckedChange = { viewModel.toggleUpdateExistingTransactions() }
                    )
                    Text(
                        text = "Update $existingTransactionCount existing ${if (existingTransactionCount == 1) "transaction" else "transactions"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Classification toggle
        val accountDefault = accountProfileId ?: ProfileEntity.PERSONAL_ID
        val effectiveProfileId = transaction.profileId ?: accountDefault
        val isEffectivelyBusiness = effectiveProfileId == ProfileEntity.BUSINESS_ID
        Text(
            text = "Classification",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            SegmentedButton(
                selected = !isEffectivelyBusiness,
                onClick = {
                    val newId = if (accountDefault == ProfileEntity.PERSONAL_ID) null else ProfileEntity.PERSONAL_ID
                    viewModel.updateProfileId(newId)
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text("Personal")
            }
            SegmentedButton(
                selected = isEffectivelyBusiness,
                onClick = {
                    val newId = if (accountDefault == ProfileEntity.BUSINESS_ID) null else ProfileEntity.BUSINESS_ID
                    viewModel.updateProfileId(newId)
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text("Business")
            }
        }

        // Recurring
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = transaction.isRecurring,
                onCheckedChange = { viewModel.updateRecurringStatus(it) }
            )
            Text(
                text = "Recurring Transaction",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Receipt attachment
        val existingReceiptUri by viewModel.receiptUri.collectAsStateWithLifecycle()
        val pendingReceiptUri by viewModel.pendingReceiptUri.collectAsStateWithLifecycle()
        val receiptRemoved by viewModel.receiptRemoved.collectAsStateWithLifecycle()
        val displayReceiptUri = pendingReceiptUri ?: if (receiptRemoved) null else existingReceiptUri
        ReceiptPickerSection(
            receiptUri = displayReceiptUri,
            onReceiptSelected = { uri -> viewModel.updatePendingReceiptUri(uri) },
            onReceiptRemoved = { viewModel.removeReceipt() },
            onCreateCameraUri = { viewModel.createCameraUri() }
        )

        // Bank (read-only)
        transaction.bankName?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimensions.Icon.small)
                )
                Text(
                    text = "Bank: $it (read-only)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Show SplitEditor when in split mode
    if (showSplitEditor) {
        Spacer(modifier = Modifier.height(Spacing.md))
        SplitEditor(
            totalAmount = transaction.amount,
            currency = transaction.currency,
            splits = splits,
            availableCategories = categories.map { it.name },
            onSplitsChanged = { viewModel.updateSplits(it) },
            onRemoveSplits = { viewModel.removeSplits() },
            modifier = Modifier.padding(horizontal = 0.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    viewModel: TransactionDetailViewModel
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    var expanded by remember { mutableStateOf(false) }
    
    // Find the selected category entity for displaying with color
    val selectedCategoryEntity = categories.find { it.name == selectedCategory }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        TextField(
            value = selectedCategory,
            onValueChange = { },
            label = { Text("Category", fontWeight = FontWeight.SemiBold) },
            leadingIcon = {
                if (selectedCategoryEntity != null) {
                    CategoryChip(
                        category = selectedCategoryEntity,
                        showText = false,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.Category,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.medium)
                    )
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            shape = editFullShape,
            colors = editFilledColors()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { 
                        CategoryChip(category = category)
                    },
                    onClick = {
                        onCategorySelected(category.name)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeField(
    dateTime: LocalDateTime,
    onDateTimeChange: (LocalDateTime) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Date button
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                )
                .padding(Spacing.sm)
                .clickable(
                    onClick = { showDatePicker = true },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.size(Spacing.sm))
                Column {
                    Text(
                        text = dateTime.format(DateTimeFormatter.ofPattern("yyyy")),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = dateTime.format(DateTimeFormatter.ofPattern("dd MMMM")),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Time display
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm)
                .clickable { showTimePicker = true },
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                val hour = if (dateTime.hour % 12 == 0) 12 else dateTime.hour % 12
                val minute = dateTime.minute
                val amPm = if (dateTime.hour < 12) "AM" else "PM"

                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(0.2f),
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    Text(
                        text = String.format("%02d", hour),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(5.dp)
                    )
                }
                Text(
                    text = ":",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp
                )
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    Text(
                        text = String.format("%02d", minute),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(5.dp)
                    )
                }
                Box(modifier = Modifier.padding(4.dp)) {
                    Text(
                        text = amPm,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
    
    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateTime.toLocalDate().toEpochDay() * 24 * 60 * 60 * 1000
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val newDate = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        onDateTimeChange(dateTime.withYear(newDate.year)
                            .withMonth(newDate.monthValue)
                            .withDayOfMonth(newDate.dayOfMonth))
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    // Time Picker Dialog
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = dateTime.hour,
            initialMinute = dateTime.minute
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    onDateTimeChange(dateTime.withHour(timePickerState.hour)
                        .withMinute(timePickerState.minute))
                    showTimePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyDropdown(
    selectedCurrency: String,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // Common currencies
    val currencies = listOf(
        "INR", "USD", "EUR", "GBP", "AED", "SGD",
        "CAD", "AUD", "JPY", "CNY", "NPR", "ETB",
        "THB", "MYR", "KWD", "KRW"
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        TextField(
            value = "${CurrencyFormatter.getCurrencySymbol(selectedCurrency)} $selectedCurrency",
            onValueChange = { },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            singleLine = true,
            shape = editFullShape,
            colors = editFilledColors()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            currencies.forEach { currency ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                CurrencyFormatter.getCurrencySymbol(currency),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.width(32.dp)
                            )
                            Text(currency)
                        }
                    },
                    onClick = {
                        onCurrencySelected(currency)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountNumberField(
    accountNumber: String?,
    onAccountNumberChange: (String?) -> Unit,
    viewModel: TransactionDetailViewModel,
    label: String = "Account (Optional)",
    placeholder: String = "Select or enter account number",
    excludeAccount: String? = null
) {
    val availableAccounts by viewModel.availableAccounts.collectAsStateWithLifecycle()
    val filteredAccounts = availableAccounts.filter { it.accountLast4 != excludeAccount }
    var expanded by remember { mutableStateOf(false) }
    var selectedAccount by remember(accountNumber) { 
        mutableStateOf(
            availableAccounts.find { 
                accountNumber?.endsWith(it.accountLast4) == true 
            }?.displayName ?: accountNumber ?: ""
        )
    }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        TextField(
            value = selectedAccount,
            onValueChange = { newValue ->
                selectedAccount = newValue
                // If manually typing, update the account number directly
                if (!availableAccounts.any { it.displayName == newValue }) {
                    onAccountNumberChange(newValue.ifEmpty { null })
                }
            },
            label = { Text(label, fontWeight = FontWeight.SemiBold) },
            leadingIcon = {
                Icon(
                    if (availableAccounts.any { it.displayName == selectedAccount && it.isCreditCard }) {
                        Icons.Default.CreditCard
                    } else {
                        Icons.Default.AccountBalance
                    },
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.Icon.medium)
                )
            },
            shape = editFullShape,
            colors = editFilledColors(),
            trailingIcon = {
                Row {
                    // Clear button if there's text
                    if (selectedAccount.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                selectedAccount = ""
                                onAccountNumberChange(null)
                            }
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear",
                                modifier = Modifier.size(Dimensions.Icon.medium)
                            )
                        }
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable),
            singleLine = true,
            placeholder = { Text(placeholder) }
        )
        
        if (filteredAccounts.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                filteredAccounts.forEach { account ->
                    DropdownMenuItem(
                        text = { 
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (account.isCreditCard) Icons.Default.CreditCard 
                                    else Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.Icon.medium),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(account.displayName)
                            }
                        },
                        onClick = {
                            selectedAccount = account.displayName
                            onAccountNumberChange(account.accountLast4)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

// ==================== Mark as Loan Bottom Sheet ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkAsLoanBottomSheet(
    transactionAmount: BigDecimal,
    transactionCurrency: String,
    direction: LoanDirection,
    recentPersonNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (personName: String, note: String?) -> Unit
) {
    var personName by remember { mutableStateOf("") }
    var isAddingNew by remember { mutableStateOf(recentPersonNames.isEmpty()) }
    var note by remember { mutableStateOf("") }

    val isDark = isSystemInDarkTheme()
    val loanColor = if (isDark) loan_dark else loan_light
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val directionLabel = if (direction == LoanDirection.LENT) "Lent" else "Borrowed"

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
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "$directionLabel ${CurrencyFormatter.formatCurrency(transactionAmount, transactionCurrency)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (direction == LoanDirection.LENT) "Who did you pay for?" else "Who paid for you?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isAddingNew && recentPersonNames.isNotEmpty()) {
                // Pick from existing people
                recentPersonNames.forEach { name ->
                    Surface(
                        onClick = { personName = name },
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                        color = if (personName == name) loanColor.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = if (personName == name) BorderStroke(1.dp, loanColor)
                        else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(loanColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    name.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = loanColor
                                )
                            }
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (personName == name) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = loanColor,
                                    modifier = Modifier.size(Dimensions.Icon.small)
                                )
                            }
                        }
                    }
                }

                // Add new person option
                TextButton(onClick = { isAddingNew = true; personName = "" }) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("New person")
                }
            } else {
                // Text field for new person name
                TextField(
                    value = personName,
                    onValueChange = { personName = it },
                    label = { Text("Person's name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = editFullShape,
                    colors = editFilledColors()
                )
                if (recentPersonNames.isNotEmpty()) {
                    TextButton(onClick = { isAddingNew = false; personName = "" }) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Pick existing")
                    }
                }
            }

            // Optional note
            TextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = editFullShape,
                colors = editFilledColors()
            )

            // Confirm button
            Button(
                onClick = {
                    onConfirm(
                        personName.trim(),
                        note.trim().ifEmpty { null }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = personName.isNotBlank(),
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = loanColor
                )
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.Icon.small)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("Confirm")
            }
        }
    }
}