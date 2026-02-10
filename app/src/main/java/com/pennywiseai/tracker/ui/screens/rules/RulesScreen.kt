package com.pennywiseai.tracker.ui.screens.rules

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.domain.usecase.BatchApplyResult
import com.pennywiseai.tracker.ui.components.PennyWiseCard
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.SectionHeader
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

    var showBatchApplyDialog by remember { mutableStateOf(false) }
    var selectedRuleForBatch by remember { mutableStateOf<com.pennywiseai.tracker.domain.model.rule.TransactionRule?>(null) }

    PennyWiseScaffold(
        title = "Smart Rules",
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate back"
                )
            }
        },
        actions = {
            // Optional: Add reset button for advanced users
            var showResetDialog by remember { mutableStateOf(false) }

            IconButton(
                onClick = { showResetDialog = true }
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Reset to defaults"
                )
            }

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(Dimensions.Padding.content),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Padding.content),
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

                // Group rules by category for better organization
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
                        SectionHeader(title = category)

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

                // Help text at the bottom
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

    // Batch Apply Dialog
    if (showBatchApplyDialog && selectedRuleForBatch != null) {
        BatchApplyDialog(
            rule = selectedRuleForBatch!!,
            progress = batchApplyProgress,
            result = batchApplyResult,
            onDismiss = {
                showBatchApplyDialog = false
                selectedRuleForBatch = null
                viewModel.clearBatchApplyResult()
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
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
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
    onDismiss: () -> Unit,
    onApplyToAll: () -> Unit,
    onApplyToUncategorized: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (progress == null) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = if (progress != null) "Applying Rule..." else "Apply Rule to Past Transactions"
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                if (progress == null && result == null) {
                    // Initial state - show options
                    Text(
                        text = "Apply \"${rule.name}\" to existing transactions?",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    Text(
                        text = "Choose how to apply this rule:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Text(
                        text = "• All - Apply to every transaction",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Uncategorized - Skip already categorized transactions (Recommended)",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (progress != null) {
                    // Processing state - show progress
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Processing ${progress.first} of ${progress.second} transactions",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else if (result != null) {
                    // Result state - show summary
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

                        HorizontalDivider()

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
                                text = "Transactions blocked (soft deleted): ${result.totalDeleted}",
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
        },
        confirmButton = {
            if (progress == null && result == null) {
                // Show action buttons using FlowRow for better responsive layout
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    TextButton(onClick = onApplyToUncategorized) {
                        Text("Uncategorized")
                    }
                    TextButton(onClick = onApplyToAll) {
                        Text("All")
                    }
                }
            } else if (result != null) {
                // Done - show close button
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}