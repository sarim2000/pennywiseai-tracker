package com.pennywiseai.tracker.ui.components

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

@Composable
fun UnifiedAccountsCard(
    creditCards: List<AccountBalanceEntity>,
    bankAccounts: List<AccountBalanceEntity>,
    totalBalance: BigDecimal,
    totalAvailableCredit: BigDecimal,
    selectedCurrency: String = "INR",
    onAccountClick: (bankName: String, accountLast4: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var showAllAccounts by remember { mutableStateOf(false) }
    
    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Header
            Text(
                text = stringResource(R.string.accounts_overview_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(Spacing.md))
            
            // Summary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Bank Balance Summary
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = stringResource(R.string.accounts_overview_bank_balance),
                        modifier = Modifier.size(Dimensions.Icon.medium),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = CurrencyFormatter.formatCurrency(totalBalance, selectedCurrency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (bankAccounts.isNotEmpty()) {
                            stringResource(R.string.accounts_overview_bank_balance_count, bankAccounts.size)
                        } else {
                            stringResource(R.string.accounts_overview_bank_balance)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(60.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                
                // Credit Cards Summary
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = stringResource(R.string.accounts_overview_available_credit),
                        modifier = Modifier.size(Dimensions.Icon.medium),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = CurrencyFormatter.formatCurrency(totalAvailableCredit, selectedCurrency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = if (creditCards.isNotEmpty()) {
                            stringResource(R.string.accounts_overview_available_credit_count, creditCards.size)
                        } else {
                            stringResource(R.string.accounts_overview_available_credit)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Show accounts detail if expanded or if there are few accounts
            val totalAccounts = creditCards.size + bankAccounts.size
            val shouldShowDetails = showAllAccounts || totalAccounts <= 4
            
            if (totalAccounts > 0) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                
                if (shouldShowDetails) {
                    // Show Bank Accounts first (to match the icon order)
                    if (bankAccounts.isNotEmpty()) {
                        bankAccounts.forEach { account ->
                            CompactAccountItem(
                                bankName = account.bankName,
                                accountLast4 = account.accountLast4,
                                formattedAmount = CurrencyFormatter.formatCurrency(account.balance, selectedCurrency),
                                subtitle = stringResource(R.string.accounts_overview_balance),
                                isCredit = false,
                                onClick = { onAccountClick(account.bankName, account.accountLast4) }
                            )
                        }
                    }
                    
                    // Add separator between bank accounts and credit cards if both exist
                    if (bankAccounts.isNotEmpty() && creditCards.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(vertical = Spacing.xs)
                        )
                    }
                    
                    // Show Credit Cards second (to match the icon order)
                    if (creditCards.isNotEmpty()) {
                        creditCards.forEachIndexed { index, card ->
                            CompactAccountItem(
                                bankName = card.bankName,
                                accountLast4 = card.accountLast4,
                                formattedAmount = CurrencyFormatter.formatCurrency(card.creditLimit ?: BigDecimal.ZERO, selectedCurrency),
                                subtitle = if (card.balance > BigDecimal.ZERO) {
                                    val utilization = if (card.creditLimit != null && card.creditLimit > BigDecimal.ZERO) {
                                        ((card.balance.toDouble() / card.creditLimit.toDouble()) * 100).toInt()
                                    } else 0
                                    stringResource(R.string.accounts_overview_used, CurrencyFormatter.formatCurrency(card.balance, selectedCurrency), utilization)
                                } else stringResource(R.string.accounts_overview_available_limit),
                                isCredit = true,
                                onClick = { onAccountClick(card.bankName, card.accountLast4) },
                                subtitleColor = if (card.balance > BigDecimal.ZERO) {
                                    val utilization = if (card.creditLimit != null && card.creditLimit > BigDecimal.ZERO) {
                                        ((card.balance.toDouble() / card.creditLimit.toDouble()) * 100).toInt()
                                    } else 0
                                    when {
                                        utilization > 80 -> MaterialTheme.colorScheme.error
                                        utilization > 50 -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                } else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Toggle button if there are many accounts
                if (totalAccounts > 4) {
                    TextButton(
                        onClick = { showAllAccounts = !showAllAccounts },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (showAllAccounts) stringResource(R.string.accounts_overview_show_less) else pluralStringResource(R.plurals.accounts_overview_view_all, totalAccounts, totalAccounts),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactAccountItem(
    bankName: String,
    accountLast4: String,
    formattedAmount: String,
    subtitle: String,
    isCredit: Boolean,
    onClick: () -> Unit,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isCredit) Icons.Default.CreditCard else Icons.Default.AccountBalance,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.Icon.small),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = bankName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Mobile-money wallets have no account number — show just the service name.
            if (accountLast4 != AccountBalanceEntity.WALLET_ACCOUNT_MARKER) {
                Text(
                    text = "••$accountLast4",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = formattedAmount,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = subtitleColor
            )
        }
    }
}