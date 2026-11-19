package com.pennywiseai.tracker.ui.components.cards

import android.content.res.Resources
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.contacts.LocalMerchantDisplay
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.formatAmount
import com.pennywiseai.tracker.ui.LocalNavAnimatedVisibilityScope
import com.pennywiseai.tracker.ui.LocalSharedTransitionScope
import com.pennywiseai.tracker.ui.components.EmojiGlyph
import com.pennywiseai.tracker.ui.icons.IconProvider
import com.pennywiseai.tracker.ui.icons.IconResource
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.icons.isValidCategoryOverride
import com.pennywiseai.tracker.ui.sharedElementIcon
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.ui.theme.spending
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@Composable
fun TransactionItem(
    transaction: TransactionEntity,
    convertedAmount: BigDecimal? = null,
    /** The part of a split row shown in a category-filtered list (#1192); shown instead of its amount. */
    splitShare: BigDecimal? = null,
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
    val colorScheme = MaterialTheme.colorScheme
    val amountColor = when (transaction.transactionType) {
        TransactionType.INCOME -> colorScheme.income
        TransactionType.EXPENSE -> containerColor
            ?.let { colorScheme.contentColorFor(it) }
            ?.takeIf { it.isSpecified }
            ?: colorScheme.spending
        TransactionType.CREDIT -> colorScheme.credit
        TransactionType.TRANSFER -> colorScheme.transfer
        TransactionType.INVESTMENT -> colorScheme.investment
    }.onRow(containerColor)

    val dateTimeFormatter = if (showDate) AppDates.dayMonthTimeFormatter() else AppDates.pattern("h:mm a")
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

    val resources = LocalContext.current.resources
    val unsorted = isUnsortedCategory(transaction.category)
    val categoryText = if (unsorted) {
        resources.getString(R.string.home_section_unsorted)
    } else {
        categoryLabel(transaction.category)
    }
    val subtitle = remember(transaction, splitShare, categoryText, dateTimeText, isEffectivelyBusiness, AmountPrivacy.hidden) {
        buildList {
            // First, so a long description can't truncate it out of the one-line subtitle.
            if (splitShare != null) add(resources.getString(R.string.txn_item_tag_split_of, transaction.formatAmount()))
            // "Groceries · 6:42 PM": what it was, then when.
            add(categoryText)
            if (description != null) add(CurrencyFormatter.maskAmountsIn(description))
            add(dateTimeText)

            if (showTypeLabel) {
                when (transaction.transactionType) {
                    TransactionType.CREDIT -> add(resources.getString(R.string.txn_item_tag_credit))
                    TransactionType.TRANSFER -> {
                        if (transferTitleOverride(transaction, resources) == null) {
                            add(resources.getString(R.string.txn_item_tag_transfer))
                        }
                    }
                    TransactionType.INVESTMENT -> add(resources.getString(R.string.txn_item_tag_investment))
                    else -> {}
                }
            }
            if (transaction.isRecurring) add(resources.getString(R.string.txn_item_tag_recurring))
            if (isEffectivelyBusiness) add(resources.getString(R.string.txn_item_tag_business))
            // Mark rows the user excluded from analytics so it's visible in the
            // list which ones are skipped by spending stats (#451).
            if (transaction.excludedFromAnalytics) add(resources.getString(R.string.txn_item_tag_excluded))
            transaction.balanceAfter?.let { balance ->
                add(resources.getString(R.string.txn_item_balance_after, CurrencyFormatter.formatCurrency(balance, transaction.currency)))
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

    val nativeAmount = splitShare?.let { CurrencyFormatter.formatCurrency(it, transaction.currency) }
        ?: transaction.formatAmount()
    val formattedAmount = if (convertedAmount != null && displayCurrency != null) {
        CurrencyFormatter.formatCurrency(convertedAmount, displayCurrency)
    } else {
        nativeAmount
    }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val merchantDisplay = LocalMerchantDisplay.current

    // For a paired self-transfer row, the event ("Transfer → 9999" /
    // "Transfer from 1234") is more informative than the merchant name (often
    // the user's own contact name), and stops the two legs from looking like
    // duplicate rows in the list. Falls back to merchant otherwise.
    val transferTitle = transferTitleOverride(transaction, resources)

    ListItemCardV2(
        title = transferTitle ?: merchantDisplay(transaction.merchantName) ?: transaction.merchantName,
        subtitle = subtitle,
        amount = CurrencyFormatter.signed(amountPrefix, formattedAmount),
        amountColor = amountColor,
        subtitleColor = (if (unsorted) colorScheme.tertiary else colorScheme.onSurfaceVariant).onRow(containerColor),
        shape = listItemPosition.toShape(),
        contentPadding = Dimensions.Padding.cardCompact,
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
            TransactionAvatar(
                merchantName = transaction.merchantName,
                category = transaction.category,
                modifier = iconModifier
            )
        },
        trailingContent = {
            if (convertedAmount != null && displayCurrency != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                ) {
                    Text(
                        text = CurrencyFormatter.signed(amountPrefix, CurrencyFormatter.formatCurrency(convertedAmount, displayCurrency)),
                        style = PennyWiseText.amountRow,
                        color = amountColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "($nativeAmount)",
                        style = PennyWiseText.amountSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.onRow(containerColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = CurrencyFormatter.signed(amountPrefix, formattedAmount),
                    style = PennyWiseText.amountRow,
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
private fun transferTitleOverride(transaction: TransactionEntity, resources: Resources): String? {
    if (transaction.transactionType != TransactionType.TRANSFER) return null
    val mine = transaction.accountNumber
    val from = transaction.fromAccount
    val to = transaction.toAccount
    // A Cash wallet saved without an identifier has a blank last4: name it by its bank name instead.
    val toLabel = to?.takeLast(4)?.ifBlank { transaction.toBankName.orEmpty() }
    val fromLabel = from?.takeLast(4)?.ifBlank { transaction.fromBankName.orEmpty() }
    return when {
        from != null && to != null && mine == from -> resources.getString(R.string.txn_item_transfer_to, toLabel)
        from != null && to != null && mine == to -> resources.getString(R.string.txn_item_transfer_from, fromLabel)
        to != null && mine != to -> resources.getString(R.string.txn_item_transfer_to, toLabel)
        from != null && mine != from -> resources.getString(R.string.txn_item_transfer_from, fromLabel)
        else -> null
    }
}

/**
 * Whether the row has no category at all: blank or the legacy "Uncategorized"
 * sentinel, the same test the row used to leave the category out of its subtitle
 * ([isValidCategoryOverride]). Others is a real category the user may have picked.
 */
fun isUnsortedCategory(category: String): Boolean = !category.isValidCategoryOverride()

/**
 * A transaction's leading badge: its category glyph on a wash of the category
 * colour, a brand logo when one is bundled, or a "?" on tertiary for a row that
 * still needs a category. The row uses a circle; the detail screen passes an
 * Expressive cookie [shape] at a larger [size].
 */
@Composable
fun TransactionAvatar(
    merchantName: String,
    category: String,
    modifier: Modifier = Modifier,
    size: Dp = Dimensions.Icon.list,
    glyphSize: Dp = Dimensions.Icon.medium,
    shape: Shape = CircleShape,
) {
    val colorScheme = MaterialTheme.colorScheme
    // Not remembered: it reads the live user category styles, as BrandIcon does.
    val icon = IconProvider.getTransactionIcon(merchantName, category)
    val base = modifier.size(size).clip(shape)
    when {
        isUnsortedCategory(category) && icon !is IconResource.DrawableResource -> Box(
            modifier = base.background(colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Help,
                contentDescription = categoryLabel(category),
                tint = colorScheme.onTertiaryContainer,
                modifier = Modifier.size(glyphSize)
            )
        }
        icon is IconResource.DrawableResource -> Box(
            modifier = base.background(colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = icon.resId),
                contentDescription = merchantName,
                modifier = Modifier.size(glyphSize)
            )
        }
        icon is IconResource.VectorIcon -> Box(
            modifier = base.background(icon.tint.copy(alpha = Dimensions.Alpha.categoryTint)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon.icon,
                contentDescription = categoryLabel(category),
                // The hue on a wash of itself: keep it, but reach 3:1.
                tint = MaterialTheme.colorScheme.inkOnWash(icon.tint, Dimensions.Alpha.categoryTint, GRAPHIC_CONTRAST),
                modifier = Modifier.size(glyphSize)
            )
        }
        icon is IconResource.Emoji -> Box(
            modifier = base.background(icon.tint.copy(alpha = Dimensions.Alpha.categoryTint)),
            contentAlignment = Alignment.Center
        ) {
            EmojiGlyph(icon.emoji, glyphSize)
        }
    }
}

/** This ink on a selected row's override fill (primaryContainer), which isn't the surface it was picked for. */
private fun Color.onRow(containerColor: Color?): Color = containerColor?.let { readableOn(listOf(it)) } ?: this
