package com.pennywiseai.tracker.ui.screens.rules

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.domain.usecase.BatchApplyResult
import com.pennywiseai.tracker.domain.usecase.DryRunResult
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.PennyWiseCard
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.viewmodel.RulesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCreateRule: () -> Unit,
    onNavigateToEditRule: (String) -> Unit,
    viewModel: RulesViewModel = hiltViewModel()
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val batchApplyProgress by viewModel.batchApplyProgress.collectAsStateWithLifecycle()
    val batchApplyResult by viewModel.batchApplyResult.collectAsStateWithLifecycle()
    val dryRunResult by viewModel.dryRunResult.collectAsStateWithLifecycle()

    var showBatchApplyDialog by remember { mutableStateOf(false) }
    var selectedRuleForBatch by remember { mutableStateOf<com.pennywiseai.tracker.domain.model.rule.TransactionRule?>(null) }

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    // Reset dialog state needs to be outside the lambda
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = "Smart Rules",
                hasBackButton = true,
                hasActionButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                actionContent = {
                    IconButton(
                        onClick = { showResetDialog = true }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset to defaults"
                        )
                    }
                },
                hazeState = hazeState
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreateRule,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Rule")
            }
        }
    ) { paddingValues ->

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset Rules") },
                text = { Text("Reset all rules to default settings? Your custom settings will be lost.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.resetToDefaults()
                            showResetDialog = false
                        }
                    ) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val lazyListState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .background(MaterialTheme.colorScheme.background)
                    .overScrollVertical(),
                contentPadding = PaddingValues(
                    start = Dimensions.Padding.content,
                    end = Dimensions.Padding.content,
                    top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                    bottom = 0.dp
                ),
                state = lazyListState,
                flingBehavior = rememberOverscrollFlingBehavior { lazyListState },
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Info Card
                item {
                    PennyWiseCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Column {
                                Text(
                                    text = "Automatic Categorization",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Enable rules to automatically categorize your transactions based on patterns",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Group rules by category for better organization
                item {
                    val groupedRules = rules.groupBy { rule ->
                        when {
                            rule.name.contains("Food", ignoreCase = true) ||
                            rule.name.contains("Fuel", ignoreCase = true) -> "Daily Expenses"

                            rule.name.contains("Salary", ignoreCase = true) ||
                            rule.name.contains("Cashback", ignoreCase = true) -> "Income & Cashback"

                            rule.name.contains("Rent", ignoreCase = true) ||
                            rule.name.contains("EMI", ignoreCase = true) ||
                            rule.name.contains("Subscription", ignoreCase = true) -> "Recurring Payments"

                            rule.name.contains("Investment", ignoreCase = true) ||
                            rule.name.contains("Transfer", ignoreCase = true) -> "Banking & Investments"

                            rule.name.contains("Healthcare", ignoreCase = true) -> "Healthcare"

                            else -> "Other"
                        }
                    }

                    groupedRules.forEach { (category, categoryRules) ->
                        if (categoryRules.isNotEmpty()) {
                            SectionHeaderV2(title = category)

                            categoryRules.forEach { rule ->
                                RuleCard(
                                    rule = rule,
                                    onToggle = { isActive ->
                                        viewModel.toggleRule(rule.id, isActive)
                                    },
                                    onEdit = {
                                        onNavigateToEditRule(rule.id)
                                    },
                                    onDelete = {
                                        viewModel.deleteRule(rule.id)
                                    },
                                    onApplyToPast = {
                                        selectedRuleForBatch = rule
                                        showBatchApplyDialog = true
                                    }
                                )
                            }
                        }
                    }
                }

                // Help text at the bottom
                item {
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    Text(
                        text = "Rules are applied automatically to new transactions. Higher priority rules run first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.md)
                    )
                }
            }
        }
    }

    // Batch Apply Dialog
    if (showBatchApplyDialog && selectedRuleForBatch != null) {
        BatchApplyDialog(
            rule = selectedRuleForBatch!!,
            progress = batchApplyProgress,
            result = batchApplyResult,
            dryRunResult = dryRunResult,
            isLoading = isLoading,
            onDismiss = {
                showBatchApplyDialog = false
                selectedRuleForBatch = null
                viewModel.clearBatchApplyResult()
            },
            onPreview = {
                viewModel.previewRule(selectedRuleForBatch!!)
            },
            onApplyToAll = {
                viewModel.applyRuleToPastTransactions(selectedRuleForBatch!!, applyToUncategorizedOnly = false)
            },
            onApplyToUncategorized = {
                viewModel.applyRuleToPastTransactions(selectedRuleForBatch!!, applyToUncategorizedOnly = true)
            }
        )
    }
}

