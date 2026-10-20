package com.pennywiseai.tracker.presentation.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.repository.GoalDetail
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GoalStatus
import com.pennywiseai.tracker.presentation.paywall.UpgradeSheet
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.success
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset

/** Which money sheet is open. */
private sealed interface MoneySheetMode {
    data object Add : MoneySheetMode
    data object TakeOut : MoneySheetMode
    data class Edit(val contribution: GoalContributionEntity) : MoneySheetMode
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(
    onNavigateBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenGoal: (Long) -> Unit,
    onOpenTransaction: (Long) -> Unit = {},
    viewModel: GoalDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val merchants by viewModel.merchants.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf<MoneySheetMode?>(null) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showUpgrade by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state) { if (state is GoalDetailState.Gone) onNavigateBack() }
    LaunchedEffect(sheet.done) { if (sheet.done) { mode = null; viewModel.resetSheet() } }

    val detail = (state as? GoalDetailState.Loaded)?.detail
    val progress = detail?.progress
    fun open(m: MoneySheetMode) { viewModel.resetSheet(); mode = m }

    PennyWiseScaffold(
        title = progress?.goal?.name.orEmpty(),
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.goal_back))
            }
        },
        actions = {
            if (progress != null) {
                GoalOverflowMenu(
                    progress = progress,
                    onEdit = { onEdit(progress.goal.id) },
                    onRepeat = { viewModel.repeatForNextYear(onCreated = onOpenGoal, onBlocked = { showUpgrade = true }) },
                    onArchive = { viewModel.setArchived(progress.status != GoalStatus.ARCHIVED) },
                    onDelete = { showDelete = true },
                )
            }
        },
    ) { padding ->
        if (detail == null || progress == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@PennyWiseScaffold
        }
        GoalDetailContent(
            detail = detail,
            padding = padding,
            onAdd = { open(MoneySheetMode.Add) },
            onTakeOut = { open(MoneySheetMode.TakeOut) },
            merchants = merchants,
            // A row taken from a transaction opens it; the rest open the edit sheet.
            onContribution = { c -> c.transactionId?.let(onOpenTransaction) ?: open(MoneySheetMode.Edit(c)) },
        )
    }

    val goal = progress?.goal
    mode?.let { m ->
        if (goal == null) return@let
        MoneySheet(
            mode = m,
            goalCurrency = goal.currency,
            saved = progress.saved,
            state = sheet,
            onDismiss = { mode = null },
            onSave = { amount, currency, date, note, manual ->
                when (m) {
                    MoneySheetMode.Add -> viewModel.saveMovement(false, amount, currency, date, note, manual)
                    MoneySheetMode.TakeOut -> viewModel.saveMovement(true, amount, currency, date, note, manual)
                    is MoneySheetMode.Edit -> viewModel.saveContributionEdit(m.contribution, amount, date, note)
                }
            },
            onDelete = (m as? MoneySheetMode.Edit)?.let { e -> { viewModel.deleteContribution(e.contribution.id); mode = null } },
        )
    }

    if (showDelete && goal != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.goal_delete_title, goal.name)) },
            text = { Text(stringResource(R.string.goal_delete_message)) },
            confirmButton = {
                Button(
                    onClick = { showDelete = false; viewModel.delete() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.goal_menu_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.goal_action_cancel)) } },
        )
    }

    if (showUpgrade) UpgradeSheet(onDismiss = { showUpgrade = false })
}

@Composable
private fun GoalOverflowMenu(
    progress: GoalProgress,
    onEdit: () -> Unit,
    onRepeat: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val archived = progress.status == GoalStatus.ARCHIVED
    // Repeat fits a goal that has run its course: Reached, or Missed.
    val canRepeat = progress.status == GoalStatus.REACHED || progress.status == GoalStatus.MISSED
    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.goal_more))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.goal_menu_edit)) },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
            onClick = { open = false; onEdit() },
        )
        if (canRepeat) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.goal_menu_repeat)) },
                leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null) },
                onClick = { open = false; onRepeat() },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(if (archived) R.string.goal_menu_unarchive else R.string.goal_menu_archive)) },
            leadingIcon = { Icon(if (archived) Icons.Default.Unarchive else Icons.Default.Archive, contentDescription = null) },
            onClick = { open = false; onArchive() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.goal_menu_delete), color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            onClick = { open = false; onDelete() },
        )
    }
}

