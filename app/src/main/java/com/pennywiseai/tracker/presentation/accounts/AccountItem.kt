package com.pennywiseai.tracker.presentation.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.ui.components.RoundedField
import com.pennywiseai.tracker.ui.components.cards.GroupedColumn
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.toShape
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

/** Which kind of account a row shows: picks its badge shape and colour. */
internal enum class AccountBadgeKind { BANK, CARD, LOAN }

/**
 * The account's first letter on a soft, Expressive-shaped badge, so a list of
 * accounts reads by name at a glance. Banks, cards and loans each get their own
 * shape and colour role.
 */
@Composable
internal fun AccountBadge(
    name: String,
    kind: AccountBadgeKind,
    modifier: Modifier = Modifier,
    rowFill: Color? = null,
) {
    val colors = MaterialTheme.colorScheme
    val (shape, color) = when (kind) {
        AccountBadgeKind.BANK -> MaterialShapes.Cookie9Sided.toShape() to colors.primary
        AccountBadgeKind.CARD -> MaterialShapes.Clover4Leaf.toShape() to colors.secondary
        AccountBadgeKind.LOAN -> MaterialShapes.SoftBurst.toShape() to colors.tertiary
    }
    val fill = color.copy(alpha = Dimensions.Alpha.categoryTint)
    // Secondary and tertiary are fills, not ink: check the letter against the tint as it composites
    // over the row ([rowFill] when tinted, else each neutral surface it could sit on).
    val ink = color.readableOn((rowFill?.let(::listOf) ?: colors.neutralSurfaces).map { fill.compositeOver(it) })
    Box(
        modifier = modifier
            .size(Dimensions.Icon.avatarLarge)
            .clip(shape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = ink,
        )
    }
}

/**
 * One account row in Manage Accounts: a segmented surface whose tap opens the
 * account's actions. Hidden (ignored) accounts are drawn faded; [highlight] tints
 * a row that needs attention (a low balance).
 */
@Composable
internal fun AccountRowFrame(
    position: ListItemPosition,
    isHidden: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val container = accountRowContainer(highlight, isHidden)
    val clickLabel = stringResource(R.string.manage_accounts_more_options)
    // Content colour set outright: a translucent container matches no theme colour, so the default went black.
    GroupedColumn(
        position = position,
        onClick = onClick,
        modifier = modifier.semantics { onClick(label = clickLabel) { onClick(); true } },
        containerColor = container,
        contentColor = MaterialTheme.colorScheme.onSurface.onAccountRow(highlight, isHidden),
        contentPadding = PaddingValues(Dimensions.Padding.card),
        verticalArrangement = Arrangement.spacedBy(Spacing.smd),
        content = content,
    )
}

@Composable
private fun accountRowContainer(highlight: Boolean, isHidden: Boolean): Color = when {
    highlight -> MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isHidden) 0.4f else 0.7f)
    isHidden -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
    else -> MaterialTheme.colorScheme.surfaceContainerLow
}

/**
 * This ink on a row from [AccountRowFrame]. The low-balance tint matches no on* role
 * (errorContainer is near-black in some light themes), so on it the ink is checked against
 * the fill as composited on the list. Unchanged on a neutral row.
 */
@Composable
internal fun Color.onAccountRow(highlight: Boolean, isHidden: Boolean): Color {
    if (!highlight) return this
    val fill = accountRowContainer(highlight, isHidden).compositeOver(MaterialTheme.colorScheme.background)
    return readableOn(listOf(fill))
}

/** Where a row's extra lines start: under its name, past the badge. */
internal val AccountRowIndent = Dimensions.Icon.avatarLarge + Spacing.md