@Composable
private fun RuleCard(
    rule: com.pennywiseai.tracker.domain.model.rule.TransactionRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onApplyToPast: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showActionsMenu by remember { mutableStateOf(false) }
    PennyWiseCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )

                rule.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Show simple condition summary
                val conditionSummary = when {
                    rule.name.contains("Small Payments", ignoreCase = true) -> "Amount < 200"
                    rule.name.contains("UPI Cashback", ignoreCase = true) -> "Amount < 10 from NPCI"
                    rule.name.contains("Salary", ignoreCase = true) -> "Credits with salary keywords"
                    rule.name.contains("Rent", ignoreCase = true) -> "Payments with rent keywords"
                    rule.name.contains("EMI", ignoreCase = true) -> "EMI/loan keywords"
                    rule.name.contains("Investment", ignoreCase = true) -> "Mutual funds, stocks keywords"
                    rule.name.contains("Subscription", ignoreCase = true) -> "Netflix, Spotify, etc."
                    rule.name.contains("Fuel", ignoreCase = true) -> "Petrol pump transactions"
                    rule.name.contains("Healthcare", ignoreCase = true) -> "Hospital, pharmacy keywords"
                    rule.name.contains("Transfer", ignoreCase = true) -> "Self transfers, contra"
                    else -> null
                }

                conditionSummary?.let {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Priority badge (only show for non-default priority)
                if (rule.priority != 100) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "Priority: ${rule.priority}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // More actions menu - only show when rule is active
                if (rule.isActive) {
                    Box {
                        IconButton(
                            onClick = { showActionsMenu = true }
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More actions"
                            )
                        }

                        DropdownMenu(
                            expanded = showActionsMenu,
                            onDismissRequest = { showActionsMenu = false }
                        ) {
                            // Edit rule
                            DropdownMenuItem(
                                text = { Text("Edit Rule") },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                },
                                onClick = {
                                    showActionsMenu = false
                                    onEdit()
                                }
                            )

                            // Apply to past transactions
                            DropdownMenuItem(
                                text = { Text("Apply to Past Transactions") },
                                leadingIcon = {
                                    Icon(Icons.Default.History, contentDescription = null)
                                },
                                onClick = {
                                    showActionsMenu = false
                                    onApplyToPast()
                                }
                            )

                            // Only show delete for custom rules
                            if (!rule.isSystemTemplate) {
                                DropdownMenuItem(
                                    text = { Text("Delete Rule") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showActionsMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }

                Switch(
                    checked = rule.isActive,
                    onCheckedChange = onToggle
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Rule") },
            text = { Text("Delete \"${rule.name}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun BatchApplyDialog(
    rule: com.pennywiseai.tracker.domain.model.rule.TransactionRule,
    progress: Pair<Int, Int>?,
    result: BatchApplyResult?,
    dryRunResult: DryRunResult?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onPreview: () -> Unit,
    onApplyToAll: () -> Unit,
    onApplyToUncategorized: () -> Unit
) {
    val title = when {
        progress != null -> "Applying Rule..."
        isLoading && dryRunResult == null -> "Previewing..."
        dryRunResult != null && result == null -> "Preview: ${rule.name}"
        result != null -> "Apply Rule to Past Transactions"
        else -> "Apply Rule to Past Transactions"
    }

    AlertDialog(
        onDismissRequest = {
            if (progress == null && !isLoading) {
                onDismiss()
            }
        },
        title = { Text(text = title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                when {
                    // Loading preview
                    isLoading && dryRunResult == null && progress == null && result == null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Scanning transactions...", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Preview result (dry run) — before actual apply
                    dryRunResult != null && result == null && progress == null -> {
                        if (dryRunResult.totalMatched == 0) {
                            Text(
                                text = "No transactions match this rule (scanned ${dryRunResult.totalScanned}).",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                text = "Scanned ${dryRunResult.totalScanned} transactions:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${dryRunResult.totalWouldUpdate} would be updated",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            if (dryRunResult.totalWouldBlock > 0) {
                                Text(
                                    text = "${dryRunResult.totalWouldBlock} would be blocked",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (dryRunResult.samples.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
                                Text(
                                    text = "Sample changes:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                dryRunResult.samples.take(5).forEach { diff ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (diff.isBlock)
                                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                            else
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(Spacing.sm)) {
                                            val orig = diff.original
                                            Text(
                                                text = "${orig.merchantName} - ${orig.amount}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (diff.isBlock) {
                                                Text(
                                                    text = "Would be blocked",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            } else if (diff.modified != null) {
                                                val mod = diff.modified
                                                if (orig.category != mod.category) {
                                                    Text(
                                                        text = "Category: ${orig.category} \u2192 ${mod.category}",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                                if (orig.merchantName != mod.merchantName) {
                                                    Text(
                                                        text = "Merchant: ${orig.merchantName} \u2192 ${mod.merchantName}",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                                if (orig.transactionType != mod.transactionType) {
                                                    Text(
                                                        text = "Type: ${orig.transactionType} \u2192 ${mod.transactionType}",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                                if (orig.description != mod.description) {
                                                    Text(
                                                        text = "Description: ${orig.description ?: "(none)"} \u2192 ${mod.description ?: "(none)"}",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (dryRunResult.totalMatched > 5) {
                                    Text(
                                        text = "...and ${dryRunResult.totalMatched - 5} more",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Initial state — show options with preview button
                    progress == null && result == null -> {
                        Text(
                            text = "Apply \"${rule.name}\" to existing transactions?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Use Preview to see what would change before applying.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Processing state
                    progress != null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = "Processing ${progress.first} of ${progress.second} transactions",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // Result state
                    result != null -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (result.errors.isEmpty()) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (result.errors.isEmpty())
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Completed",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))

                            Text(
                                text = "Transactions processed: ${result.totalProcessed}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Transactions updated: ${result.totalUpdated}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            if (result.totalDeleted > 0) {
                                Text(
                                    text = "Transactions blocked: ${result.totalDeleted}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (result.errors.isNotEmpty()) {
                                Text(
                                    text = "Errors: ${result.errors.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                // Loading — no buttons
                isLoading -> {}

                // Preview result — show Apply or Cancel
                dryRunResult != null && result == null && progress == null -> {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        if (dryRunResult.totalMatched > 0) {
                            TextButton(onClick = onApplyToUncategorized) { Text("Uncategorized") }
                            TextButton(onClick = onApplyToAll) { Text("Apply All") }
                        }
                    }
                }

                // Initial state — show Preview + direct apply buttons
                progress == null && result == null -> {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        OutlinedButton(onClick = onPreview) {
                            Icon(Icons.Default.Preview, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Preview")
                        }
                    }
                }

                // Done — close button
                result != null -> {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    )
}