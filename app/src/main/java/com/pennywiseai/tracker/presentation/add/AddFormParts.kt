package com.pennywiseai.tracker.presentation.add

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.time.LocalDate
import java.time.ZoneOffset

/** The currency code beside an amount; tapping it picks another. */
@Composable
internal fun CurrencyButton(currency: String, onPick: (String) -> Unit, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    val description = stringResource(R.string.add_field_currency, currency)
    Box {
        TextButton(
            onClick = { open = true },
            enabled = enabled,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.semantics { contentDescription = description }
        ) {
            Text("${CurrencyFormatter.getCurrencySymbol(currency)} $currency", maxLines = 1)
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            CurrencyFormatter.getSupportedCurrencies().forEach { code ->
                DropdownMenuItem(
                    text = { Text("${CurrencyFormatter.getCurrencySymbol(code)} $code") },
                    onClick = {
                        open = false
                        onPick(code)
                    }
                )
            }
        }
    }
}

/**
 * A form value picked elsewhere (a sheet, a menu, a dialog): its label above its value, like
 * a filled field, on the grouped-row surface. [menu] hangs a dropdown off the row.
 */
@Composable
internal fun PickerRow(
    position: ListItemPosition,
    label: String,
    value: String,
    onClick: () -> Unit,
    leading: @Composable RowScope.() -> Unit,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    supporting: String? = null,
    menu: @Composable () -> Unit = {}
) {
    Box {
        GroupedRow(position = position, onClick = onClick) {
            leading()
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                supporting?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        menu()
    }
}

/** The optional fields, folded: a summary of what's filled (or what's inside) until opened. */
@Composable
internal fun MoreDetailsRow(position: ListItemPosition, expanded: Boolean, summary: String, onToggle: () -> Unit) {
    GroupedRow(
        position = position,
        onClick = onToggle,
        modifier = Modifier.semantics {
            if (expanded) collapse { onToggle(); true } else expand { onToggle(); true }
        }
    ) {
        Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.quick_add_more_details), style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Picks a day; [onPick] gets the picker's UTC-midnight millis, as the ViewModel expects. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddDatePickerDialog(initial: LocalDate, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let(onPick)
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(R.string.add_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.add_cancel)) }
        }
    ) {
        DatePicker(state = state)
    }
}
