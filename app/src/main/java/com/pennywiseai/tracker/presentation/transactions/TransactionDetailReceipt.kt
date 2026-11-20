package com.pennywiseai.tracker.presentation.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.contacts.LocalMerchantDisplay
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.formatAmount
import com.pennywiseai.tracker.domain.loan.LoanMath
import com.pennywiseai.tracker.ui.LocalNavAnimatedVisibilityScope
import com.pennywiseai.tracker.ui.LocalSharedTransitionScope
import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
import com.pennywiseai.tracker.ui.components.SettledCheck
import com.pennywiseai.tracker.ui.components.SplitBreakdownCard
import com.pennywiseai.tracker.ui.components.SplitItem
import com.pennywiseai.tracker.ui.components.cards.GroupedColumn
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.TransactionAvatar
import com.pennywiseai.tracker.ui.components.cards.isUnsortedCategory
import com.pennywiseai.tracker.ui.components.mascot.Mascot
import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.sharedElementIcon
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.ui.theme.spending
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

// ==================== Clean detail read-only view ====================

/** One row of a detail group, drawn at the [ListItemPosition] the group gives it. */
private typealias DetailRowSlot = @Composable (ListItemPosition) -> Unit

/** A connected block of detail rows: rounded outer corners, 2dp gutters. */
@Composable
private fun DetailGroup(rows: List<DetailRowSlot>) {
    if (rows.isEmpty()) return
    GroupedList {
        rows.forEachIndexed { index, row -> row(ListItemPosition.from(index, rows.size)) }
    }
}

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
    splitWith: @Composable () -> Unit = {},
    whatWasThis: @Composable () -> Unit = {},
    onCategoryClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    /** True when [whatWasThis] will draw a row, so the unsorted nudge can step aside. */
    hasClassificationAction: Boolean = false
) {
    // Prefer the user's saved alias (#583); else the contact-resolved name when
    // that toggle is on. The raw merchant is still what the editor shows.
    val merchantDisplay = LocalMerchantDisplay.current
    val currentAlias by viewModel.currentMerchantAlias.collectAsStateWithLifecycle()
    val merchantName = currentAlias ?: merchantDisplay(transaction.merchantName) ?: transaction.merchantName
    val ruleSuggestion by viewModel.ruleSuggestion.collectAsStateWithLifecycle()
    val unsorted = isUnsortedCategory(transaction.category) && !(hasSplits && splits.isNotEmpty())

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        DetailHero(
            transaction = transaction,
            merchantName = merchantName,
            settled = settled,
            primaryCurrency = primaryCurrency,
            convertedAmount = convertedAmount,
            loan = loan,
            onNavigateToLoanDetail = onNavigateToLoanDetail,
            onUnmarkLoanClick = onUnmarkLoanClick
        )

        DetailGroup(
            primaryRows(
                transaction = transaction,
                splits = splits,
                hasSplits = hasSplits,
                onCategoryClick = onCategoryClick,
                onEditClick = onEditClick
            )
        )

        // One inline action for what's still unresolved: the remember-this offer
        // after a category change, else "What was this?", else a nudge to sort it.
        if (ruleSuggestion != null) {
            RuleSuggestionCard(
                // The raw merchant: that is what the saved category is keyed on.
                text = stringResource(R.string.txn_detail_remember_prompt, transaction.merchantName, categoryLabel(ruleSuggestion!!)),
                onCreate = viewModel::acceptRuleSuggestion,
                onDismiss = viewModel::dismissRuleSuggestion
            )
        } else if (unsorted && !hasClassificationAction) {
            MascotNote(
                text = stringResource(R.string.txn_detail_uncategorized_hint),
                mood = MascotMood.Thinking,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        } else {
            whatWasThis()
        }

        towardGoals()

        splitWith()

        // ── Split Breakdown ──
        if (hasSplits && splits.isNotEmpty()) {
            SplitBreakdownCard(
                splits = splits,
                currency = transaction.currency
            )
        }

        // ── More details: everything the SMS said, tucked away until asked for ──
        val detailTags by viewModel.transactionTags.collectAsStateWithLifecycle()
        val accountCurrency by viewModel.accountCurrency.collectAsStateWithLifecycle()
        val discrepancy by viewModel.balanceDiscrepancy.collectAsStateWithLifecycle()
        val receiptUri by viewModel.receiptUri.collectAsStateWithLifecycle()
        val receiptMissing by viewModel.receiptMissing.collectAsStateWithLifecycle()
        var moreExpanded by rememberSaveable { mutableStateOf(false) }
        // A real balance mismatch stays in view beside the account.
        val showMore = moreExpanded || discrepancy != null
        GroupedRow(
            position = ListItemPosition.Single,
            onClick = { moreExpanded = !showMore }
        ) {
            DetailIcon(Icons.Outlined.Info)
            Text(
                stringResource(R.string.txn_detail_more_details),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (showMore) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.Icon.medium)
            )
        }
        AnimatedVisibility(visible = showMore, enter = pwExpand(), exit = pwCollapse()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                // ── Exclude from analytics (#451) ──
                GroupedRow(
                    position = ListItemPosition.Single,
                    onClick = { viewModel.setExcludedFromAnalytics(!transaction.excludedFromAnalytics) }
                ) {
                    DetailIcon(Icons.Default.QueryStats)
                    RowLabels(
                        title = stringResource(R.string.txn_detail_exclude_analytics_title),
                        subtitle = stringResource(R.string.txn_detail_exclude_analytics_subtitle)
                    )
                    // Display-only — the row's click is the single toggle handler
                    // so a tap can't fire the VM call twice (Greptile #454).
                    Switch(
                        checked = transaction.excludedFromAnalytics,
                        onCheckedChange = null
                    )
                }

                DetailGroup(
                    moreRows(
                        transaction = transaction,
                        tags = detailTags,
                        accountProfileId = accountProfileId,
                        accountCurrency = accountCurrency,
                        discrepancy = discrepancy
                    )
                )
                discrepancy?.let { d -> BalanceAdjustmentAction(d, viewModel) }

                // ── Receipt Section ──
                receiptUri?.let { uri ->
                    GroupedColumn(
                        position = ListItemPosition.Single,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.large)
                            .clickable { viewModel.showFullScreenReceipt() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            DetailIcon(Icons.Default.Receipt)
                            Text(
                                text = stringResource(R.string.txn_detail_receipt),
                                style = PennyWiseText.rowTitle,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(R.string.txn_detail_receipt_tap_to_view),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AsyncImage(
                            model = uri,
                            contentDescription = stringResource(R.string.txn_detail_receipt),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = RECEIPT_PREVIEW_MAX_HEIGHT)
                                .clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                if (receiptUri == null && receiptMissing) {
                    GroupedRow(position = ListItemPosition.Single) {
                        DetailIcon(Icons.Default.Receipt)
                        Text(
                            text = stringResource(R.string.txn_detail_receipt_missing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ── SMS Section ──
                if (!transaction.smsBody.isNullOrBlank()) {
                    OriginalSmsSection(smsBody = transaction.smsBody)
                }
            }
        }
    }
}

/** Badge, merchant, the big amount and a type chip, centred. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetailHero(
    transaction: TransactionEntity,
    merchantName: String,
    settled: Boolean,
    primaryCurrency: String,
    convertedAmount: BigDecimal?,
    loan: LoanEntity?,
    onNavigateToLoanDetail: (Long) -> Unit,
    onUnmarkLoanClick: () -> Unit
) {
    val typeColor = when (transaction.transactionType) {
        TransactionType.INCOME -> MaterialTheme.colorScheme.income
        TransactionType.EXPENSE -> MaterialTheme.colorScheme.spending
        TransactionType.CREDIT -> MaterialTheme.colorScheme.credit
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.transfer
        TransactionType.INVESTMENT -> MaterialTheme.colorScheme.investment
    }
    val sign = when (transaction.transactionType) {
        TransactionType.INCOME -> "+"
        TransactionType.EXPENSE -> "-"
        TransactionType.CREDIT, TransactionType.TRANSFER, TransactionType.INVESTMENT -> ""
    }
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm)
            .padding(bottom = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
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
        SettledCheck(settled = settled, size = Dimensions.Icon.detailBadge, modifier = heroIconModifier) {
            TransactionAvatar(
                merchantName = transaction.merchantName,
                category = transaction.category,
                size = Dimensions.Icon.detailBadge,
                glyphSize = Dimensions.Icon.detailBadgeGlyph,
                shape = MaterialShapes.Cookie9Sided.toShape()
            )
        }

        Text(
            text = merchantName,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs)
        )

        val amountStyle = MaterialTheme.typography.displayMediumEmphasized.money()
        Text(
            text = CurrencyFormatter.signed(sign, transaction.formatAmount()),
            style = amountStyle,
            color = typeColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = PennyWiseText.autoSizeFloor,
                maxFontSize = amountStyle.fontSize
            )
        )

        if (transaction.currency.isNotEmpty() &&
            !transaction.currency.equals(primaryCurrency, ignoreCase = true) &&
            convertedAmount != null
        ) {
            Text(
                text = "≈ ${CurrencyFormatter.formatCurrency(convertedAmount, primaryCurrency)}",
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // The type, once; the date is the Date row below.
        val typeLabel = stringResource(transactionTypeLabel(transaction.transactionType))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.heightIn(min = Dimensions.Component.chipHeight)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = Spacing.smd, vertical = Spacing.xs)) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Loan chip
        if (loan != null) {
            val loanColor = MaterialTheme.colorScheme.loan
            val loanInk = MaterialTheme.colorScheme.inkOnWash(loanColor, Dimensions.Alpha.categoryTint)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                SuggestionChip(
                    onClick = { onNavigateToLoanDetail(loan.id) },
                    label = {
                        // This row's own role on the loan: a repayment isn't "Borrowed from".
                        val principal = LoanMath.isPrincipal(loan.direction, transaction.transactionType)
                        Text(
                            text = stringResource(
                                when {
                                    loan.direction == LoanDirection.LENT && principal -> R.string.txn_detail_loan_lent_to
                                    loan.direction == LoanDirection.LENT -> R.string.txn_detail_loan_repaid_by
                                    principal -> R.string.txn_detail_loan_borrowed_from
                                    else -> R.string.txn_detail_loan_repaid_to
                                },
                                loan.personName
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    icon = {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small),
                            tint = loanInk
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = loanColor.copy(alpha = Dimensions.Alpha.categoryTint),
                        labelColor = loanInk,
                        iconContentColor = loanInk
                    ),
                    shape = CircleShape,
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
            LoanPartLine(transaction)
        }
    }
}

/** Category (a tinted dropdown chip), Account, Date and Note. */
@Composable
private fun primaryRows(
    transaction: TransactionEntity,
    splits: List<SplitItem>,
    hasSplits: Boolean,
    onCategoryClick: () -> Unit,
    onEditClick: () -> Unit
): List<DetailRowSlot> {
    val isSplit = hasSplits && splits.isNotEmpty()
    val categoryText = when {
        isSplit -> pluralStringResource(R.plurals.txn_detail_split_categories, splits.size, splits.size)
        isUnsortedCategory(transaction.category) -> stringResource(R.string.home_section_unsorted)
        else -> categoryLabel(transaction.category)
    }
    val categoryColor = when {
        isSplit -> MaterialTheme.colorScheme.primary
        isUnsortedCategory(transaction.category) -> MaterialTheme.colorScheme.tertiary
        else -> CategoryMapping.colorFor(transaction.category)
    }
    // Account, or both legs of a paired transfer. An account without a number
    // (a Cash account is ("Cash", "")) shows its name instead of a blank (#913).
    val accountText = transaction.accountNumber?.takeIf { it.isNotBlank() }?.let { number ->
        listOfNotNull(transaction.bankName, "••" + number.takeLast(4)).joinToString(" ")
    } ?: transaction.bankName
    val showTransferLegs = transaction.fromAccount != null && transaction.toAccount != null
    // A one-legged transfer shows its leg under the details below instead.
    val hasLeg = transaction.fromAccount != null || transaction.toAccount != null
    val note = transaction.description?.takeIf { it.isNotBlank() }

    return buildList {
        add { position ->
            GroupedRow(position = position, onClick = onCategoryClick) {
                DetailIcon(Icons.Outlined.Category)
                Text(
                    stringResource(R.string.txn_detail_label_category),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                CategoryDropdownChip(text = categoryText, color = categoryColor)
            }
        }
        if (showTransferLegs) {
            add { position ->
                GroupedRow(position = position) {
                    TransferFlowRow(
                        fromValue = maskedLeg(transaction.fromAccount!!, transaction.fromBankName),
                        toValue = maskedLeg(transaction.toAccount!!, transaction.toBankName)
                    )
                }
            }
        } else if (accountText != null && !hasLeg) {
            add { position ->
                DetailValueRow(position, Icons.Outlined.AccountBalance, stringResource(R.string.txn_detail_label_account), accountText)
            }
        }
        add { position ->
            DetailValueRow(
                position,
                Icons.Outlined.Schedule,
                stringResource(R.string.txn_detail_label_date),
                transaction.dateTime.format(AppDates.dateTimeFormatter(weekday = true))
            )
        }
        add { position ->
            GroupedRow(position = position, onClick = onEditClick) {
                DetailIcon(Icons.Outlined.EditNote)
                Text(
                    stringResource(R.string.txn_detail_label_note),
                    style = MaterialTheme.typography.bodyLarge
                )
                if (note != null) {
                    Text(
                        text = CurrencyFormatter.maskAmountsIn(note),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.txn_detail_note_add),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/** Tags, recurring, classification, one-sided transfer legs, balance, mismatch, reference. */
@Composable
private fun moreRows(
    transaction: TransactionEntity,
    tags: List<String>,
    accountProfileId: Long?,
    accountCurrency: String?,
    discrepancy: com.pennywiseai.tracker.data.ledger.BalanceDiscrepancy?
): List<DetailRowSlot> {
    val effectiveProfileId = transaction.profileId ?: accountProfileId
    val isEffectivelyBusiness = effectiveProfileId == ProfileEntity.BUSINESS_ID
    val tagsLabel = pluralStringResource(R.plurals.txn_detail_label_tags, tags.size)
    val recurringLabel = stringResource(R.string.txn_detail_label_status)
    val recurringValue = stringResource(R.string.txn_detail_status_recurring)
    val classificationLabel = stringResource(R.string.txn_detail_label_classification)
    val classificationValue = stringResource(if (isEffectivelyBusiness) R.string.txn_detail_business else R.string.txn_detail_personal)
    val fromLabel = stringResource(R.string.txn_detail_label_from)
    val toLabel = stringResource(R.string.txn_detail_label_to)
    val balanceLabel = stringResource(R.string.txn_detail_label_balance)
    val mismatchLabel = stringResource(R.string.txn_detail_label_balance_mismatch)
    val referenceLabel = stringResource(R.string.txn_detail_label_reference)
    val mismatchValue = discrepancy?.let { d ->
        stringResource(
            R.string.txn_detail_balance_mismatch_since,
            CurrencyFormatter.formatCurrency(d.delta.abs(), d.currency),
            CurrencyFormatter.formatCurrency(d.expected, d.currency),
            // Where the check starts (the previous balance SMS), so moving an
            // adjustment outside it explains a mismatch coming back (#1022).
            d.since?.format(AppDates.dayMonthTimeFormatter()).orEmpty()
        )
    }
    val pairedTransfer = transaction.fromAccount != null && transaction.toAccount != null

    return buildList {
        if (tags.isNotEmpty()) {
            add { DetailValueRow(it, Icons.Outlined.Sell, tagsLabel, tags.joinToString(", ")) }
        }
        if (transaction.isRecurring) {
            add { DetailValueRow(it, Icons.Default.Repeat, recurringLabel, recurringValue) }
        }
        add {
            DetailValueRow(
                it,
                if (isEffectivelyBusiness) Icons.Outlined.Business else Icons.Outlined.Person,
                classificationLabel,
                classificationValue
            )
        }
        if (!pairedTransfer) {
            transaction.fromAccount?.let { from ->
                add { DetailValueRow(it, Icons.Default.Output, fromLabel, maskedLeg(from, transaction.fromBankName)) }
            }
            transaction.toAccount?.let { to ->
                add { DetailValueRow(it, Icons.AutoMirrored.Filled.Input, toLabel, maskedLeg(to, transaction.toBankName)) }
            }
        }
        transaction.balanceAfter?.let { balance ->
            add {
                DetailValueRow(
                    it,
                    Icons.Outlined.AccountBalanceWallet,
                    balanceLabel,
                    CurrencyFormatter.formatCurrency(balance, accountCurrency ?: transaction.currency)
                )
            }
        }
        if (mismatchValue != null) {
            add { DetailValueRow(it, Icons.Outlined.Warning, mismatchLabel, mismatchValue) }
        }
        // Reference number (prefer extracted reference, fallback to SMS sender)
        (transaction.reference ?: transaction.smsSender)?.let { reference ->
            add { DetailValueRow(it, Icons.Default.Tag, referenceLabel, reference) }
        }
    }
}

/** Offers to record a balance mismatch as an untracked transaction (#734/#135). */
@Composable
private fun BalanceAdjustmentAction(
    discrepancy: com.pennywiseai.tracker.data.ledger.BalanceDiscrepancy,
    viewModel: TransactionDetailViewModel
) {
    val adding by viewModel.isAddingAdjustment.collectAsStateWithLifecycle()
    var showAdjustmentSheet by rememberSaveable { mutableStateOf(false) }
    if (showAdjustmentSheet) {
        BalanceAdjustmentSheet(
            discrepancy = discrepancy,
            viewModel = viewModel,
            onDismiss = { showAdjustmentSheet = false }
        )
    }
    FilledTonalButton(
        onClick = { showAdjustmentSheet = true },
        enabled = !adding,
        modifier = Modifier.fillMaxWidth(),
        shapes = ButtonDefaults.shapes()
    ) {
        Text(
            stringResource(
                if (discrepancy.delta.signum() < 0) R.string.txn_detail_add_untracked_expense
                else R.string.txn_detail_add_untracked_income,
                CurrencyFormatter.formatCurrency(discrepancy.delta.abs(), discrepancy.currency)
            )
        )
    }
}

/** A leg's account: its last four digits masked, or the bank name when it has no number. */
private fun maskedLeg(account: String, bank: String?): String = when {
    account.isBlank() -> bank ?: account
    account.length > 4 -> "••" + account.takeLast(4)
    else -> account
}

@Composable
private fun DetailIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(Dimensions.Icon.medium),
        tint = tint
    )
}

/** Label on the left, its value right-aligned. */
@Composable
private fun DetailValueRow(
    position: ListItemPosition,
    icon: ImageVector,
    label: String,
    value: String
) {
    GroupedRow(position = position) {
        DetailIcon(icon)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/** The category's name on a wash of its colour, with a dropdown chevron. */
@Composable
private fun CategoryDropdownChip(text: String, color: Color) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = Dimensions.Alpha.categoryTint),
        // Ink nudged for the wash of itself it sits on; the wash keeps the raw hue.
        contentColor = MaterialTheme.colorScheme.inkOnWash(color, Dimensions.Alpha.categoryTint),
        modifier = Modifier.heightIn(min = Dimensions.Component.chipHeight)
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.smd, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = stringResource(R.string.txn_list_change_category),
                modifier = Modifier.size(Dimensions.Icon.inline)
            )
        }
    }
}

/** The owl beside a short line, on a tonal card. */
@Composable
private fun MascotNote(
    text: String,
    mood: MascotMood,
    containerColor: Color,
    contentColor: Color,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.smd)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
            ) {
                Mascot(size = Dimensions.Icon.avatarLarge, mood = mood)
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
            if (actions != null) {
                Row(
                    modifier = Modifier.padding(start = Dimensions.Icon.avatarLarge + Spacing.smd),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    content = actions
                )
            }
        }
    }
}

/** "Always put X in Y?" after a category pick: Remember / Not now. */
@Composable
private fun RuleSuggestionCard(text: String, onCreate: () -> Unit, onDismiss: () -> Unit) {
    MascotNote(
        text = text,
        mood = MascotMood.Happy,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Button(onClick = onCreate, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.txn_detail_remember))
        }
        TextButton(
            onClick = onDismiss,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
        ) {
            Text(stringResource(R.string.txn_detail_remember_not_now))
        }
    }
}

@Composable
private fun RowScope.TransferFlowRow(
    fromValue: String,
    toValue: String
) {
    DetailIcon(Icons.Default.SwapHoriz)
    Text(
        text = fromValue,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false)
    )
    Icon(
        Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = null,
        modifier = Modifier.size(Dimensions.Icon.small),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = toValue,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false)
    )
}

