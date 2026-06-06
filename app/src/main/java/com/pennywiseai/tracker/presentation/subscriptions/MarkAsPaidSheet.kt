package com.pennywiseai.tracker.presentation.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.ui.components.BrandIcon
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.expense_dark
import com.pennywiseai.tracker.ui.theme.expense_light
import com.pennywiseai.tracker.ui.theme.income_dark
import com.pennywiseai.tracker.ui.theme.income_light
import com.pennywiseai.tracker.utils.CurrencyFormatter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Bottom sheet for confirming a subscription payment / receipt (#412).
 *
 * Layout (top-down):
 *   - drag handle (single dismiss affordance — no redundant X)
 *   - eyebrow chip identifying the cadence ("MARK AS PAID" / "MARK AS RECEIVED")
 *   - brand icon + merchant name (compact header row)
 *   - hero amount — sized to the app's hero convention (headlineLarge Bold)
 *   - "When did you pay?" with 3 date chips (Today / Yesterday / Pick…)
 *   - CTA button with the amount baked into the label
 *   - small disclaimer about the cycle being advanced
 *
 * Adapts to subscription direction:
 *   - EXPENSE → expense palette + "Mark as paid"
 *   - INCOME  → income (teal) palette + "Mark as received"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkAsPaidSheet(
    subscription: SubscriptionEntity,
    isPaidThisCycle: Boolean = false,
    candidates: List<TransactionEntity> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    onLinkExisting: (TransactionEntity) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf(today) }
    var showDatePicker by remember { mutableStateOf(false) }

    val isIncome = subscription.direction == SubscriptionDirection.INCOME
    val amountColor = if (isIncome) {
        if (androidx.compose.foundation.isSystemInDarkTheme()) income_dark else income_light
    } else {
        if (androidx.compose.foundation.isSystemInDarkTheme()) expense_dark else expense_light
    }
    val eyebrowText = if (isIncome) "MARK AS RECEIVED" else "MARK AS PAID"
    val ctaPrefix = if (isIncome) "Mark received" else "Mark paid"

    val confirmAndDismiss: () -> Unit = {
        onConfirm(selectedDate)
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    val linkAndDismiss: (TransactionEntity) -> Unit = { txn ->
        onLinkExisting(txn)
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets.navigationBars },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Dimensions.Padding.content,
                    vertical = Spacing.md,
                ),
        ) {
            // Eyebrow chip — financial-document micro-typography matching
            // the paywall sheet's pattern (gold there, primary here).
            EyebrowChip(
                text = eyebrowText,
                isAccent = isIncome,
            )
            Spacer(Modifier.height(Spacing.md))

            // Brand + merchant row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                BrandIcon(
                    merchantName = subscription.merchantName,
                    size = 44.dp,
                    showBackground = true,
                )
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subscription.merchantName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    subscription.category?.let { cat ->
                        Text(
                            text = cat,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))

            // Hero amount — typography carries the moment. headlineLarge
            // matches the rest of the app's big-number convention
            // (SummaryCardV2 uses the same size).
            Text(
                text = CurrencyFormatter.formatCurrency(subscription.amount, subscription.currency),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = amountColor,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.lg))

            // Already-paid notice — shown when this cycle was marked paid
            // already. Truth comes from the screen's VM (today-anchored
            // cycle check), passed in via [isPaidThisCycle] so the sheet
            // and row badge can't disagree.
            if (isPaidThisCycle) {
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(Dimensions.CornerRadius.large),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = Dimensions.Padding.card,
                            vertical = Spacing.md,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = "Already marked paid on ${subscription.lastPaidAt?.format(DateTimeFormatter.ofPattern("d MMM"))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }

            // Suggested-payment candidates (#412 follow-up). When the
            // subscription is on auto-pay, the bank SMS already created
            // EXPENSE rows for the merchant — tap one of these to link
            // (advances the schedule, no duplicate phantom row created).
            if (candidates.isNotEmpty()) {
                Text(
                    text = if (candidates.size == 1) {
                        "Found 1 matching payment"
                    } else {
                        "Found ${candidates.size} matching payments"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    candidates.forEach { txn ->
                        CandidateCard(txn, onClick = { linkAndDismiss(txn) })
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
                OrDivider()
                Spacer(Modifier.height(Spacing.lg))
            }

            // Date selector — chips for the common cases (today, yesterday)
            // + a "Pick date" option that defers to the system date picker.
            Text(
                text = "When?",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            DateChips(
                selected = selectedDate,
                today = today,
                onSelect = { selectedDate = it },
                onPickDate = { showDatePicker = true },
            )
            Spacer(Modifier.height(Spacing.lg))

            // CTA
            Button(
                onClick = confirmAndDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimensions.Component.buttonHeight),
                shape = RoundedCornerShape(Dimensions.CornerRadius.large),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                contentPadding = PaddingValues(horizontal = Spacing.md),
            ) {
                Text(
                    text = "$ctaPrefix · ${CurrencyFormatter.formatCurrency(subscription.amount, subscription.currency)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(Spacing.sm))

            // Quiet helper about cycle behavior so the user understands
            // why the subscription will "go away" from the due list.
            Text(
                text = if (isIncome) {
                    "We'll log a ${subscription.currency} income and roll the schedule to the next cycle."
                } else {
                    "We'll log the expense and roll the schedule to the next cycle."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.sm))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC)
                .toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = java.time.Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * One suggested-payment card. Compact row: amount on the left (in expense
 * tint to mirror the transactions list), merchant + relative date on the
 * right. Tap = link this transaction as the cycle's payment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CandidateCard(
    txn: TransactionEntity,
    onClick: () -> Unit,
) {
    val amountColor = if (androidx.compose.foundation.isSystemInDarkTheme()) expense_dark else expense_light
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Dimensions.CornerRadius.large),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Dimensions.Padding.card,
                vertical = Spacing.md,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = CurrencyFormatter.formatCurrency(txn.amount, txn.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor,
                )
                Text(
                    text = relativeDate(txn.dateTime.toLocalDate()) +
                        (txn.bankName?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = "Link this payment",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** "─── or mark today ───" divider sitting between the candidates list and the date selector. */
@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = "or mark today",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

private fun relativeDate(date: LocalDate): String {
    val today = LocalDate.now()
    val days = java.time.temporal.ChronoUnit.DAYS.between(date, today)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Yesterday"
        days in 2..6 -> "$days days ago"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM"))
    }
}

/** Tiny letter-spaced uppercase label. Matches the paywall eyebrow pattern. */
@Composable
private fun EyebrowChip(text: String, isAccent: Boolean) {
    val container = if (isAccent) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val content = if (isAccent) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
    androidx.compose.material3.Surface(
        color = container,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = content,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = 6.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateChips(
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onPickDate: () -> Unit,
) {
    val yesterday = today.minusDays(1)
    val isToday = selected == today
    val isYesterday = selected == yesterday
    val isCustom = !isToday && !isYesterday

    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        FilterChip(
            selected = isToday,
            onClick = { onSelect(today) },
            label = { Text("Today") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
        FilterChip(
            selected = isYesterday,
            onClick = { onSelect(yesterday) },
            label = { Text("Yesterday") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
        FilterChip(
            selected = isCustom,
            onClick = onPickDate,
            label = {
                Text(
                    text = if (isCustom) {
                        selected.format(DateTimeFormatter.ofPattern("MMM d"))
                    } else {
                        "Pick date"
                    },
                )
            },
            leadingIcon = if (isCustom) null else {
                {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
    }
}
