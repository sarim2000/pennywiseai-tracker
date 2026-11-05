package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalDensity
import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.ui.components.BrandIcon
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.formatBalance

@Composable
fun AccountCarousel(
    bankAccounts: List<AccountBalanceEntity>,
    creditCards: List<AccountBalanceEntity>,
    onAccountClick: (bankName: String, accountLast4: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    isUnifiedMode: Boolean = false,
    selectedCurrency: String,
    isAmountHidden: Boolean = true,
) {
    val allAccounts = bankAccounts + creditCards

    if (allAccounts.isEmpty()) return

    // A list, not a carousel: every balance is on screen with its account beside it, instead
    // of one card at a time and the rest behind a swipe.
    GroupedList(modifier = modifier) {
        allAccounts.forEachIndexed { index, account ->
            AccountRow(
                account = account,
                isCreditCard = creditCards.contains(account),
                position = ListItemPosition.from(index, allAccounts.size),
                onClick = { onAccountClick(account.bankName, account.accountLast4) },
                isAmountHidden = isAmountHidden,
            )
        }
    }
}

@Composable
private fun AccountRow(
    account: AccountBalanceEntity,
    isCreditCard: Boolean,
    position: ListItemPosition,
    onClick: () -> Unit,
    isAmountHidden: Boolean,
) {
    // Low-balance alert when a non-credit account is at or below its user-set threshold
    // (matches Manage Accounts). #509
    val isLowBalance = !isCreditCard &&
        account.lowBalanceThreshold != null &&
        account.balance <= account.lowBalanceThreshold
    val accountLabel = buildList {
        // Mobile-money wallets have no account number — show just the service name.
        if (account.accountLast4 != AccountBalanceEntity.WALLET_ACCOUNT_MARKER) add("••${account.accountLast4}")
        add(
            when {
                isLowBalance -> stringResource(R.string.account_card_low_balance)
                isCreditCard -> stringResource(R.string.account_card_outstanding)
                else -> stringResource(R.string.account_card_type_savings)
            }
        )
    }.joinToString(" · ")
    GroupedRow(
        position = position,
        onClick = onClick,
        horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
    ) {
        BrandIcon(
            merchantName = account.bankName,
            size = Dimensions.Icon.list,
            showBackground = true
        )
        // For a credit card this is the amount owed, not the limit. The entity is already
        // pre-converted in unified mode, so its own currency keeps an un-convertible balance
        // honest ("$400", not "MZN400"). Home's eye governs it, like the rest of Home.
        val balance: @Composable () -> Unit = {
            Text(
                text = if (isAmountHidden) AmountPrivacy.MASK else CurrencyFormatter.formatCurrency(account.balance, account.currency),
                style = PennyWiseText.amountRow,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                autoSize = PennyWiseText.fit(PennyWiseText.amountRow),
            )
        }
        // At a large font a long balance would take the width and hide the account's name.
        val stacked = LocalDensity.current.fontScale >= STACK_FONT_SCALE
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = account.bankName,
                style = PennyWiseText.rowTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = accountLabel,
                style = PennyWiseText.metadata,
                color = if (isLowBalance) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (stacked) balance()
        }
        if (!stacked) balance()
    }
}
