package com.pennywiseai.tracker.presentation.accounts

import com.pennywiseai.tracker.domain.model.AccountType
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.ui.theme.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import java.math.BigDecimal
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity

@Composable
internal fun AccountItem(
    account: com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity,
    linkedCards: List<com.pennywiseai.tracker.data.database.entity.CardEntity> = emptyList(),
    isHidden: Boolean,
    isUnanchored: Boolean = false,
    onToggleVisibility: () -> Unit,
    onUpdateBalance: () -> Unit,
    onViewHistory: () -> Unit,
    onUnlinkCard: (cardId: Long) -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onEditAccount: () -> Unit = {},
    onSetProfile: (Long) -> Unit = {},
    onSetAlias: (String?) -> Unit = {},
    onSetLowBalanceThreshold: (BigDecimal?) -> Unit = {}
) {
    val isManualAccount = account.sourceType == "MANUAL"
    var showAliasDialog by remember { mutableStateOf(false) }
    var showThresholdDialog by remember { mutableStateOf(false) }
    // Low-balance alert: only for non-credit accounts with a threshold set, when the
    // current balance has fallen at or below it. (Credit cards invert this — their
    // "low" concept is available limit, not balance — so they're excluded.)
    val isLowBalance = !account.isLiability &&
        account.lowBalanceThreshold != null &&
        account.balance <= account.lowBalanceThreshold
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isLowBalance -> MaterialTheme.colorScheme.errorContainer.copy(
                    alpha = if (isHidden) 0.4f else 0.7f
                )
                isHidden -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
            // Set outright: a translucent container matches no theme colour, so the default went black.
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Account Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (account.isLoan) AccountType.LOAN.icon else Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val alias = account.alias?.takeIf { it.isNotBlank() }
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = alias ?: account.bankName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                // Ellipsize a long name instead of pushing the badges
                                // (and the masked number below) off-screen.
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (account.profileId == ProfileEntity.BUSINESS_ID) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        text = stringResource(R.string.manage_accounts_business_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            if (isHidden) {
                                Icon(
                                    Icons.Default.VisibilityOff,
                                    contentDescription = stringResource(R.string.manage_accounts_hidden_badge),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        // Masked account number on its own line below the name. Inlining it
                        // next to a long bank name squeezed it into a per-character vertical
                        // stack; this keeps it as a clean second line. (#465)
                        Text(
                            text = if (alias != null) {
                                AccountBalanceEntity.accountLabel(account.bankName, account.accountLast4)
                            } else {
                                AccountBalanceEntity.accountLabel("", account.accountLast4).trim()
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Balance
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = CurrencyFormatter.formatCurrency(
                            account.balance,
                            // Resolve so the list matches Account Detail — SMS-tracked
                            // non-INR accounts show their parser currency, not stored INR.
                            CurrencyFormatter.resolveAccountCurrency(
                                sourceType = account.sourceType,
                                storedCurrency = account.currency,
                                bankName = account.bankName
                            )
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isLowBalance -> stringResource(R.string.manage_accounts_low_balance)
                            account.isLoan -> stringResource(R.string.manage_accounts_outstanding)
                            else -> stringResource(R.string.manage_accounts_balance)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isLowBalance) FontWeight.Medium else null,
                        color = if (isLowBalance) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // No SMS for this account ever stated a balance, so the figure is
            // 0 ± its transactions. Point at Update Balance; it goes once set (#982).
            if (isUnanchored && !account.isLiability) {
                Surface(
                    onClick = onUpdateBalance,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = Dimensions.Component.minTouchTarget),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = stringResource(R.string.manage_accounts_estimated_balance_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Linked Cards Section
            if (linkedCards.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = Spacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.manage_accounts_linked_cards),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.xs)
                    )
                    linkedCards.forEach { card ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = Spacing.xs),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CreditCard,
                                        contentDescription = null,
                                        modifier = Modifier.size(Dimensions.Icon.small),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Column {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                        ) {
                                            Text(
                                                text = "••${card.cardLast4}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            if (!card.isActive) {
                                                Text(
                                                    text = stringResource(R.string.manage_accounts_card_inactive),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                        // Show last transaction date if available
                                        if (card.lastBalanceDate != null) {
                                            Text(
                                                text = stringResource(R.string.manage_accounts_card_updated, card.lastBalanceDate.format(AppDates.dayMonthFormatter())),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { onUnlinkCard(card.id) },
                                    modifier = Modifier.size(Dimensions.Icon.medium),
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LinkOff,
                                        contentDescription = stringResource(R.string.manage_accounts_unlink_card),
                                        modifier = Modifier.size(Dimensions.Icon.small),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Action Buttons - Primary action + overflow menu
            var showMenu by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary action
                OutlinedButton(
                    onClick = if (isManualAccount) onEditAccount else onUpdateBalance,
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(if (isManualAccount) stringResource(R.string.manage_accounts_action_edit) else stringResource(R.string.manage_accounts_update_balance))
                }

                // Overflow menu
                Box {
                    IconButton(onClick = { showMenu = true }, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.manage_accounts_more_options)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        // SMS-tracked accounts reach the edit dialog (name, currency,
                        // type) from here; manual ones have it as the primary button.
                        if (!isManualAccount) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.manage_accounts_action_edit)) },
                                onClick = {
                                    showMenu = false
                                    onEditAccount()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.manage_accounts_menu_history)) },
                            onClick = {
                                showMenu = false
                                onViewHistory()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isHidden) stringResource(R.string.manage_accounts_menu_show) else stringResource(R.string.manage_accounts_menu_hide)) },
                            onClick = {
                                showMenu = false
                                onToggleVisibility()
                            },
                            leadingIcon = {
                                Icon(
                                    if (isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (account.profileId == ProfileEntity.BUSINESS_ID) stringResource(R.string.manage_accounts_menu_mark_personal) else stringResource(R.string.manage_accounts_menu_mark_business)) },
                            onClick = {
                                showMenu = false
                                val newProfileId = if (account.profileId == ProfileEntity.BUSINESS_ID)
                                    ProfileEntity.PERSONAL_ID else ProfileEntity.BUSINESS_ID
                                onSetProfile(newProfileId)
                            },
                            leadingIcon = {
                                Icon(
                                    if (account.profileId == ProfileEntity.BUSINESS_ID) Icons.Default.Person else Icons.Default.Business,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (account.alias.isNullOrBlank()) stringResource(R.string.manage_accounts_menu_set_alias) else stringResource(R.string.manage_accounts_menu_rename)) },
                            onClick = {
                                showMenu = false
                                showAliasDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DriveFileRenameOutline,
                                    contentDescription = null
                                )
                            }
                        )
                        // Only non-credit accounts — credit cards' "low" concept is
                        // available limit, not balance, so the alert (and this entry)
                        // don't apply (matches the isLowBalance exclusion above). Same
                        // for loans, whose balance is owed.
                        if (!account.isLiability) {
                            DropdownMenuItem(
                                text = { Text(if (account.lowBalanceThreshold == null) stringResource(R.string.manage_accounts_low_balance_alert) else stringResource(R.string.manage_accounts_menu_edit_low_balance_alert)) },
                                onClick = {
                                    showMenu = false
                                    showThresholdDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.NotificationsActive,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.accounts_action_delete)) },
                            onClick = {
                                showMenu = false
                                onDeleteAccount()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
            }
        }
    }

    if (showAliasDialog) {
        AccountAliasDialog(
            currentAlias = account.alias,
            accountLabel = AccountBalanceEntity.accountLabel(account.bankName, account.accountLast4),
            onDismiss = { showAliasDialog = false },
            onConfirm = { newAlias ->
                onSetAlias(newAlias)
                showAliasDialog = false
            }
        )
    }

    if (showThresholdDialog) {
        LowBalanceThresholdDialog(
            currentThreshold = account.lowBalanceThreshold,
            accountLabel = AccountBalanceEntity.accountLabel(account.bankName, account.accountLast4),
            currency = CurrencyFormatter.resolveAccountCurrency(
                account.sourceType, account.currency, account.bankName
            ),
            currentBalance = account.balance,
            onDismiss = { showThresholdDialog = false },
            onConfirm = { threshold ->
                onSetLowBalanceThreshold(threshold)
                showThresholdDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LowBalanceThresholdDialog(
    currentThreshold: BigDecimal?,
    accountLabel: String,
    currency: String,
    currentBalance: BigDecimal,
    onDismiss: () -> Unit,
    onConfirm: (BigDecimal?) -> Unit
) {
    var text by remember { mutableStateOf(currentThreshold?.toPlainString().orEmpty()) }
    val parsed = text.trim().toBigDecimalOrNull()
    val context = LocalContext.current
    // A refusal still saves: the card turns red either way, only the notification needs this.
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { onConfirm(parsed) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_accounts_low_balance_alert)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = accountLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.manage_accounts_low_balance_alert_description, CurrencyFormatter.formatCurrency(currentBalance, currency)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.manage_accounts_threshold_label, currency)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val needsPermission = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (needsPermission) {
                        notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onConfirm(parsed)
                    }
                },
                enabled = parsed != null && parsed >= BigDecimal.ZERO,
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(R.string.accounts_action_save)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (currentThreshold != null) {
                    TextButton(onClick = { onConfirm(null) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.manage_accounts_action_clear)) }
                }
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.accounts_action_cancel)) }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountAliasDialog(
    currentAlias: String?,
    accountLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var aliasText by remember { mutableStateOf(currentAlias.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_accounts_rename_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = accountLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = aliasText,
                    onValueChange = { aliasText = it },
                    label = { Text(stringResource(R.string.manage_accounts_alias_label)) },
                    placeholder = { Text(stringResource(R.string.manage_accounts_alias_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.manage_accounts_alias_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(aliasText.trim().ifBlank { null }) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.accounts_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.accounts_action_cancel))
            }
        }
    )
}
