package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import com.pennywiseai.tracker.data.contacts.LocalMerchantDisplay
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.LocalNavAnimatedVisibilityScope
import com.pennywiseai.tracker.ui.LocalSharedTransitionScope
import com.pennywiseai.tracker.ui.sharedElementIcon
import com.pennywiseai.tracker.ui.components.BrandIcon
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.formatAmount
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@Composable
fun TransactionItem(
    transaction: TransactionEntity,
    convertedAmount: BigDecimal? = null,
    displayCurrency: String? = null,
    showDate: Boolean = true,
    showTypeLabel: Boolean = true,
    listItemPosition: ListItemPosition = ListItemPosition.Single,
    profileAccountKeys: Map<Long, Set<String>> = emptyMap(),
    onClick: () -> Unit = {},
    /** Optional long-press handler — used for bulk-edit selection entry. */
    onLongClick: (() -> Unit)? = null,
    /** Overrides the row's container colour (e.g. for selected state). */
    containerColor: androidx.compose.ui.graphics.Color? = null,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    val amountColor = remember(transaction.transactionType, isDark) {
        when (transaction.transactionType) {
            TransactionType.INCOME -> if (!isDark) income_light else income_dark
            TransactionType.EXPENSE -> if (!isDark) expense_light else expense_dark
            TransactionType.CREDIT -> if (!isDark) credit_light else credit_dark
            TransactionType.TRANSFER -> if (!isDark) transfer_light else transfer_dark
            TransactionType.INVESTMENT -> if (!isDark) investment_light else investment_dark
        }
    }

    val dateTimeFormatter = remember(showDate) {
        DateTimeFormatter.ofPattern(if (showDate) "d MMM \u00B7 h:mm a" else "h:mm a")
    }
    val dateTimeText = remember(transaction.dateTime, dateTimeFormatter) {
        transaction.dateTime.format(dateTimeFormatter)
    }

    val isEffectivelyBusiness = remember(transaction, profileAccountKeys) {
        val effectiveProfileId = transaction.profileId ?: run {
            if (transaction.bankName != null && transaction.accountNumber != null) {
                val key = "${transaction.bankName}_${transaction.accountNumber}"
                profileAccountKeys.entries.firstOrNull { (_, keys) -> keys.contains(key) }?.key
            } else null
        }
        effectiveProfileId == ProfileEntity.BUSINESS_ID
    }

    // User-written description, if present, is surfaced as the LEAD segment of
    // the subtitle (kept short) — not as the title. Promoting it to title made
    // casual notes ("movie night with sarah") read as inconsistent next to
    // brand-name merchants ("Uber", "Netflix") and routinely got truncated. The
    // merchant stays the visual heading; the description is a small contextual
    // tag below. (#383)
    val description = transaction.description?.takeIf { it.isNotBlank() }

    val subtitle = remember(transaction, dateTimeText, isEffectivelyBusiness) {
        buildList {
            if (description != null) add(description)
            add(dateTimeText)
            if (transaction.category.isNotBlank() &&
                !transaction.category.equals("Uncategorized", ignoreCase = true)
            ) {
                add(transaction.category)
            }

            if (showTypeLabel) {
                when (transaction.transactionType) {
                    TransactionType.CREDIT -> add("Credit")
                    TransactionType.TRANSFER -> {
                        if (transferTitleOverride(transaction) == null) {
                            add("Transfer")
                        }
                    }
                    TransactionType.INVESTMENT -> add("Investment")
                    else -> {}
                }
            }
            if (transaction.isRecurring) add("Recurring")
            if (isEffectivelyBusiness) add("Business")
            // Mark rows the user excluded from analytics so it's visible in the
            // list which ones are skipped by spending stats (#451).
            if (transaction.excludedFromAnalytics) add("Excluded")
            transaction.balanceAfter?.let { balance ->
                add("Bal ${CurrencyFormatter.formatCurrency(balance, transaction.currency)}")
            }
        }.joinToString(" \u00B7 ")
    }

    val amountPrefix = remember(transaction.transactionType) {
        when (transaction.transactionType) {
            TransactionType.INCOME -> "+"
            TransactionType.EXPENSE, TransactionType.CREDIT, TransactionType.INVESTMENT -> "-"
            TransactionType.TRANSFER -> ""
        }
    }

    val formattedAmount = if (convertedAmount != null && displayCurrency != null) {
        CurrencyFormatter.formatCurrency(convertedAmount, displayCurrency)
    } else {
        transaction.formatAmount()
    }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val merchantDisplay = LocalMerchantDisplay.current

    // For a paired self-transfer row, the event ("Transfer → 9999" /
    // "Transfer from 1234") is more informative than the merchant name (often
    // the user's own contact name), and stops the two legs from looking like
    // duplicate rows in the list. Falls back to merchant otherwise.
    val transferTitle = transferTitleOverride(transaction)

    ListItemCardV2(
        title = transferTitle ?: merchantDisplay(transaction.merchantName) ?: transaction.merchantName,
        subtitle = subtitle,
        amount = "$amountPrefix$formattedAmount",
        amountColor = amountColor,
        shape = listItemPosition.toShape(),
        contentPadding = 14.dp,
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        },
        onLongClick = onLongClick,
        containerColor = containerColor,
        modifier = modifier,
        leadingContent = {
            val iconModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                    sharedElementIcon(
                        key = "brand_icon_${transaction.id}",
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            } else {
                Modifier
            }
            BrandIcon(
                merchantName = transaction.merchantName,
                modifier = iconModifier,
                size = Dimensions.Icon.list,
                showBackground = true,
                category = transaction.category
            )
        },
        trailingContent = {
            if (convertedAmount != null && displayCurrency != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$amountPrefix${CurrencyFormatter.formatCurrency(convertedAmount, displayCurrency)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = amountColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "(${transaction.formatAmount()})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = "$amountPrefix$formattedAmount",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    )
}

/**
 * For TRANSFER rows that have `fromAccount` and `toAccount` populated,
 * synthesise a title that describes the event (which leg + the other
 * account's last-4) rather than the merchant. Returns null for any other
 * row, in which case the default merchant-as-title rendering wins.
 */
private fun transferTitleOverride(transaction: TransactionEntity): String? {
    if (transaction.transactionType != TransactionType.TRANSFER) return null
    val mine = transaction.accountNumber
    val from = transaction.fromAccount
    val to = transaction.toAccount
    return when {
        from != null && to != null && mine == from -> "Transfer → ${to.takeLast(4)}"
        from != null && to != null && mine == to -> "Transfer from ${from.takeLast(4)}"
        to != null && mine != to -> "Transfer → ${to.takeLast(4)}"
        from != null && mine != from -> "Transfer from ${from.takeLast(4)}"
        else -> null
    }
}