@Composable
internal fun AccountItem(
    account: AccountBalanceEntity,
    linkedCards: List<com.pennywiseai.tracker.data.database.entity.CardEntity> = emptyList(),
    isHidden: Boolean,
    isUnanchored: Boolean = false,
    position: ListItemPosition = ListItemPosition.Single,
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
    var showMenu by remember { mutableStateOf(false) }
    var showAliasDialog by remember { mutableStateOf(false) }
    var showThresholdDialog by remember { mutableStateOf(false) }
    // Low-balance alert: only for non-credit accounts with a threshold set, when the
    // current balance has fallen at or below it. (Credit cards invert this — their
    // "low" concept is available limit, not balance — so they're excluded.)
    val isLowBalance = !account.isLiability &&
        account.lowBalanceThreshold != null &&
        account.balance <= account.lowBalanceThreshold
    val alias = account.alias?.takeIf { it.isNotBlank() }
    val name = alias ?: account.bankName
    // Resolve so the list matches Account Detail — SMS-tracked non-INR accounts
    // show their parser currency, not stored INR.
    val currency = CurrencyFormatter.resolveAccountCurrency(
        sourceType = account.sourceType,
        storedCurrency = account.currency,
        bankName = account.bankName
    )

    Box {
        AccountRowFrame(
            position = position,
            isHidden = isHidden,
            highlight = isLowBalance,
            onClick = { showMenu = true },
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountBadge(
                    name = name,
                    kind = if (account.isLoan) AccountBadgeKind.LOAN else AccountBadgeKind.BANK,
                    rowFill = if (isLowBalance) {
                        accountRowContainer(highlight = true, isHidden).compositeOver(MaterialTheme.colorScheme.background)
                    } else {
                        null
                    },
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            style = PennyWiseText.rowTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // Ellipsize a long name instead of pushing the badges
                            // (and the masked number below) off-screen.
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (account.profileId == ProfileEntity.BUSINESS_ID) {
                            BusinessBadge()
                        }
                        if (isHidden) {
                            Icon(
                                Icons.Default.VisibilityOff,
                                contentDescription = stringResource(R.string.manage_accounts_hidden_badge),
                                modifier = Modifier.size(Dimensions.Icon.small),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.onAccountRow(isLowBalance, isHidden)
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
                        style = PennyWiseText.rowSubtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.onAccountRow(isLowBalance, isHidden),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CurrencyFormatter.formatCurrency(account.balance, currency),
                        style = PennyWiseText.amountRow,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // A plain balance needs no label; a low or owed one does.
                    when {
                        isLowBalance -> Text(
                            text = stringResource(R.string.manage_accounts_low_balance),
                            style = PennyWiseText.metadata,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error.onAccountRow(isLowBalance, isHidden)
                        )
                        account.isLoan -> Text(
                            text = stringResource(R.string.manage_accounts_outstanding),
                            style = PennyWiseText.metadata,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // No SMS for this account ever stated a balance, so the figure is
            // 0 ± its transactions. Point at Update Balance; it goes once set (#982).
            if (isUnanchored && !account.isLiability) {
                Surface(
                    onClick = onUpdateBalance,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AccountRowIndent)
                        .defaultMinSize(minHeight = Dimensions.Component.minTouchTarget),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.smd, vertical = Spacing.sm),
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

            if (linkedCards.isNotEmpty()) {
                LinkedCards(
                    cards = linkedCards,
                    onUnlinkCard = onUnlinkCard,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant.onAccountRow(isLowBalance, isHidden)
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            shape = MaterialTheme.shapes.large
        ) {
            // Manual accounts are edited outright; SMS-tracked ones get their balance
            // corrected first, and reach the edit dialog (name, currency, type) next.
            if (!isManualAccount) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.manage_accounts_update_balance)) },
                    onClick = {
                        showMenu = false
                        onUpdateBalance()
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manage_accounts_action_edit)) },
                onClick = {
                    showMenu = false
                    onEditAccount()
                },
                leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manage_accounts_menu_history)) },
                onClick = {
                    showMenu = false
                    onViewHistory()
                },
                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
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
                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) }
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
                    leadingIcon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) }
                )
            }
            DeleteAccountMenuItem(onClick = {
                showMenu = false
                onDeleteAccount()
            })
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
            currency = currency,
            currentBalance = account.balance,
            onDismiss = { showThresholdDialog = false },
            onConfirm = { threshold ->
                onSetLowBalanceThreshold(threshold)
                showThresholdDialog = false
            }
        )
    }
}

@Composable
private fun BusinessBadge() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = stringResource(R.string.manage_accounts_business_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs)
        )
    }
}

/** The debit cards linked to an account, each with its own unlink action. */
@Composable
private fun LinkedCards(
    cards: List<com.pennywiseai.tracker.data.database.entity.CardEntity>,
    onUnlinkCard: (cardId: Long) -> Unit,
    labelColor: Color,
) {
    Column(
        modifier = Modifier.padding(start = AccountRowIndent),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Text(
            text = stringResource(R.string.manage_accounts_linked_cards),
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            modifier = Modifier.padding(bottom = Spacing.xxs)
        )
        cards.forEachIndexed { index, card ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = ListItemPosition.from(index, cards.size).toShape()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.smd, top = Spacing.xxs, bottom = Spacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
                        if (card.lastBalanceDate != null) {
                            Text(
                                text = stringResource(R.string.manage_accounts_card_updated, card.lastBalanceDate.format(AppDates.dayMonthFormatter())),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { onUnlinkCard(card.id) }, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = stringResource(R.string.manage_accounts_unlink_card),
                            modifier = Modifier.size(Dimensions.Icon.inline),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** The destructive last entry of an account's menu. */
@Composable
internal fun DeleteAccountMenuItem(onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.accounts_action_delete)) },
        onClick = onClick,
        leadingIcon = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
    )
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedField.shape
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedField.shape
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
