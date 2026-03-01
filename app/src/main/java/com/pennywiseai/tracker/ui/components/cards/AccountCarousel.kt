package com.pennywiseai.tracker.ui.components.cards

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.ui.components.BrandIcon
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.formatBalance
import com.pennywiseai.tracker.utils.formatCreditLimit
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

@Composable
fun AccountCarousel(
    bankAccounts: List<AccountBalanceEntity>,
    creditCards: List<AccountBalanceEntity>,
    onAccountClick: (bankName: String, accountLast4: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    blurEffects: Boolean = false,
    hazeState: HazeState? = null
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
            blurEffects = blurEffects,
            hazeState = hazeState
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
                blurEffects = blurEffects,
                hazeState = hazeState
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
    blurEffects: Boolean = false,
    hazeState: HazeState? = null
) {
    var isAmountHidden by remember { mutableStateOf(true) }
    val containerColor = if (blurEffects)
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.surfaceContainerLow

    val cardShape = RoundedCornerShape(16.dp)

    PennyWiseCardV2(
        modifier = modifier
            .then(
                if (blurEffects && hazeState != null) Modifier
                    .clip(cardShape)
                    .hazeEffect(
                        state = hazeState,
                        block = fun HazeEffectScope.() {
                            style = HazeDefaults.style(
                                backgroundColor = Color.Transparent,
                                tint = HazeDefaults.tint(containerColor),
                                blurRadius = 20.dp,
                                noiseFactor = -1f,
                            )
                            blurredEdgeTreatment = BlurredEdgeTreatment.Unbounded
                        }
                    )
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

            Text(
                text = "••${account.accountLast4}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1
            )

            // Account type chip
            Surface(
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = if (isCreditCard) "Credit" else "Savings",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                text = if (isCreditCard) "Outstanding" else "Balance",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isAmountHidden) "••••••"
                           else if (isCreditCard) account.formatCreditLimit()
                           else account.formatBalance(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                IconButton(
                    onClick = { isAmountHidden = !isAmountHidden },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isAmountHidden) Icons.Outlined.VisibilityOff
                                      else Icons.Outlined.Visibility,
                        contentDescription = if (isAmountHidden) "Show balance" else "Hide balance",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
