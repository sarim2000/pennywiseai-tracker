package com.pennywiseai.tracker.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.presentation.add.QuickAddActivity
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.time.LocalDate

/** A one-tap subscription: brand names stay as is, generic ones are translated. Category is a built-in key. */
private data class SubscriptionPreset(val name: String?, val labelRes: Int?, val category: String)

private val PRESETS = listOf(
    SubscriptionPreset("Netflix", null, "Entertainment"),
    SubscriptionPreset("Shahid", null, "Entertainment"),
    SubscriptionPreset("Spotify", null, "Entertainment"),
    SubscriptionPreset("YouTube Premium", null, "Entertainment"),
    SubscriptionPreset("iCloud", null, "Bills & Utilities"),
    SubscriptionPreset(null, R.string.onboarding_subs_mobile, "Mobile"),
    SubscriptionPreset(null, R.string.onboarding_subs_internet, "Bills & Utilities"),
    SubscriptionPreset(null, R.string.onboarding_subs_gym, "Fitness"),
    SubscriptionPreset(null, R.string.onboarding_subs_rent, "Bills & Utilities"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickSetupStep(
    uiState: OnBoardingUiState,
    viewModel: OnBoardingViewModel
) {
    // Name + built-in category of the preset being added; "" name = "Other" (user types it).
    var adding by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = stringResource(R.string.onboarding_quick_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(R.string.onboarding_quick_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )

        QuickSetupSection(
            title = stringResource(R.string.onboarding_payday_title),
            body = stringResource(R.string.onboarding_payday_body)
        ) {
            OnboardingPicker(
                options = (1..31).toList(),
                selected = uiState.paydayDay,
                label = rememberDayLabel(),
                onSelect = viewModel::setPayday,
                supportingText = if (uiState.paydayDetected) stringResource(R.string.onboarding_payday_detected) else null
            )
        }

        QuickSetupSection(
            title = stringResource(R.string.onboarding_subs_title),
            body = stringResource(
                if (uiState.subscriptions.any { it.fromScan }) R.string.onboarding_subs_body_found
                else R.string.onboarding_subs_body_empty
            )
        ) {
            uiState.subscriptions.forEach { choice ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = choice.selected,
                            role = Role.Checkbox,
                            onValueChange = { viewModel.toggleSubscription(choice) }
                        )
                ) {
                    Checkbox(checked = choice.selected, onCheckedChange = null)
                    Text(
                        text = choice.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = CurrencyFormatter.formatCurrency(choice.amount, choice.currency),
                        style = PennyWiseText.amountRow
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PRESETS.forEach { preset ->
                    val name = preset.name ?: stringResource(preset.labelRes!!)
                    if (uiState.subscriptions.none { it.name.equals(name, ignoreCase = true) }) {
                        AssistChip(
                            onClick = { adding = name to preset.category },
                            label = { Text(name) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                        )
                    }
                }
                AssistChip(
                    onClick = { adding = "" to "Others" },
                    label = { Text(stringResource(R.string.onboarding_subs_other)) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                )
            }
        }

        QuickSetupSection(
            title = stringResource(R.string.onboarding_cash_title),
            body = stringResource(R.string.onboarding_cash_body),
            checked = uiState.usesCash,
            onCheckedChange = viewModel::setUsesCash
        ) {
            if (uiState.usesCash) {
                AmountField(
                    value = uiState.cashBalance,
                    onValueChange = viewModel::updateCashBalance,
                    label = stringResource(R.string.onboarding_cash_balance_label),
                    currency = uiState.baseCurrency
                )
            }
        }

        QuickSetupSection(
            title = stringResource(R.string.onboarding_budget_title),
            body = stringResource(R.string.onboarding_budget_body)
        ) {
            AmountField(
                value = uiState.monthlyBudget,
                onValueChange = viewModel::updateMonthlyBudget,
                label = stringResource(R.string.onboarding_budget_label),
                currency = uiState.baseCurrency
            )
        }

        QuickSetupSection(
            title = stringResource(R.string.onboarding_lock_title),
            body = stringResource(
                if (uiState.appLockAvailable) R.string.onboarding_lock_body else R.string.onboarding_lock_unavailable
            ),
            checked = uiState.appLockEnabled,
            onCheckedChange = viewModel::setAppLock.takeIf { uiState.appLockAvailable }
        )
    }

    adding?.let { (presetName, category) ->
        AddSubscriptionDialog(
            presetName = presetName,
            currency = uiState.baseCurrency,
            onDismiss = { adding = null },
            onAdd = { name, amount, day ->
                viewModel.addSubscription(name, category, amount, day)
                adding = null
            }
        )
    }
}

/** A titled card; with [onCheckedChange] the title row carries a switch (null + [checked] = disabled switch). */
@Composable
private fun QuickSetupSection(
    title: String,
    body: String,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // The whole row toggles, so the switch is announced with the title and body.
            val toggle = if (checked != null) {
                Modifier.toggleable(
                    value = checked,
                    enabled = onCheckedChange != null,
                    role = Role.Switch,
                    onValueChange = { onCheckedChange?.invoke(it) }
                )
            } else Modifier
            Row(verticalAlignment = Alignment.CenterVertically, modifier = toggle) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (checked != null) {
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Switch(checked = checked, onCheckedChange = null, enabled = onCheckedChange != null)
                }
            }
            content()
        }
    }
}

/** Amount entry read as Quick Add reads it: any-script digits, "٫" and the locale's decimal comma. */
@Composable
private fun AmountField(value: String, onValueChange: (String) -> Unit, label: String, currency: String) {
    val configuration = LocalConfiguration.current
    val decimalComma = remember(configuration) {
        DecimalFormatSymbols.getInstance(configuration.locales[0]).decimalSeparator == ','
    }
    OutlinedTextField(
        value = value,
        onValueChange = { typed -> QuickAddActivity.normalizeAmount(typed, decimalComma)?.let(onValueChange) },
        label = { Text(label) },
        prefix = { Text(CurrencyFormatter.getCurrencySymbol(currency) + " ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AddSubscriptionDialog(
    presetName: String,
    currency: String,
    onDismiss: () -> Unit,
    onAdd: (name: String, amount: BigDecimal, day: Int) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(presetName) }
    var amount by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf(LocalDate.now().dayOfMonth) }
    val parsed = amount.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.onboarding_subs_add_title)) },
        text = {
            // Scrolls so a large font or the keyboard can't push the fields out of reach.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (presetName.isEmpty()) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.onboarding_subs_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(text = presetName, style = MaterialTheme.typography.titleMedium)
                }
                AmountField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = stringResource(R.string.onboarding_subs_amount_label),
                    currency = currency
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.onboarding_subs_day_label),
                    style = MaterialTheme.typography.labelLarge
                )
                OnboardingPicker(
                    options = (1..31).toList(),
                    selected = day,
                    label = rememberDayLabel(),
                    onSelect = { day = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name.trim(), parsed!!, day) },
                enabled = parsed != null && name.isNotBlank()
            ) { Text(stringResource(R.string.onboarding_subs_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

/** "Day 25" — a plain lambda for [OnboardingPicker]'s non-composable label. */
@Composable
private fun rememberDayLabel(): (Int) -> String {
    val resources = LocalContext.current.resources
    return { day -> resources.getString(R.string.onboarding_payday_day, day) }
}
