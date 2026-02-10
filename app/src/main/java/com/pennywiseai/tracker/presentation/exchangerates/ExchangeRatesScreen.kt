package com.pennywiseai.tracker.presentation.exchangerates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pennywiseai.tracker.data.database.entity.ExchangeRateEntity
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeRatesScreen(
    viewModel: ExchangeRatesViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    var editingRate by remember { mutableStateOf<ExchangeRateEntity?>(null) }

    PennyWiseScaffold(
        title = "Exchange Rates",
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(
                onClick = { viewModel.refreshRates() },
                enabled = !uiState.isRefreshing
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh rates")
            }
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.rates.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No exchange rates available",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = "Rates will appear when you have transactions in multiple currencies",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(Dimensions.Padding.content),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    // Last updated
                    uiState.lastUpdated?.let { lastUpdated ->
                        item {
                            Text(
                                text = "Last updated: ${lastUpdated.format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(Spacing.sm))
                        }
                    }

                    // Refreshing indicator
                    if (uiState.isRefreshing) {
                        item {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(Spacing.sm))
                        }
                    }

                    // Rate cards
                    items(
                        items = uiState.rates,
                        key = { "${it.fromCurrency}_${it.toCurrency}" }
                    ) { rate ->
                        ExchangeRateCard(
                            rate = rate,
                            onClick = { editingRate = rate }
                        )
                    }

                    // Bottom info + reset button
                    val hasCustomRates = uiState.rates.any { it.isCustomRate }
                    item {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Text(
                            text = "Tap a rate to set a custom value. Custom rates are preserved across API refreshes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (hasCustomRates) {
                            Spacer(modifier = Modifier.height(Spacing.md))
                            OutlinedButton(
                                onClick = { viewModel.clearAllCustomRates() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Reset All to Auto")
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))
                    }
                }
            }
        }
    }

    // Edit rate dialog
    editingRate?.let { rate ->
        EditRateDialog(
            rate = rate,
            onDismiss = { editingRate = null },
            onSetCustomRate = { newRate ->
                viewModel.setCustomRate(rate.fromCurrency, rate.toCurrency, newRate)
                editingRate = null
            },
            onResetToAuto = {
                viewModel.clearCustomRate(rate.fromCurrency, rate.toCurrency)
                editingRate = null
            }
        )
    }
}

@Composable
private fun ExchangeRateCard(
    rate: ExchangeRateEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${CurrencyFormatter.getCurrencySymbol(rate.fromCurrency)} ${rate.fromCurrency}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "  \u2192  ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${CurrencyFormatter.getCurrencySymbol(rate.toCurrency)} ${rate.toCurrency}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = rate.rate.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (rate.isCustomRate) "Custom" else "API",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (rate.isCustomRate)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EditRateDialog(
    rate: ExchangeRateEntity,
    onDismiss: () -> Unit,
    onSetCustomRate: (BigDecimal) -> Unit,
    onResetToAuto: () -> Unit
) {
    var rateText by remember {
        mutableStateOf(rate.rate.setScale(6, java.math.RoundingMode.HALF_UP).toPlainString())
    }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${rate.fromCurrency} \u2192 ${rate.toCurrency}")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = "1 ${rate.fromCurrency} = ? ${rate.toCurrency}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = rateText,
                    onValueChange = {
                        rateText = it
                        isError = it.toBigDecimalOrNull() == null || (it.toBigDecimalOrNull() ?: BigDecimal.ZERO) <= BigDecimal.ZERO
                    },
                    label = { Text("Exchange Rate") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("Enter a valid positive number") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (rate.isCustomRate) {
                    TextButton(
                        onClick = onResetToAuto,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset to Auto")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newRate = rateText.toBigDecimalOrNull()
                    if (newRate != null && newRate > BigDecimal.ZERO) {
                        onSetCustomRate(newRate)
                    }
                },
                enabled = !isError && rateText.isNotBlank()
            ) {
                Text("Set Custom Rate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