@Composable
private fun GoalDetailContent(
    detail: GoalDetail,
    padding: PaddingValues,
    onAdd: () -> Unit,
    onTakeOut: () -> Unit,
    merchants: Map<Long, String>,
    onContribution: (GoalContributionEntity) -> Unit,
) {
    val progress = detail.progress
    val goal = progress.goal
    val currency = goal.currency
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dimensions.Padding.content,
            end = Dimensions.Padding.content,
            top = padding.calculateTopPadding() + Spacing.sm,
            bottom = padding.calculateBottomPadding() + Spacing.Layout.scrollBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    GoalRing(progress, Dimensions.Component.goalRingLarge, Dimensions.Component.goalRingLargeStroke) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.budgets_percent, progress.percent()), style = MaterialTheme.typography.headlineMedium)
                            Text(
                                CurrencyFormatter.formatCurrency(progress.saved, currency),
                                style = PennyWiseText.amountSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        stringResource(
                            R.string.goals_amount_of,
                            CurrencyFormatter.formatCurrency(progress.saved, currency),
                            CurrencyFormatter.formatCurrency(goal.targetAmount, currency),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    GoalStatusPill(progress)
                    if (progress.over.signum() > 0) {
                        Text(
                            stringResource(R.string.goal_detail_over, CurrencyFormatter.formatCurrency(progress.over, currency)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.success,
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile(stringResource(R.string.goal_detail_left), CurrencyFormatter.formatCurrency(progress.left, currency), Modifier.weight(1f))
                StatTile(stringResource(R.string.goal_detail_per_month), CurrencyFormatter.formatCurrency(progress.monthlyDisplay(), currency), Modifier.weight(1f))
            }
        }
        item {
            GroupedList {
                GroupedRow(position = ListItemPosition.Single) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    RowLabels(title = stringResource(R.string.goal_edit_due), subtitle = goal.dueDate.format(goalDateFormat))
                    Text(
                        text = when (progress.status) {
                            GoalStatus.MISSED -> stringResource(R.string.goals_missed_by, CurrencyFormatter.formatCurrency(progress.left, currency))
                            GoalStatus.REACHED -> stringResource(R.string.goals_reached)
                            else -> goalTimeLabel(progress)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (progress.status) {
                            GoalStatus.MISSED -> MaterialTheme.colorScheme.error
                            GoalStatus.REACHED -> MaterialTheme.colorScheme.success
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
                    Text(stringResource(R.string.goal_add_money))
                }
                OutlinedButton(onClick = onTakeOut, enabled = progress.saved.signum() > 0, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
                    Text(stringResource(R.string.goal_take_out))
                }
            }
        }
        item { SectionHeaderV2(title = stringResource(R.string.goal_detail_history)) }
        val rows = detail.contributions
        val hasAlreadySaved = goal.alreadySaved.signum() > 0
        if (rows.isEmpty() && !hasAlreadySaved) {
            item {
                Text(
                    stringResource(R.string.goal_detail_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                val total = rows.size + if (hasAlreadySaved) 1 else 0
                GroupedList {
                    rows.forEachIndexed { i, c ->
                        ContributionRow(c, currency, c.transactionId?.let(merchants::get), ListItemPosition.from(i, total)) { onContribution(c) }
                    }
                    if (hasAlreadySaved) {
                        GroupedRow(position = ListItemPosition.from(total - 1, total)) {
                            RowLabels(
                                title = stringResource(R.string.goal_edit_already_saved),
                                subtitle = goal.createdAt.toLocalDate().format(goalDateFormat),
                            )
                            Text(
                                CurrencyFormatter.signed("+", CurrencyFormatter.formatCurrency(goal.alreadySaved, currency)),
                                style = PennyWiseText.amountRow,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    PennyWiseCardV2(modifier = modifier, contentPadding = Dimensions.Padding.cardCompact) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = PennyWiseText.amountMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ContributionRow(c: GoalContributionEntity, currency: String, merchant: String?, position: ListItemPosition, onClick: () -> Unit) {
    val withdrawal = c.amount.signum() < 0
    val subtitle = buildList {
        add(c.date.format(goalDateFormat))
        if (c.originalAmount != null && c.originalCurrency != null && !c.originalCurrency.equals(currency, ignoreCase = true)) {
            add(stringResource(R.string.goal_converted_from, CurrencyFormatter.formatCurrency(c.originalAmount.abs(), c.originalCurrency)))
        }
        c.note?.let(::add)
    }.joinToString(" · ")
    GroupedRow(position = position, onClick = onClick) {
        RowLabels(
            title = merchant ?: stringResource(if (withdrawal) R.string.goal_withdrawal else R.string.goal_contribution),
            subtitle = subtitle,
        )
        Text(
            CurrencyFormatter.signed(if (withdrawal) "-" else "+", CurrencyFormatter.formatCurrency(c.amount.abs(), currency)),
            style = PennyWiseText.amountRow,
            color = if (withdrawal) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.success,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoneySheet(
    mode: MoneySheetMode,
    goalCurrency: String,
    saved: BigDecimal,
    state: MoneySheetState,
    onDismiss: () -> Unit,
    onSave: (amount: BigDecimal?, currency: String, date: LocalDate, note: String, manual: BigDecimal?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val existing = (mode as? MoneySheetMode.Edit)?.contribution
    var amount by rememberSaveable { mutableStateOf(existing?.amount?.abs()?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var currency by rememberSaveable { mutableStateOf(goalCurrency) }
    var date by remember { mutableStateOf(existing?.date ?: LocalDate.now()) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var manual by rememberSaveable { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val askManual = state.error == MoneyError.NO_RATE || manual.isNotEmpty()
    val isWithdrawal = mode == MoneySheetMode.TakeOut || (existing != null && existing.amount.signum() < 0)
    val title = when (mode) {
        MoneySheetMode.Add -> R.string.goal_add_money
        MoneySheetMode.TakeOut -> R.string.goal_take_out
        is MoneySheetMode.Edit -> if (isWithdrawal) R.string.goal_edit_withdrawal else R.string.goal_edit_contribution
    }
    // An edited withdrawal can grow by what's saved plus what it already took out.
    val withdrawLimit = if (existing != null) saved + existing.amount.abs() else saved

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            if (isWithdrawal) {
                Text(
                    stringResource(R.string.goal_money_saved_so_far, CurrencyFormatter.formatCurrency(saved, goalCurrency)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CurrencyField(
                    currency = currency,
                    enabled = existing == null,
                    onSelect = { currency = it; manual = "" },
                    modifier = Modifier.weight(0.4f),
                )
                val amountError = when (state.error) {
                    MoneyError.AMOUNT_INVALID -> stringResource(R.string.goal_money_error_amount)
                    MoneyError.MORE_THAN_SAVED -> stringResource(
                        R.string.goal_money_error_more_than_saved, CurrencyFormatter.formatCurrency(withdrawLimit, goalCurrency)
                    )
                    else -> null
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.matches(amountInput)) amount = it },
                    label = { Text(stringResource(R.string.goal_money_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountError != null,
                    supportingText = amountError?.let { { Text(it) } },
                    modifier = Modifier.weight(0.6f),
                )
            }
            if (askManual && !currency.equals(goalCurrency, ignoreCase = true)) {
                Text(
                    stringResource(R.string.goal_money_no_rate, currency, goalCurrency),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = manual,
                    onValueChange = { if (it.matches(amountInput)) manual = it },
                    label = { Text(stringResource(R.string.goal_money_converted, goalCurrency)) },
                    prefix = { Text(CurrencyFormatter.getCurrencySymbol(goalCurrency)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = date.format(goalDateFormat),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.goal_money_date)) },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.goal_money_date))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.goal_money_note)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.error == MoneyError.FAILED) {
                Text(stringResource(R.string.goal_money_error_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.goal_contribution_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                Box(Modifier.weight(1f))
                Button(
                    onClick = { onSave(amount.toBigDecimalOrNull(), currency, date, note, manual.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }) },
                    enabled = !state.saving,
                ) { Text(stringResource(R.string.goal_money_save)) }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { date = it.toUtcDate() }
                    showDatePicker = false
                }) { Text(stringResource(R.string.goal_action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.goal_action_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}
