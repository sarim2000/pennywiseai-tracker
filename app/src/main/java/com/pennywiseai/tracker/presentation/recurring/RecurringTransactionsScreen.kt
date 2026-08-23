package com.pennywiseai.tracker.presentation.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.database.entity.RecurringFrequency
import com.pennywiseai.tracker.data.database.entity.RecurringTransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringTransactionsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: RecurringTransactionsViewModel = hiltViewModel()
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val baseCurrency by viewModel.baseCurrency.collectAsStateWithLifecycle()
    val canAddMore by viewModel.canAddMore.collectAsStateWithLifecycle()

    // Null = editor closed. Non-null = editing this form (id 0 for a fresh add).
    var editing by remember { mutableStateOf<RecurringFormState?>(null) }
    // Free tier allows a limited number of templates; adding beyond it opens the paywall (#706).
    var showUpgradeSheet by rememberSaveable { mutableStateOf(false) }

    val active = templates.filter { it.isActive }
    val paused = templates.filterNot { it.isActive }

    PennyWiseScaffold(
        title = "Recurring",
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (canAddMore) editing = RecurringFormState(currency = baseCurrency)
                    else showUpgradeSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add recurring transaction")
            }
        }
    ) { padding ->
        if (templates.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                PennyWiseEmptyState(
                    icon = Icons.Default.EventRepeat,
                    headline = "No recurring transactions",
                    description = "Schedule cash or manual spend to be added automatically — rent, an allowance, a weekly cleaner."
                )
            }
            editing?.let { form ->
                RecurringEditorDialog(
                    form = form,
                    categoryNames = categories.map { it.name },
                    onDismiss = { editing = null },
                    onSave = { viewModel.save(it); editing = null }
                )
            }
            return@PennyWiseScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Dimensions.Component.fabBottomInset
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (active.isNotEmpty()) {
                item { SectionHeaderV2(title = "Active") }
                items(active, key = { it.id }) { template ->
                    RecurringItem(
                        template = template,
                        onEdit = { editing = RecurringFormState.from(template) },
                        onToggleActive = { viewModel.setActive(template, it) },
                        onDelete = { viewModel.delete(template) }
                    )
                }
            }
            if (paused.isNotEmpty()) {
                item { SectionHeaderV2(title = "Paused") }
                items(paused, key = { it.id }) { template ->
                    RecurringItem(
                        template = template,
                        onEdit = { editing = RecurringFormState.from(template) },
                        onToggleActive = { viewModel.setActive(template, it) },
                        onDelete = { viewModel.delete(template) }
                    )
                }
            }
        }
    }

    editing?.let { form ->
        RecurringEditorDialog(
            form = form,
            categoryNames = categories.map { it.name },
            onDismiss = { editing = null },
            onSave = { viewModel.save(it); editing = null }
        )
    }

    if (showUpgradeSheet) {
        com.pennywiseai.tracker.presentation.paywall.UpgradeSheet(
            onDismiss = { showUpgradeSheet = false },
        )
    }
}

private fun RecurringFrequency.label(): String = when (this) {
    RecurringFrequency.DAILY -> "Daily"
    RecurringFrequency.WEEKLY -> "Weekly"
    RecurringFrequency.MONTHLY -> "Monthly"
    RecurringFrequency.YEARLY -> "Yearly"
}

@Composable
private fun RecurringItem(
    template: RecurringTransactionEntity,
    onEdit: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    PennyWiseCardV2(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.merchantName.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = buildString {
                        append(template.frequency.label())
                        append(" · Next ")
                        append(template.nextDueDate.format(DateTimeFormatter.ofPattern("MMM d")))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                template.category.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = CurrencyFormatter.formatCurrency(template.amount, template.currency),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (template.isActive) "Pause" else "Resume") },
                        onClick = {
                            showMenu = false
                            onToggleActive(!template.isActive)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            showDeleteConfirm = true
                        }
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete recurring transaction?") },
            text = { Text("\"${template.merchantName}\" will stop being added automatically. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringEditorDialog(
    form: RecurringFormState,
    categoryNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (RecurringFormState) -> Unit
) {
    var state by remember { mutableStateOf(form) }
    var freqExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var dowExpanded by remember { mutableStateOf(false) }

    val dayOfWeekNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (form.id == 0L) "New recurring" else "Edit recurring") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedTextField(
                    value = state.merchantName,
                    onValueChange = { state = state.copy(merchantName = it) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = state.amount,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() || it == '.' }
                            if (filtered.count { it == '.' } <= 1) state = state.copy(amount = filtered)
                        },
                        label = { Text("Amount") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.currency,
                        onValueChange = { state = state.copy(currency = it.uppercase().take(3)) },
                        label = { Text("Currency") },
                        singleLine = true,
                        modifier = Modifier.width(110.dp)
                    )
                }

                // Type: Expense / Income
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    FilterChip(
                        selected = state.transactionType == TransactionType.EXPENSE,
                        onClick = { state = state.copy(transactionType = TransactionType.EXPENSE) },
                        label = { Text("Expense") }
                    )
                    FilterChip(
                        selected = state.transactionType == TransactionType.INCOME,
                        onClick = { state = state.copy(transactionType = TransactionType.INCOME) },
                        label = { Text("Income") }
                    )
                }

                // Category dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = state.category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categoryNames.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    state = state.copy(category = name)
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Frequency dropdown
                ExposedDropdownMenuBox(
                    expanded = freqExpanded,
                    onExpandedChange = { freqExpanded = it }
                ) {
                    OutlinedTextField(
                        value = state.frequency.label(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Frequency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(freqExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = freqExpanded,
                        onDismissRequest = { freqExpanded = false }
                    ) {
                        // Yearly needs a month anchor we don't model yet, so it's
                        // left out of the picker for now (the enum stays for backup
                        // compatibility). (#706 Greptile)
                        RecurringFrequency.entries
                            .filter { it != RecurringFrequency.YEARLY }
                            .forEach { freq ->
                            DropdownMenuItem(
                                text = { Text(freq.label()) },
                                onClick = {
                                    state = state.copy(frequency = freq)
                                    freqExpanded = false
                                }
                            )
                        }
                    }
                }

                // Day selector — depends on cadence
                when (state.frequency) {
                    RecurringFrequency.MONTHLY, RecurringFrequency.YEARLY -> {
                        OutlinedTextField(
                            value = state.dayOfMonth?.toString() ?: "",
                            onValueChange = { input ->
                                val n = input.filter { it.isDigit() }.toIntOrNull()
                                state = state.copy(dayOfMonth = n?.coerceIn(1, 31))
                            },
                            label = { Text("Day of month (1-31)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    RecurringFrequency.WEEKLY -> {
                        ExposedDropdownMenuBox(
                            expanded = dowExpanded,
                            onExpandedChange = { dowExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = state.dayOfWeek?.let { dayOfWeekNames[it - 1] } ?: "Any",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Day of week") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dowExpanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = dowExpanded,
                                onDismissRequest = { dowExpanded = false }
                            ) {
                                dayOfWeekNames.forEachIndexed { index, name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            state = state.copy(dayOfWeek = index + 1)
                                            dowExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    RecurringFrequency.DAILY -> { /* no day selector */ }
                }

                OutlinedTextField(
                    value = state.note,
                    onValueChange = { state = state.copy(note = it) },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Active", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = state.isActive,
                        onCheckedChange = { state = state.copy(isActive = it) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = state.isValid, onClick = { onSave(state) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
