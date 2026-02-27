package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val containerColor = if (blurEffects)
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.surfaceContainerLow
    val accentColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .height(190.dp)
            .then(
                if (blurEffects && hazeState != null) Modifier
                    .clip(RoundedCornerShape(Dimensions.CornerRadius.large))
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
            )
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        color = containerColor,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            BrandIcon(
                merchantName = account.bankName,
                size = 44.dp,
                showBackground = true
            )

            Column {
                Text(
                    text = "${account.bankName.uppercase()} ••${account.accountLast4}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isCreditCard) account.formatCreditLimit() else account.formatBalance(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isCreditCard) "Credit Card" else "Savings account",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontWeight = FontWeight.SemiBold
                    )

                    Surface(
                        onClick = onClick,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        border = BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "View details",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}
