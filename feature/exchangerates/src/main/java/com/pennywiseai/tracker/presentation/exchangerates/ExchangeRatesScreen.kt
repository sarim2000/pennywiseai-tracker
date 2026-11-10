package com.pennywiseai.tracker.presentation.exchangerates

import com.pennywiseai.feature.exchangerates.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pennywiseai.tracker.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * The app's shared pieces come in as parameters, so this module needs nothing from
 * :app. `navigation/ExchangeRatesEntry.kt` in :app supplies them.
 *
 * @param topBar the app's top bar, given the title, both scroll behaviors, the haze
 *   source and this screen's actions (the refresh button). It owns the back button.
 * @param formatDateTime the "last updated" date and time.
 * @param currencySymbol the symbol shown before a currency code.
 * @param cancelLabel the edit dialog's dismiss button.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExchangeRatesScreen(
    topBar: @Composable (
        title: String,
        scrollBehaviorSmall: TopAppBarScrollBehavior,
        scrollBehaviorLarge: TopAppBarScrollBehavior,
        hazeState: HazeState,
        actions: @Composable () -> Unit,
    ) -> Unit,
    formatDateTime: (LocalDateTime) -> String,
    currencySymbol: (String) -> String,
    cancelLabel: String,
    viewModel: ExchangeRatesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    var editingRate by remember { mutableStateOf<ExchangeRateSnapshot?>(null) }

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            topBar(
                stringResource(R.string.exchange_rates_title),
                scrollBehaviorSmall,
                scrollBehaviorLarge,
                hazeState,
            ) {
                IconButton(
                    onClick = { viewModel.refreshRates() },
                    enabled = !uiState.isRefreshing,
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.exchange_rates_refresh))
                }
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
                    LoadingIndicator()
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
                            text = stringResource(R.string.exchange_rates_empty_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = stringResource(R.string.exchange_rates_empty_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                val lazyListState = rememberLazyListState()
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState)
                        .background(MaterialTheme.colorScheme.background)
                        .overScrollVertical(),
                    contentPadding = PaddingValues(
                        start = Dimensions.Padding.content,
                        end = Dimensions.Padding.content,
                        top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                        bottom = 0.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
                ) {
                    // Last updated
                    uiState.lastUpdated?.let { lastUpdated ->
                        item {
                            Text(
                                text = stringResource(R.string.exchange_rates_last_updated, formatDateTime(lastUpdated)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(Spacing.sm))
                        }
                    }

                    // Refreshing indicator
                    if (uiState.isRefreshing) {
                        item {
                            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                            currencySymbol = currencySymbol,
                            onClick = { editingRate = rate },
                            modifier = Modifier.animateItem()
                        )
                    }

                    // Bottom info + reset button
                    val hasCustomRates = uiState.rates.any { it.isCustomRate }
                    item {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Text(
                            text = stringResource(R.string.exchange_rates_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (hasCustomRates) {
                            Spacer(modifier = Modifier.height(Spacing.md))
                            OutlinedButton(
                                onClick = { viewModel.clearAllCustomRates() },
                                modifier = Modifier.fillMaxWidth(),
                                shapes = ButtonDefaults.shapes()
                            ) {
                                Text(stringResource(R.string.exchange_rates_reset_all))
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
            cancelLabel = cancelLabel,
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
    rate: ExchangeRateSnapshot,
    currencySymbol: (String) -> String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
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
                        text = "${currencySymbol(rate.fromCurrency)} ${rate.fromCurrency}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "  \u2192  ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${currencySymbol(rate.toCurrency)} ${rate.toCurrency}",
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
                    text = if (rate.isCustomRate) stringResource(R.string.exchange_rates_badge_custom) else stringResource(R.string.exchange_rates_badge_api),
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
    rate: ExchangeRateSnapshot,
    cancelLabel: String,
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
                TextField(
                    value = rateText,
                    onValueChange = {
                        rateText = it
                        isError = it.toBigDecimalOrNull() == null || (it.toBigDecimalOrNull() ?: BigDecimal.ZERO) <= BigDecimal.ZERO
                    },
                    label = { Text(stringResource(R.string.exchange_rates_rate_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = isError,
                    supportingText = if (isError) {
                        { Text(stringResource(R.string.exchange_rates_invalid_rate)) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )

                if (rate.isCustomRate) {
                    TextButton(
                        onClick = onResetToAuto,
                        modifier = Modifier.fillMaxWidth(),
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text(stringResource(R.string.exchange_rates_reset))
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
                enabled = !isError && rateText.isNotBlank(),
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.exchange_rates_set_custom))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(cancelLabel)
            }
        }
    )
}
