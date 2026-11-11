package com.pennywiseai.tracker.ui.components.cards

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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.money
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.data.database.entity.formatBalance
import com.pennywiseai.tracker.ui.effects.frostedBlur
import dev.chrisbanes.haze.HazeState

@Composable
fun AccountCarousel(
    bankAccounts: List<AccountBalanceEntity>,
    creditCards: List<AccountBalanceEntity>,
    onAccountClick: (bankName: String, accountLast4: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    isUnifiedMode: Boolean = false,
    selectedCurrency: String,
    blurEffects: Boolean = false,
    hazeState: HazeState? = null,
    isAmountHidden: Boolean = true,
    onToggleAmountsHidden: () -> Unit = {}
) {
    val allAccounts = bankAccounts + creditCards

    if (allAccounts.isEmpty()) return

    if (allAccounts.size == 1) {
        val account = allAccounts.first()
        AccountCarouselCard(
            account = account,
            isCreditCard = creditCards.contains(account),
            onClick = { onAccountClick(account.bankName, account.accountLast4) },
            modifier = modifier.fillMaxWidth(),
            isUnifiedMode = isUnifiedMode,
            selectedCurrency = selectedCurrency,
            blurEffects = blurEffects,
            hazeState = hazeState,
            isAmountHidden = isAmountHidden,
            onToggleAmountsHidden = onToggleAmountsHidden
        )
    } else {
        val pagerState = rememberPagerState(pageCount = { allAccounts.size })

        HorizontalPager(
            state = pagerState,
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(end = 32.dp),
            pageSpacing = Spacing.md
        ) { page ->
            val account = allAccounts[page]
            AccountCarouselCard(
                account = account,
                isCreditCard = creditCards.contains(account),
                onClick = { onAccountClick(account.bankName, account.accountLast4) },
                modifier = Modifier.fillMaxWidth(),
                isUnifiedMode = isUnifiedMode,
                selectedCurrency = selectedCurrency,
                blurEffects = blurEffects,
                hazeState = hazeState,
                isAmountHidden = isAmountHidden,
                onToggleAmountsHidden = onToggleAmountsHidden
            )
        }
    }
}

@Composable
private fun AccountCarouselCard(
    account: AccountBalanceEntity,
    isCreditCard: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isUnifiedMode: Boolean = false,
    selectedCurrency: String,
    blurEffects: Boolean = false,
    hazeState: HazeState? = null,
    isAmountHidden: Boolean = true,
    onToggleAmountsHidden: () -> Unit = {}
) {
    // Low-balance alert: tint the home card red when a non-credit account's balance
    // is at or below its user-set threshold (matches Manage Accounts). #509
    val isLowBalance = !isCreditCard &&
        account.lowBalanceThreshold != null &&
        account.balance <= account.lowBalanceThreshold
    val containerColor = when {
        isLowBalance -> MaterialTheme.colorScheme.errorContainer.copy(
            alpha = if (blurEffects) 0.5f else 0.7f
        )
        blurEffects -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    val cardShape = MaterialTheme.shapes.large

    PennyWiseCardV2(
        modifier = modifier
            .then(
                if (blurEffects && hazeState != null) Modifier
                    .clip(cardShape)
                    .frostedBlur(hazeState, tint = containerColor)
                else Modifier
            ),
        onClick = onClick,
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        contentPadding = Spacing.md
    ) {
        // Top row: BrandIcon + Bank name + Account last4 + Account type chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            BrandIcon(
                merchantName = account.bankName,
                size = 40.dp,
                showBackground = true
            )

            Text(
                text = account.bankName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            // Mobile-money wallets have no account number — show just the service name.
            if (account.accountLast4 != AccountBalanceEntity.WALLET_ACCOUNT_MARKER) {
                Text(
                    text = "••${account.accountLast4}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }

            // Account type chip
            Surface(
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = if (isCreditCard) stringResource(R.string.account_card_type_credit) else stringResource(R.string.account_card_type_savings),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Bottom row: Balance label on left, amount + eye on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    isLowBalance -> stringResource(R.string.account_card_low_balance)
                    isCreditCard -> stringResource(R.string.account_card_outstanding)
                    else -> stringResource(R.string.account_card_balance)
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isLowBalance) FontWeight.Medium else null,
                color = if (isLowBalance) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    // The label above is "Outstanding" for credit cards; that is the
                    // amount owed (account.balance), NOT the total creditLimit. Mirrors
                    // CreditCardsCard / AccountDetailScreen, which already do this.
                    // The entity is already pre-converted in unified mode (its currency
                    // is the display currency when a rate was found), so format with the
                    // account's own currency. This keeps an un-convertible balance honest
                    // (shows "$400", not "MZN400") instead of forcing the display label.
                    // Home's eye, not a per-card one: hidden by default like the rest of Home.
                    text = if (isAmountHidden) AmountPrivacy.MASK else account.formatBalance(),
                    style = MaterialTheme.typography.titleLarge.money(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                IconButton(
                    onClick = onToggleAmountsHidden,
                    modifier = Modifier.size(Dimensions.Component.minTouchTarget),
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        imageVector = if (isAmountHidden) Icons.Default.VisibilityOff
                                      else Icons.Default.Visibility,
                        contentDescription = if (isAmountHidden) stringResource(R.string.balance_show) else stringResource(R.string.balance_hide),
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