/** "Original SMS" as a row that opens onto the message in a mono, inset block. */
@Composable
private fun OriginalSmsSection(smsBody: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    GroupedList {
        GroupedRow(
            position = if (expanded) ListItemPosition.Top else ListItemPosition.Single,
            onClick = { expanded = !expanded }
        ) {
            DetailIcon(Icons.AutoMirrored.Outlined.Chat)
            Text(
                stringResource(R.string.txn_detail_original_sms),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.txn_detail_sms_hide else R.string.txn_detail_sms_show),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.Icon.medium)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = pwExpand(),
            exit = pwCollapse()
        ) {
            GroupedColumn(position = ListItemPosition.Bottom) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = CurrencyFormatter.maskAmountsIn(smsBody),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(Spacing.smd)
                    )
                }
            }
        }
    }
}

/** Caps the inline receipt preview; the full image opens on tap. */
private val RECEIPT_PREVIEW_MAX_HEIGHT = Dimensions.Component.dialogListMaxHeight / 2

/**
 * How a loan-linked row divides: the part on the loan and the part that counts
 * as your own spend or income (#831), e.g. "₹1,000 on the loan · ₹500 your spending".
 */
@Composable
private fun LoanPartLine(transaction: TransactionEntity) {
    val loanPart = (transaction.loanContribution ?: transaction.amount).min(transaction.amount)
    val ownPart = transaction.amount - loanPart
    val onLoan = stringResource(R.string.txn_detail_loan_part, CurrencyFormatter.formatCurrency(loanPart, transaction.currency))
    val text = if (ownPart.signum() > 0) {
        val own = CurrencyFormatter.formatCurrency(ownPart, transaction.currency)
        val ownText = if (transaction.transactionType == TransactionType.INCOME) {
            stringResource(R.string.txn_detail_loan_own_income, own)
        } else {
            stringResource(R.string.txn_detail_loan_own_spend, own)
        }
        "$onLoan · $ownText"
    } else {
        onLoan
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}
