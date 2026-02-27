package com.pennywiseai.tracker.presentation.transactions

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.components.BrandIcon
import com.pennywiseai.tracker.ui.components.CategoryChip
import com.pennywiseai.tracker.ui.components.DashedLine
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.PennyWiseCard
import com.pennywiseai.tracker.ui.components.SplitBreakdownCard
import com.pennywiseai.tracker.ui.components.SplitEditor
import com.pennywiseai.tracker.ui.components.SplitItem
import com.pennywiseai.tracker.ui.components.cards.ReceiptShape
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.pennywiseai.tracker.utils.formatAmount
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transactionId: Long,
    onNavigateBack: () -> Unit,
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
                                    strokeWidth = 2.dp
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
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
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
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .imePadding()
            .overScrollVertical()
            .verticalScroll(scrollState)
            .padding(Dimensions.Padding.content)
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
                viewModel = viewModel,
                splits = splits,
                showSplitEditor = showSplitEditor
            )

            Spacer(modifier = Modifier.height(100.dp))
        } else {
            TransactionReceipt(
                transaction = transaction,
                primaryCurrency = accountPrimaryCurrency,
                convertedAmount = convertedAmount,
                viewModel = viewModel,
                splits = splits,
                hasSplits = hasSplits
            )
        }
    }
}

// ==================== Receipt-style read-only view ====================

