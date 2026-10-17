package com.pennywiseai.tracker.presentation.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.toColorOr
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.success
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

/**
 * Add to goal (#914): pick a goal and how much of [transaction] goes toward it.
 * The amount starts at what's left of the transaction and is in its currency;
 * the date is the transaction's and can't change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToGoalSheet(
    transaction: TransactionEntity,
    goals: List<GoalProgress>,
    state: AddToGoalState,
    onDismiss: () -> Unit,
    onSave: (goal: GoalEntity, share: BigDecimal?, note: String, manual: BigDecimal?) -> Unit,
    onCreateGoal: () -> Unit,
    onEdited: () -> Unit,
) {
    val left = state.left
    val currency = transaction.currency
    var goalId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = goals.firstOrNull { it.goal.id == goalId } ?: goals.firstOrNull()
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var manual by rememberSaveable { mutableStateOf("") }
    // Prefill once the unallocated amount has loaded.
    LaunchedEffect(left != null) { if (left != null && left.signum() > 0 && amount.isEmpty()) amount = left.stripTrailingZeros().toPlainString() }
    val goalCurrency = selected?.goal?.currency
    val askManual = goalCurrency != null && goalCurrency != currency && (state.error == MoneyError.NO_RATE || manual.isNotEmpty())

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.goal_add_to_goal), style = MaterialTheme.typography.titleMedium)
            if (left != null) {
                Text(
                    text = if (left.signum() > 0) {
                        stringResource(R.string.goal_add_to_goal_left, CurrencyFormatter.formatCurrency(left, currency))
                    } else {
                        stringResource(R.string.goal_add_to_goal_none_left)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (goals.isEmpty()) {
                Text(stringResource(R.string.goal_add_to_goal_no_goals), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onCreateGoal, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.goals_empty_action)) }
                return@Column
            }

            GroupedList {
                goals.forEachIndexed { i, p ->
                    GoalPickRow(p, selected = p.goal.id == selected?.goal?.id, ListItemPosition.from(i, goals.size)) {
                        goalId = p.goal.id
                        manual = ""
                        onEdited()
                    }
                }
            }

            val amountError = when (state.error) {
                MoneyError.AMOUNT_INVALID -> stringResource(R.string.goal_money_error_amount)
                MoneyError.MORE_THAN_LEFT -> stringResource(
                    R.string.goal_money_error_more_than_left, CurrencyFormatter.formatCurrency(left ?: BigDecimal.ZERO, currency)
                )
                else -> null
            }
            OutlinedTextField(
                value = amount,
                onValueChange = { if (it.matches(amountInput)) { amount = it; onEdited() } },
                label = { Text(stringResource(R.string.goal_money_amount)) },
                prefix = { Text(CurrencyFormatter.getCurrencySymbol(currency)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = amountError != null,
                supportingText = amountError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            if (askManual && goalCurrency != null) {
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
                value = transaction.dateTime.toLocalDate().format(goalDateFormat),
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(stringResource(R.string.goal_money_date)) },
                supportingText = { Text(stringResource(R.string.goal_add_to_goal_date_hint)) },
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
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(Modifier.weight(1f))
                OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.goal_action_cancel)) }
                Button(
                    onClick = {
                        selected?.let {
                            onSave(it.goal, amount.toBigDecimalOrNull(), note, manual.toBigDecimalOrNull()?.takeIf { m -> m.signum() > 0 })
                        }
                    },
                    enabled = !state.saving && left != null && left.signum() > 0 && selected != null,
                ) { Text(stringResource(R.string.goal_add_to_goal)) }
            }
        }
    }
}

@Composable
private fun GoalPickRow(progress: GoalProgress, selected: Boolean, position: ListItemPosition, onClick: () -> Unit) {
    val goal = progress.goal
    GroupedRow(
        position = position,
        onClick = onClick,
        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        GoalRing(progress, Dimensions.Component.goalRingSmall, Dimensions.Component.goalRingSmallStroke) {}
        RowLabels(
            title = goal.name,
            subtitle = stringResource(
                R.string.goals_amount_of,
                CurrencyFormatter.formatCurrency(progress.saved, goal.currency),
                CurrencyFormatter.formatCurrency(goal.targetAmount, goal.currency),
            ),
        )
        RadioButton(selected = selected, onClick = onClick)
    }
}

/**
 * "Toward goals" on a transaction: each goal it was added to, with the amount.
 * A row opens its goal; the close button takes the money back out of that goal.
 */
@Composable
fun TowardGoalsSection(linked: List<LinkedGoal>, onOpenGoal: (Long) -> Unit, onRemove: (Long) -> Unit, modifier: Modifier = Modifier) {
    val rows = linked.filter { it.goal != null }
    if (rows.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)) {
        Text(
            stringResource(R.string.goal_toward_goals),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        GroupedList {
            rows.forEachIndexed { i, (c, goal) ->
                goal!!
                GroupedRow(position = ListItemPosition.from(i, rows.size), onClick = { onOpenGoal(goal.id) }) {
                    Box(
                        Modifier
                            .size(Dimensions.Component.legendDot)
                            .background(goal.color.toColorOr(MaterialTheme.colorScheme.primary), CircleShape)
                    )
                    val converted = c.originalAmount != null && c.originalCurrency != null && c.originalCurrency != goal.currency
                    RowLabels(
                        title = goal.name,
                        subtitle = if (converted) {
                            stringResource(R.string.goal_converted_from, CurrencyFormatter.formatCurrency(c.originalAmount!!.abs(), c.originalCurrency!!))
                        } else null,
                    )
                    Text(
                        "+" + CurrencyFormatter.formatCurrency(c.amount, goal.currency),
                        style = PennyWiseText.amountRow,
                        color = MaterialTheme.colorScheme.success,
                    )
                    IconButton(onClick = { onRemove(c.id) }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.goal_toward_remove, goal.name),
                            modifier = Modifier.size(Dimensions.Icon.small),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
