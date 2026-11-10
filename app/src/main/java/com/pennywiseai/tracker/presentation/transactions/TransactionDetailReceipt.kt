package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.ui.animations.pwExpand
import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.data.contacts.LocalMerchantDisplay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.LocalNavAnimatedVisibilityScope
import com.pennywiseai.tracker.ui.LocalSharedTransitionScope
import com.pennywiseai.tracker.ui.sharedElementIcon
import com.pennywiseai.tracker.ui.components.BrandIcon
import com.pennywiseai.tracker.ui.components.SettledCheck
import com.pennywiseai.tracker.ui.components.SplitBreakdownCard
import com.pennywiseai.tracker.ui.components.SplitItem
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.formatAmount
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.Dimensions
import java.math.BigDecimal
import com.pennywiseai.tracker.ui.icons.categoryLabel

// ==================== Clean detail read-only view ====================

@Composable
internal fun TransactionReceipt(
    transaction: TransactionEntity,
    settled: Boolean,
    primaryCurrency: String,
    convertedAmount: BigDecimal?,
    viewModel: TransactionDetailViewModel,
    splits: List<SplitItem>,
    hasSplits: Boolean,
    loan: LoanEntity?,
    onNavigateToLoanDetail: (Long) -> Unit,
    onUnmarkLoanClick: () -> Unit,
    accountProfileId: Long? = null,
    towardGoals: @Composable () -> Unit = {},
    splitWith: @Composable () -> Unit = {}
) {
    val typeColor = when (transaction.transactionType) {
        TransactionType.INCOME -> MaterialTheme.colorScheme.income
        TransactionType.EXPENSE -> MaterialTheme.colorScheme.onSurface
        TransactionType.CREDIT -> MaterialTheme.colorScheme.credit
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.transfer
        TransactionType.INVESTMENT -> MaterialTheme.colorScheme.investment
    }
    val sign = when (transaction.transactionType) {
        TransactionType.INCOME -> "+"
        TransactionType.EXPENSE -> "-"
        TransactionType.CREDIT -> ""
        TransactionType.TRANSFER -> ""
        TransactionType.INVESTMENT -> ""
    }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // ── Hero Header ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = Dimensions.Elevation.raisedCard
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val heroIconModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        sharedElementIcon(
                            key = "brand_icon_${transaction.id}",
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                } else {
                    Modifier
                }
                SettledCheck(settled = settled, size = Dimensions.Icon.hero, modifier = heroIconModifier) {
                    BrandIcon(
                        merchantName = transaction.merchantName,
                        category = transaction.category,
                        size = Dimensions.Icon.hero,
                        showBackground = true
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                // Prefer the user's saved alias (#583); else the contact-resolved
                // name when that toggle is on. The raw merchant is still what the
                // Edit field below renders so users can correct mis-detections.
                val merchantDisplay = LocalMerchantDisplay.current
                val currentAlias by viewModel.currentMerchantAlias.collectAsStateWithLifecycle()
                Text(
                    text = currentAlias
                        ?: merchantDisplay(transaction.merchantName)
                        ?: transaction.merchantName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                // Transaction type chip
                val typeLabel = stringResource(
                    when (transaction.transactionType) {
                        TransactionType.INCOME -> R.string.txn_type_income
                        TransactionType.EXPENSE -> R.string.txn_type_expense
                        TransactionType.CREDIT -> R.string.txn_type_credit
                        TransactionType.TRANSFER -> R.string.txn_type_transfer
                        TransactionType.INVESTMENT -> R.string.txn_type_investment
                    }
                )
                val typeIcon = when (transaction.transactionType) {
                    TransactionType.INCOME -> Icons.AutoMirrored.Filled.TrendingUp
                    TransactionType.EXPENSE -> Icons.AutoMirrored.Filled.TrendingDown
                    TransactionType.CREDIT -> Icons.Default.CreditCard
                    TransactionType.TRANSFER -> Icons.Default.SwapHoriz
                    TransactionType.INVESTMENT -> Icons.AutoMirrored.Filled.ShowChart
                }
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    icon = {
                        Icon(
                            typeIcon,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small),
                            tint = typeColor
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = typeColor.copy(alpha = 0.12f),
                        labelColor = typeColor,
                        iconContentColor = typeColor
                    ),
                    border = null as androidx.compose.foundation.BorderStroke?
                )

                // Loan chip
                val loanColor = MaterialTheme.colorScheme.loan
                Spacer(modifier = Modifier.height(Spacing.xs))
                if (loan != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        SuggestionChip(
                            onClick = { onNavigateToLoanDetail(loan.id) },
                            label = {
                                Text(
                                    text = if (loan.direction == LoanDirection.LENT)
                                        stringResource(R.string.txn_detail_loan_lent_to, loan.personName) else stringResource(R.string.txn_detail_loan_borrowed_from, loan.personName),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            icon = {
                                Icon(
                                    Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = loanColor
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = loanColor.copy(alpha = 0.12f),
                                labelColor = loanColor,
                                iconContentColor = loanColor
                            ),
                            border = null
                        )
                        // Unmark-as-loan (#444): removes the loan link; deletes the
                        // loan too if no other transactions are linked.
                        IconButton(onClick = onUnmarkLoanClick, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.txn_detail_unmark_loan),
                                modifier = Modifier.size(Dimensions.Icon.small),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (transaction.lentSharesTotal == null) {
                    // A split bill (#346) is already on Loans, one share per person.
                    SuggestionChip(
                        onClick = { viewModel.showMarkAsLoanSheet() },
                        label = {
                            Text(
                                text = stringResource(R.string.txn_detail_mark_as_loan),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.Icon.small)
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = null as androidx.compose.foundation.BorderStroke?
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                Text(
                    text = CurrencyFormatter.signed(sign, transaction.formatAmount()),
                    style = PennyWiseText.heroAmount,
                    color = typeColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = PennyWiseText.autoSizeFloor,
                        maxFontSize = PennyWiseText.heroAmount.fontSize
                    )
                )

                if (transaction.currency.isNotEmpty() &&
                    !transaction.currency.equals(primaryCurrency, ignoreCase = true) &&
                    convertedAmount != null
                ) {
                    Text(
                        text = "\u2248 ${CurrencyFormatter.formatCurrency(convertedAmount, primaryCurrency)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Exclude from analytics (#451) ── placed above the detail rows so its
        // Switch never sits under the bottom-right floating action buttons.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setExcludedFromAnalytics(!transaction.excludedFromAnalytics) }
                    .padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    Icons.Default.QueryStats,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.Icon.medium),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.txn_detail_exclude_analytics_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.txn_detail_exclude_analytics_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Display-only — the Row's clickable is the single toggle handler
                // so a tap can't fire the VM call twice (Greptile #454).
                Switch(
                    checked = transaction.excludedFromAnalytics,
                    onCheckedChange = null
                )
            }
        }

        towardGoals()

        splitWith()

        // ── Details Section ──
        Column(modifier = Modifier.fillMaxWidth()) {
            // Date & Time
            DetailInfoRow(
                icon = Icons.Default.CalendarToday,
                label = stringResource(R.string.txn_detail_label_date_time),
                value = transaction.dateTime.format(
                    AppDates.dateTimeFormatter(weekday = true)
                )
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Category
            val categoryValue = if (hasSplits && splits.isNotEmpty()) {
                pluralStringResource(R.plurals.txn_detail_split_categories, splits.size, splits.size)
            } else {
                categoryLabel(transaction.category)
            }
            DetailInfoRow(
                icon = Icons.Default.Category,
                label = stringResource(R.string.txn_detail_label_category),
                value = categoryValue
            )

            // Bank
            transaction.bankName?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailInfoRow(
                    icon = Icons.Default.AccountBalance,
                    label = stringResource(R.string.txn_detail_label_bank),
                    value = it
                )
            }

            // Description
            transaction.description?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailInfoRow(
                    icon = Icons.Default.Description,
                    label = stringResource(R.string.txn_detail_label_description),
                    value = CurrencyFormatter.maskAmountsIn(it)
                )
            }

            // Tags
            val detailTags by viewModel.transactionTags.collectAsStateWithLifecycle()
            if (detailTags.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailInfoRow(
                    icon = Icons.Default.Sell,
                    label = pluralStringResource(R.plurals.txn_detail_label_tags, detailTags.size),
                    value = detailTags.joinToString(", ")
                )
            }

            // Recurring
            if (transaction.isRecurring) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailInfoRow(
                    icon = Icons.Default.Repeat,
                    label = stringResource(R.string.txn_detail_label_status),
                    value = stringResource(R.string.txn_detail_status_recurring)
                )
            }

            // Classification
            val effectiveProfileId = transaction.profileId ?: accountProfileId
            val isEffectivelyBusiness = effectiveProfileId == ProfileEntity.BUSINESS_ID
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            DetailInfoRow(
                icon = if (isEffectivelyBusiness) Icons.Default.Business else Icons.Default.Person,
                label = stringResource(R.string.txn_detail_label_classification),
                value = if (isEffectivelyBusiness) stringResource(R.string.txn_detail_business) else stringResource(R.string.txn_detail_personal)
            )

            // Account info. A leg on an account without a number (a Cash account
            // is ("Cash", "")) shows its name instead of a blank chip (#913).
            fun maskedLeg(account: String, bank: String?): String = when {
                account.isBlank() -> bank ?: account
                account.length > 4 -> "*".repeat(account.length - 4) + account.takeLast(4)
                else -> account
            }
            if (transaction.fromAccount != null && transaction.toAccount != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                TransferFlowRow(
                    fromValue = maskedLeg(transaction.fromAccount, transaction.fromBankName),
                    toValue = maskedLeg(transaction.toAccount, transaction.toBankName)
                )
            } else {
                transaction.accountNumber?.takeIf { it.isNotBlank() }?.let {
                    if (transaction.fromAccount == null && transaction.toAccount == null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        val masked = if (it.length > 4) {
                            "*".repeat(it.length - 4) + it.takeLast(4)
                        } else it
                        DetailInfoRow(
                            icon = Icons.Default.AccountBalanceWallet,
                            label = stringResource(R.string.txn_detail_label_account),
                            value = masked
                        )
                    }
                }
                transaction.fromAccount?.let { from ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DetailInfoRow(
                        icon = Icons.Default.Output,
                        label = stringResource(R.string.txn_detail_label_from),
                        value = maskedLeg(from, transaction.fromBankName)
                    )
                }
                transaction.toAccount?.let { to ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    val masked = maskedLeg(to, transaction.toBankName)
                    DetailInfoRow(
                        icon = Icons.AutoMirrored.Filled.Input,
                        label = stringResource(R.string.txn_detail_label_to),
                        value = masked
                    )
                }
            }

            // Balance
            transaction.balanceAfter?.let {
                val accountCurrency by viewModel.accountCurrency.collectAsStateWithLifecycle()
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailInfoRow(
                    icon = Icons.Default.AccountBalanceWallet,
                    label = stringResource(R.string.txn_detail_label_balance),
                    value = CurrencyFormatter.formatCurrency(it, accountCurrency ?: transaction.currency)
                )
            }

            // Bank balance vs. ledger prediction (#734/#135): surface the gap and
            // offer to record it as an untracked transaction.
            val discrepancy by viewModel.balanceDiscrepancy.collectAsStateWithLifecycle()
            discrepancy?.let { d ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailInfoRow(
                    icon = Icons.Default.Warning,
                    label = stringResource(R.string.txn_detail_label_balance_mismatch),
                    value = stringResource(
                        R.string.txn_detail_balance_mismatch_value,
                        CurrencyFormatter.formatCurrency(d.delta.abs(), d.currency),
                        CurrencyFormatter.formatCurrency(d.expected, d.currency)
                    )
                )
                val adding by viewModel.isAddingAdjustment.collectAsStateWithLifecycle()
                var showAdjustmentSheet by rememberSaveable { mutableStateOf(false) }
                if (showAdjustmentSheet) {
                    BalanceAdjustmentSheet(
                        discrepancy = d,
                        viewModel = viewModel,
                        onDismiss = { showAdjustmentSheet = false }
                    )
                }
                TextButton(
                    onClick = { showAdjustmentSheet = true },
                    enabled = !adding,
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(
                        stringResource(
                            if (d.delta.signum() < 0) R.string.txn_detail_add_untracked_expense
                            else R.string.txn_detail_add_untracked_income,
                            CurrencyFormatter.formatCurrency(d.delta.abs(), d.currency)
                        )
                    )
                }
            }

            // Reference number (prefer extracted reference, fallback to SMS sender)
            val referenceValue = transaction.reference ?: transaction.smsSender
            referenceValue?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailInfoRow(
                    icon = Icons.Default.Tag,
                    label = stringResource(R.string.txn_detail_label_reference),
                    value = it
                )
            }
        }

        // ── Receipt Section ──
        val receiptUri by viewModel.receiptUri.collectAsStateWithLifecycle()
        receiptUri?.let { uri ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { viewModel.showFullScreenReceipt() },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.medium),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.txn_detail_receipt),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.txn_detail_receipt_tap_to_view),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    AsyncImage(
                        model = uri,
                        contentDescription = stringResource(R.string.txn_detail_receipt),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        val receiptMissing by viewModel.receiptMissing.collectAsStateWithLifecycle()
        if (receiptUri == null && receiptMissing) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        Icons.Default.Receipt,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.medium),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.txn_detail_receipt_missing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── SMS Section ──
        if (!transaction.smsBody.isNullOrBlank()) {
            ExpandableSmsSection(smsBody = transaction.smsBody)
        }

        // ── Split Breakdown ──
        if (hasSplits && splits.isNotEmpty()) {
            SplitBreakdownCard(
                splits = splits,
                currency = transaction.currency
            )
        }
    }
}

@Composable
private fun DetailInfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.medium),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TransferFlowRow(
    fromValue: String,
    toValue: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(
            Icons.Default.SwapHoriz,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.medium),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Text(
                text = fromValue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = Spacing.sm)
            )
        }

        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.small),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Text(
                text = toValue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = Spacing.sm)
            )
        }
    }
}

@Composable
private fun ExpandableSmsSection(smsBody: String) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Text(
                        text = if (expanded) stringResource(R.string.txn_detail_sms_hide) else stringResource(R.string.txn_detail_sms_show),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimensions.Icon.medium)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = pwExpand(),
                exit = pwCollapse()
            ) {
                Text(
                    text = CurrencyFormatter.maskAmountsIn(smsBody),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md)
                )
            }
        }
    }
}