@Composable
private fun TransactionReceipt(
    transaction: TransactionEntity,
    primaryCurrency: String,
    convertedAmount: BigDecimal?,
    viewModel: TransactionDetailViewModel,
    splits: List<SplitItem>,
    hasSplits: Boolean
) {
    val density = LocalDensity.current
    val cutoutRadius = 10.dp
    val cutoutRadiusPx = with(density) { cutoutRadius.toPx() }
    val scallopRadiusPx = with(density) { 6.dp.toPx() }

    // Dynamic cutout offset — tracks the dashed separator position
    var cutoutOffsetPx by remember { mutableFloatStateOf(with(density) { 420.dp.toPx() }) }

    val dashedLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.lg, start = 12.dp, end = 12.dp)
    ) {
        Surface(
            modifier = Modifier
                .animateContentSize(animationSpec = tween(durationMillis = 300))
                .fillMaxWidth(),
            shape = ReceiptShape(cutoutRadiusPx, cutoutOffsetPx, scallopRadiusPx),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 4.dp,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Merchant badge with dashed lines
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DashedLine(
                        modifier = Modifier.weight(1f),
                        color = dashedLineColor
                    )
                    ReceiptBadge(merchantName = transaction.merchantName)
                    DashedLine(
                        modifier = Modifier.weight(1f),
                        color = dashedLineColor
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // Amount — immediately after merchant badge like Cashiro
                val isDark = isSystemInDarkTheme()
                val amountColor = when (transaction.transactionType) {
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
                val typeEmoji = when (transaction.transactionType) {
                    TransactionType.EXPENSE -> "\uD83D\uDCB3 "
                    TransactionType.CREDIT -> "\uD83D\uDCB3 "
                    TransactionType.TRANSFER -> "\u2194\uFE0F "
                    TransactionType.INVESTMENT -> "\uD83D\uDCC8 "
                    TransactionType.INCOME -> "\uD83D\uDCB0 "
                }

                Text(
                    text = "$typeEmoji$sign${transaction.formatAmount()}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
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

                Spacer(modifier = Modifier.height(Spacing.md))

                // Dashed separator — side notch cutouts align here
                DashedLine(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimensions.Padding.content)
                        .onGloballyPositioned { coordinates ->
                            cutoutOffsetPx = coordinates.parentLayoutCoordinates
                                ?.let { parent ->
                                    coordinates.localToRoot(
                                        androidx.compose.ui.geometry.Offset.Zero
                                    ).y - parent.localToRoot(
                                        androidx.compose.ui.geometry.Offset.Zero
                                    ).y
                                } ?: cutoutOffsetPx
                        },
                    color = dashedLineColor
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // Detail rows (below the separator)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimensions.Padding.content),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    // Date & Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Date",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = transaction.dateTime.format(
                                    DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val dt = transaction.dateTime
                                val hour = if (dt.hour % 12 == 0) 12 else dt.hour % 12
                                val minute = dt.minute
                                val amPm = if (dt.hour < 12) "AM" else "PM"

                                Box(
                                    modifier = Modifier
                                        .padding(2.dp)
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
                                        .padding(2.dp)
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
                                Box(modifier = Modifier.padding(5.dp)) {
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

                    // Transaction Type
                    ReceiptInfoRow(
                        label = "Type",
                        value = transaction.transactionType.name.lowercase()
                            .replaceFirstChar { it.uppercase() }
                    )

                    // Category
                    if (hasSplits && splits.isNotEmpty()) {
                        ReceiptInfoRow(
                            label = "Category",
                            value = "Split (${splits.size} categories)"
                        )
                    } else {
                        ReceiptInfoRow(
                            label = "Category",
                            value = transaction.category
                        )
                    }

                    // Bank
                    transaction.bankName?.let {
                        ReceiptInfoRow(label = "Bank", value = it)
                    }

                    // Description
                    transaction.description?.let {
                        ReceiptInfoRow(label = "Description", value = it)
                    }

                    // Recurring
                    if (transaction.isRecurring) {
                        ReceiptInfoRow(label = "Status", value = "Recurring")
                    }

                    // Account info
                    if (transaction.fromAccount != null && transaction.toAccount != null) {
                        val maskedFrom = transaction.fromAccount.let { from ->
                            if (from.length > 4) "*".repeat(from.length - 4) + from.takeLast(4) else from
                        }
                        val maskedTo = transaction.toAccount.let { to ->
                            if (to.length > 4) "*".repeat(to.length - 4) + to.takeLast(4) else to
                        }
                        ReceiptTransferRow(fromValue = maskedFrom, toValue = maskedTo)
                    } else {
                        transaction.accountNumber?.let {
                            if (transaction.fromAccount == null && transaction.toAccount == null) {
                                val masked = if (it.length > 4) {
                                    "*".repeat(it.length - 4) + it.takeLast(4)
                                } else it
                                ReceiptInfoRow(label = "Account", value = masked)
                            }
                        }
                        transaction.fromAccount?.let { from ->
                            val masked = if (from.length > 4) {
                                "*".repeat(from.length - 4) + from.takeLast(4)
                            } else from
                            ReceiptInfoRow(label = "From", value = masked)
                        }
                        transaction.toAccount?.let { to ->
                            val masked = if (to.length > 4) {
                                "*".repeat(to.length - 4) + to.takeLast(4)
                            } else to
                            ReceiptInfoRow(label = "To", value = masked)
                        }
                    }
                    transaction.balanceAfter?.let {
                        ReceiptInfoRow(
                            label = "Balance",
                            value = CurrencyFormatter.formatCurrency(it, viewModel.primaryCurrency.value)
                        )
                    }

                    // SMS sender
                    transaction.smsSender?.let {
                        ReceiptInfoRow(label = "Reference", value = it)
                    }
                }

                // Expandable SMS body
                if (!transaction.smsBody.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    ExpandableSmsSection(smsBody = transaction.smsBody)
                }

                // Split breakdown
                if (hasSplits && splits.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    SplitBreakdownCard(
                        splits = splits,
                        currency = transaction.currency,
                        modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptBadge(merchantName: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 2.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            BrandIcon(
                merchantName = merchantName,
                size = 48.dp,
                showBackground = true
            )
            Text(
                text = merchantName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ReceiptInfoRow(
    label: String,
    value: String,
    pillColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        DashedLine(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )
        Box(
            modifier = Modifier
                .background(
                    color = pillColor,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ReceiptTransferRow(
    fromValue: String,
    toValue: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "FROM",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "TO",
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = fromValue,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            DashedLine(
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.xs),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = toValue,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ExpandableSmsSection(smsBody: String) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = Spacing.xs),
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
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (expanded) "Hide SMS" else "Show original SMS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = smsBody,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier.padding(Spacing.md)
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
    PennyWiseCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Merchant Name
            OutlinedTextField(
                value = transaction.merchantName,
                onValueChange = { viewModel.updateMerchantName(it) },
                label = { Text("Merchant Name") },
                leadingIcon = {
                    BrandIcon(
                        merchantName = transaction.merchantName,
                        size = 24.dp,
                        showBackground = false
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = transaction.merchantName.isBlank()
            )
            
            // Amount and Currency FlowRow
            val primaryCurrency by viewModel.primaryCurrency.collectAsStateWithLifecycle()
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Currency Dropdown
                CurrencyDropdown(
                    selectedCurrency = transaction.currency.ifEmpty { primaryCurrency },
                    onCurrencySelected = { viewModel.updateCurrency(it) },
                    modifier = Modifier.widthIn(min = 120.dp, max = 160.dp)
                )

                // Amount Field
                OutlinedTextField(
                    value = transaction.amount.stripTrailingZeros().toPlainString(),
                    onValueChange = { viewModel.updateAmount(it) },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.widthIn(min = 150.dp, max = 200.dp)
                )
            }
            
            // Transaction Type - Using FlowRow for responsive layout
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = transaction.transactionType == TransactionType.INCOME,
                    onClick = { viewModel.updateTransactionType(TransactionType.INCOME) },
                    label = { 
                        Text(
                            text = "Income",
                            maxLines = 1
                        ) 
                    },
                    leadingIcon = if (transaction.transactionType == TransactionType.INCOME) {
                        { Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small)) }
                    } else null
                )
                FilterChip(
                    selected = transaction.transactionType == TransactionType.EXPENSE,
                    onClick = { viewModel.updateTransactionType(TransactionType.EXPENSE) },
                    label = { 
                        Text(
                            text = "Expense",
                            maxLines = 1
                        ) 
                    },
                    leadingIcon = if (transaction.transactionType == TransactionType.EXPENSE) {
                        { Icon(Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small)) }
                    } else null
                )
                FilterChip(
                    selected = transaction.transactionType == TransactionType.CREDIT,
                    onClick = { viewModel.updateTransactionType(TransactionType.CREDIT) },
                    label = { 
                        Text(
                            text = "Credit",
                            maxLines = 1
                        ) 
                    },
                    leadingIcon = if (transaction.transactionType == TransactionType.CREDIT) {
                        { Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small)) }
                    } else null
                )
                FilterChip(
                    selected = transaction.transactionType == TransactionType.TRANSFER,
                    onClick = { viewModel.updateTransactionType(TransactionType.TRANSFER) },
                    label = { 
                        Text(
                            text = "Transfer",
                            maxLines = 1
                        ) 
                    },
                    leadingIcon = if (transaction.transactionType == TransactionType.TRANSFER) {
                        { Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small)) }
                    } else null
                )
                FilterChip(
                    selected = transaction.transactionType == TransactionType.INVESTMENT,
                    onClick = { viewModel.updateTransactionType(TransactionType.INVESTMENT) },
                    label = { 
                        Text(
                            text = "Investment",
                            maxLines = 1
                        ) 
                    },
                    leadingIcon = if (transaction.transactionType == TransactionType.INVESTMENT) {
                        { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small)) }
                    } else null
                )
            }
            
            // Date and Time
            DateTimeField(
                dateTime = transaction.dateTime,
                onDateTimeChange = { viewModel.updateDateTime(it) }
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
    viewModel: TransactionDetailViewModel,
    splits: List<SplitItem>,
    showSplitEditor: Boolean
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle(initialValue = emptyList())

    PennyWiseCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = "Transaction Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Category section - show dropdown only if not in split mode
            if (!showSplitEditor) {
                CategoryDropdown(
                    selectedCategory = transaction.category,
                    onCategorySelected = { viewModel.updateCategory(it) },
                    viewModel = viewModel
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                // "Split into categories" button - only for expenses
                if (transaction.transactionType == TransactionType.EXPENSE) {
                    OutlinedButton(
                        onClick = { viewModel.enableSplitMode() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.CallSplit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Split into categories")
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))
                }
            }

            // Apply to all from merchant checkbox - only when not in split mode
            if (!showSplitEditor) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = applyToAllFromMerchant,
                        onCheckedChange = { viewModel.toggleApplyToAllFromMerchant() }
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = "Apply this category to all future transactions from ${transaction.merchantName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Update existing transactions checkbox (only show if there are other transactions)
                if (existingTransactionCount > 0) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = updateExistingTransactions,
                            onCheckedChange = { viewModel.toggleUpdateExistingTransactions() }
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = "Also update $existingTransactionCount existing ${if (existingTransactionCount == 1) "transaction" else "transactions"} from ${transaction.merchantName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            // Description
            OutlinedTextField(
                value = transaction.description ?: "",
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text("Description (Optional)") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Account Number
            AccountNumberField(
                accountNumber = transaction.accountNumber,
                onAccountNumberChange = { viewModel.updateAccountNumber(it) },
                viewModel = viewModel
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Recurring checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = transaction.isRecurring,
                    onCheckedChange = { viewModel.updateRecurringStatus(it) }
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = "Recurring Transaction",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Bank (read-only)
            transaction.bankName?.let {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = "Bank: $it (cannot be edited)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
        OutlinedTextField(
            value = selectedCategory,
            onValueChange = { },
            label = { Text("Category") },
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
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            readOnly = true
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
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // Date Field
        OutlinedTextField(
            value = dateTime.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
            onValueChange = { },
            label = { Text("Date") },
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.CalendarToday, "Select date")
                }
            },
            modifier = Modifier.weight(1f)
        )
        
        // Time Field
        OutlinedTextField(
            value = dateTime.format(DateTimeFormatter.ofPattern("h:mm a")),
            onValueChange = { },
            label = { Text("Time") },
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { showTimePicker = true }) {
                    Icon(Icons.Default.Schedule, "Select time")
                }
            },
            modifier = Modifier.weight(1f)
        )
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
        OutlinedTextField(
            value = selectedCurrency,
            onValueChange = { },
            label = { Text("Currency") },
            leadingIcon = {
                Text(
                    CurrencyFormatter.getCurrencySymbol(selectedCurrency),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            singleLine = true
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
    viewModel: TransactionDetailViewModel
) {
    val availableAccounts by viewModel.availableAccounts.collectAsStateWithLifecycle()
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
        OutlinedTextField(
            value = selectedAccount,
            onValueChange = { newValue ->
                selectedAccount = newValue
                // If manually typing, update the account number directly
                if (!availableAccounts.any { it.displayName == newValue }) {
                    onAccountNumberChange(newValue.ifEmpty { null })
                }
            },
            label = { Text("Account (Optional)") },
            leadingIcon = {
                Icon(
                    if (availableAccounts.any { it.displayName == selectedAccount && it.isCreditCard }) {
                        Icons.Default.CreditCard
                    } else {
                        Icons.Default.AccountBalance
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
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
                                modifier = Modifier.size(20.dp)
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
            placeholder = { Text("Select or enter account number") }
        )
        
        if (availableAccounts.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableAccounts.forEach { account ->
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
                                    modifier = Modifier.size(20.dp),
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