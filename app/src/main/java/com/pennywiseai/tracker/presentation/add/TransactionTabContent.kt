package com.pennywiseai.tracker.presentation.add

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.error
import com.pennywiseai.tracker.utils.AppDates

import com.pennywiseai.tracker.presentation.transactions.transactionTypeLabel
import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.presentation.categories.CategoryEditDialog
import com.pennywiseai.tracker.ui.components.CategoryMenuItems
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.SelectedCategoryIcon
import com.pennywiseai.tracker.ui.components.CategoryChip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.domain.model.displayName
import com.pennywiseai.tracker.domain.model.getAccountType
import com.pennywiseai.tracker.domain.model.icon
import com.pennywiseai.tracker.presentation.accounts.AccountType
import com.pennywiseai.tracker.ui.components.TagInputField
import com.pennywiseai.tracker.ui.components.cards.GroupedColumn
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.toShape
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.icons.categoryLabel
import androidx.compose.ui.platform.LocalDensity


/** Above this font scale a side-by-side row can't hold its contents, so it stacks. */
internal const val LARGE_FONT_SCALE = 1.3f

/** A three-letter code plus the dropdown arrow. Stacks full width above [LARGE_FONT_SCALE]. */
internal val CurrencyFieldWidth = 112.dp

/** Below this window width the five transaction type chips can't all show in one row. */

/**
 * The form's lead: a label over the amount, with the currency as a chip on the amount's
 * baseline. Moves the chip up beside the label at a large font scale, where sharing a line
 * would squeeze the figure. Shared by the Transaction and Subscription tabs.
 */
@Composable
internal fun AmountCurrencyField(
    amount: String,
    onAmountChange: (String) -> Unit,
    errorText: String?,
    currency: String,
    onCurrencySelected: (String) -> Unit
) {
    val isLargeFont = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    val amountLabel = stringResource(R.string.add_field_amount)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val amountStyle = (if (isLargeFont) PennyWiseText.amountLarge else PennyWiseText.heroAmount)
        .copy(color = onSurface)

    val labelText: @Composable (Modifier) -> Unit = { modifier ->
        Text(
            text = amountLabel,
            style = PennyWiseText.fieldLabel,
            color = if (errorText != null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.clearAndSetSemantics {}
        )
    }

    val amountField: @Composable (Modifier) -> Unit = { modifier ->
        BasicTextField(
            value = amount,
            onValueChange = onAmountChange,
            textStyle = amountStyle,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = modifier.semantics {
                contentDescription = amountLabel
                if (errorText != null) error(errorText)
            },
            decorationBox = { innerTextField ->
                Box {
                    if (amount.isEmpty()) {
                        Text(
                            text = stringResource(R.string.add_amount_placeholder),
                            style = amountStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.smd),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            if (isLargeFont) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
                ) {
                    labelText(Modifier.weight(1f))
                    CurrencyChip(currency = currency, onCurrencySelected = onCurrencySelected)
                }
                amountField(Modifier.fillMaxWidth())
            } else {
                labelText(Modifier)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
                ) {
                    CurrencyChip(
                        currency = currency,
                        onCurrencySelected = onCurrencySelected,
                        modifier = Modifier.alignByBaseline()
                    )
                    amountField(
                        Modifier
                            .weight(1f)
                            .alignByBaseline()
                    )
                }
            }
            if (errorText != null) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** The amount's currency: its code in a chip that opens the list of currencies. */
@Composable
private fun CurrencyChip(
    currency: String,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currencyLabel = stringResource(R.string.add_field_currency)
    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.semantics { contentDescription = currencyLabel }
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = Dimensions.Component.chipHeight)
                    .padding(start = Spacing.smd, end = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = currency,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.Icon.small),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            CurrencyFormatter.getSupportedCurrencies().forEach { code ->
                DropdownMenuItem(
                    text = { Text("${CurrencyFormatter.getCurrencySymbol(code)} $code") },
                    onClick = {
                        onCurrencySelected(code)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** The first thing Save is still waiting for, mirroring [TransactionUiState.isValid]. */
private fun TransactionUiState.missingFieldHint(): Int? = when {
    isValid -> null
    amount.toDoubleOrNull()?.let { it > 0 } != true -> R.string.add_hint_amount
    transactionType == TransactionType.TRANSFER -> R.string.add_hint_transfer_accounts
    merchant.isBlank() -> R.string.add_hint_merchant
    category.isBlank() -> R.string.add_hint_category
    else -> null
}

/**
 * Save, pinned below the form so it rides on top of the keyboard instead of under it. The
 * divider is the edge the form scrolls under; [hint] says what Save is waiting for.
 */
@Composable
internal fun AddSaveBar(
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    hint: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Dimensions.Padding.content, end = Dimensions.Padding.content, top = Spacing.sm)
            )
        }
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.sm)
                .fillMaxWidth()
                .heightIn(min = Dimensions.Component.fab),
            shapes = ButtonDefaults.shapes()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimensions.Icon.small),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Done, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.add_save), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Which account picker the shared account dropdown is currently assigning to. */
private enum class AccountPickerTarget { FROM, TO }

/**
 * The anatomy of every row in the Add forms: icon, small label, then the value or a
 * placeholder. Text fields and pickers share it, so an empty field and a filled one are
 * visibly the same component, with one height whether or not a clear button is showing.
 *
 * @param hugValue Size the row to its value, for a row sharing a line with a wider one.
 */
@Composable
private fun AddFormRowLayout(
    leadingIcon: @Composable () -> Unit,
    label: String,
    labelColor: Color,
    error: String?,
    modifier: Modifier = Modifier,
    hugValue: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
    value: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
        Row(
            modifier = modifier
                .defaultMinSize(minHeight = TextFieldDefaults.MinHeight)
                .padding(horizontal = Spacing.smd, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            leadingIcon()
            Column(modifier = Modifier.weight(1f, fill = !hugValue)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = labelColor
                )
                value()
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            trailing()
        }
    }
}

/** An [AddFormRowLayout] that opens a picker. A null [value] shows [placeholder]. */
@Composable
internal fun AddPickerRow(
    leadingIcon: @Composable () -> Unit,
    label: String,
    value: String?,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    hugValue: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = null
    ) {
        AddFormRowLayout(
            leadingIcon = leadingIcon,
            label = label,
            labelColor = if (error != null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            error = error,
            modifier = Modifier.fillMaxHeight(),
            hugValue = hugValue,
            trailing = trailing
        ) {
            Text(
                text = value ?: placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = if (value != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** An [AddFormRowLayout] you type in. Tapping anywhere on the row focuses the field. */
@Composable
internal fun AddTextRow(
    leadingIcon: @Composable () -> Unit,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    shape: Shape,
    error: String? = null,
    singleLine: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val labelColor = when {
        error != null -> MaterialTheme.colorScheme.error
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            singleLine = singleLine,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                AddFormRowLayout(
                    leadingIcon = leadingIcon,
                    label = label,
                    labelColor = labelColor,
                    error = error,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            }
        )
    }
}

/**
 * Picks an account: the single-account picker, both legs of a TRANSFER, and a subscription's
 * funding account. Shows the alias when set, else the bank name, matching the dropdown items
 * so the account doesn't change name the moment the menu closes (#637), with a clear button
 * once one is chosen.
 */
@Composable
internal fun AccountPickerRow(
    label: String,
    account: AccountBalanceEntity?,
    shape: Shape,
    onClick: () -> Unit,
    onClear: () -> Unit
) {
    AddPickerRow(
        leadingIcon = {
            Icon((account?.getAccountType() ?: AccountType.SAVINGS).icon, contentDescription = null)
        },
        label = label,
        value = account?.let {
            AccountBalanceEntity.accountLabel(
                it.alias?.takeIf { alias -> alias.isNotBlank() } ?: it.bankName,
                it.accountLast4
            )
        },
        placeholder = stringResource(R.string.add_txn_select_account),
        shape = shape,
        onClick = onClick,
        trailing = {
            if (account != null) {
                IconButton(
                    onClick = onClear,
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.add_clear),
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                }
            }
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionTabContent(
    viewModel: AddViewModel,
    onSave: () -> Unit
) {
    val uiState by viewModel.transactionUiState.collectAsState()
    val categories by viewModel.transactionCategories.collectAsStateWithLifecycle()
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    val accounts by viewModel.accounts.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    // Which account picker is open (null = closed). For a TRANSFER this routes the
    // chosen account to either the FROM or TO card; for other types only FROM is used.
    var accountPickerTarget by remember { mutableStateOf<AccountPickerTarget?>(null) }

    val isLargeFont = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    val isTransfer = uiState.transactionType == TransactionType.TRANSFER

    // Open whenever one of these holds a value, so a duplicate or edit never hides data.
    val hasOptionalDetails = uiState.notes.isNotBlank() ||
        uiState.reference.isNotBlank() ||
        uiState.tags.isNotEmpty() ||
        uiState.receiptUri != null
    var showMoreDetails by rememberSaveable { mutableStateOf(hasOptionalDetails) }
    LaunchedEffect(hasOptionalDetails) {
        if (hasOptionalDetails) showMoreDetails = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // ── Amount ──
            AmountCurrencyField(
                amount = uiState.amount,
                onAmountChange = viewModel::updateTransactionAmount,
                errorText = uiState.amountError?.asString(),
                currency = uiState.currency,
                onCurrencySelected = viewModel::updateTransactionCurrency
            )

            // ── Transaction Type chips ──
            // One scrolling row, so "Investment" isn't left alone on a line of its own,
            // until large text or a narrow window would hide labels off the end: then wrap.
            val typeChips: @Composable () -> Unit = {
                TransactionType.values().forEach { type ->
                    FilterChip(
                        selected = uiState.transactionType == type,
                        shapes = FilterChipDefaults.shapes(),
                        onClick = { viewModel.updateTransactionType(type) },
                        label = {
                            Text(stringResource(transactionTypeLabel(type)))
                        },
                        leadingIcon = if (uiState.transactionType == type) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.Icon.small)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderWidth = 0.dp,
                            selected = uiState.transactionType == type,
                            enabled = true
                        )
                    )
                }
            }
            // Wraps at every size, so all five types are always visible.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                typeChips()
            }

            // ── Merchant + Account(s) + Category + Date (connected fields) ──
            // Merchant is meaningless for a TRANSFER (it moves money between own
            // accounts), so hide it and start the group at the From account.
            GroupedList {
                if (!isTransfer) {
                    AddTextRow(
                        leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                        label = "${stringResource(R.string.add_field_merchant)} *",
                        value = uiState.merchant,
                        onValueChange = viewModel::updateTransactionMerchant,
                        placeholder = stringResource(R.string.add_merchant_placeholder),
                        shape = ListItemPosition.Top.toShape(),
                        error = uiState.merchantError?.asString()
                    )
                }

                // The Box anchors the shared menu to the account fields.
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (isTransfer) {
                        // Two account pickers: From (money out) and To (money in).
                        GroupedList {
                            AccountPickerRow(
                                label = stringResource(R.string.add_txn_from_account),
                                account = uiState.selectedAccount,
                                shape = ListItemPosition.Top.toShape(),
                                onClick = { accountPickerTarget = AccountPickerTarget.FROM },
                                onClear = { viewModel.updateSelectedAccount(null) }
                            )
                            AccountPickerRow(
                                label = stringResource(R.string.add_txn_to_account),
                                account = uiState.toAccount,
                                shape = ListItemPosition.Middle.toShape(),
                                onClick = { accountPickerTarget = AccountPickerTarget.TO },
                                onClear = { viewModel.updateToAccount(null) }
                            )
                        }
                    } else {
                        AccountPickerRow(
                            label = stringResource(R.string.add_field_account),
                            account = uiState.selectedAccount,
                            shape = ListItemPosition.Middle.toShape(),
                            onClick = { accountPickerTarget = AccountPickerTarget.FROM },
                            onClear = { viewModel.updateSelectedAccount(null) }
                        )
                    }

                    // Account selection dropdown menu (shared by From/To pickers).
                    DropdownMenu(
                        expanded = accountPickerTarget != null,
                        onDismissRequest = { accountPickerTarget = null }
                    ) {
                        val target = accountPickerTarget
                        // "No account" only makes sense outside a transfer — both transfer
                        // legs must reference a real account to move money.
                        if (!isTransfer) {
                            SelectableDropdownMenuItem(
                                selected = uiState.selectedAccount == null,
                                shapes = MenuDefaults.itemShape(0, 1),
                                colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                                text = {
                                    Column {
                                        Text(stringResource(R.string.add_txn_no_account))
                                        Text(
                                            stringResource(R.string.add_txn_no_account_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.updateSelectedAccount(null)
                                    accountPickerTarget = null
                                },
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                                selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                            )
                            HorizontalDivider()
                        }
                        val selectedForTarget = when (target) {
                            AccountPickerTarget.TO -> uiState.toAccount
                            else -> uiState.selectedAccount
                        }
                        val groupedAccounts = accounts.groupBy { it.getAccountType() }
                        groupedAccounts.forEach { (accountType, accountList) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = accountType.displayName(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                onClick = {},
                                enabled = false
                            )
                            accountList.forEachIndexed { index, account ->
                                SelectableDropdownMenuItem(
                                    selected = selectedForTarget?.id == account.id,
                                    shapes = MenuDefaults.itemShape(index, accountList.size),
                                    colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                                    text = {
                                        Column {
                                            Text(account.displayLabel)
                                            Text(
                                                CurrencyFormatter.formatCurrency(account.balance, account.currency),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        if (target == AccountPickerTarget.TO) {
                                            viewModel.updateToAccount(account)
                                        } else {
                                            viewModel.updateSelectedAccount(account)
                                        }
                                        accountPickerTarget = null
                                    },
                                    leadingIcon = {
                                        Icon(
                                            accountType.icon,
                                            contentDescription = null
                                        )
                                    },
                                    selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                                )
                            }
                        }
                    }
                }

                if (!isTransfer) {
                    // Category field
                    Box(modifier = Modifier.fillMaxWidth()) {
                        AddPickerRow(
                            leadingIcon = { SelectedCategoryIcon(categories.find { it.name == uiState.category }) },
                            label = stringResource(R.string.add_field_category),
                            value = uiState.category.takeIf { it.isNotBlank() }?.let { categoryLabel(it) },
                            placeholder = stringResource(R.string.add_category_placeholder),
                            shape = ListItemPosition.Middle.toShape(),
                            onClick = { showCategoryMenu = true },
                            error = uiState.categoryError?.asString(),
                            trailing = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) }
                        )

                        DropdownMenu(
                            expanded = showCategoryMenu,
                            onDismissRequest = { showCategoryMenu = false }
                        ) {
                            CategoryMenuItems(
                                categories = categories,
                                isIncome = uiState.transactionType == TransactionType.INCOME,
                                onSelected = {
                                    viewModel.updateTransactionCategory(it.name)
                                    showCategoryMenu = false
                                },
                                onAddCategory = {
                                    showCategoryMenu = false
                                    showAddCategoryDialog = true
                                }
                            )
                        }
                    }
                }

                // Date + time close the group; stacked when large text can't share a row.
                val dateLabel = stringResource(R.string.add_field_date)
                val dateValue = uiState.date.format(AppDates.pattern("dd MMMM yyyy"))
                val timeLabel = stringResource(R.string.add_field_time)
                val timeValue = uiState.date.format(AppDates.pattern("h:mm a"))
                val middleShape = ListItemPosition.Middle.toShape()
                val bottomShape = ListItemPosition.Bottom.toShape()
                val dateIcon: @Composable () -> Unit = {
                    Icon(Icons.Default.CalendarToday, contentDescription = null)
                }
                val timeIcon: @Composable () -> Unit = {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                }
                if (isLargeFont) {
                    AddPickerRow(
                        leadingIcon = dateIcon,
                        label = dateLabel,
                        value = dateValue,
                        shape = middleShape,
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    AddPickerRow(
                        leadingIcon = timeIcon,
                        label = timeLabel,
                        value = timeValue,
                        shape = bottomShape,
                        onClick = { showTimePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Layout.groupedListGap)
                    ) {
                        AddPickerRow(
                            leadingIcon = dateIcon,
                            label = dateLabel,
                            value = dateValue,
                            shape = bottomShape.copy(bottomEnd = middleShape.bottomEnd),
                            onClick = { showDatePicker = true },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        AddPickerRow(
                            leadingIcon = timeIcon,
                            label = timeLabel,
                            value = timeValue,
                            shape = bottomShape.copy(bottomStart = middleShape.bottomStart),
                            onClick = { showTimePicker = true },
                            modifier = Modifier.fillMaxHeight(),
                            hugValue = true
                        )
                    }
                }
            }

            if (showAddCategoryDialog) {
                CategoryEditDialog(
                    defaultIsIncome = uiState.transactionType == TransactionType.INCOME,
                    lockType = true,
                    parentOptions = categories,
                    onDismiss = { showAddCategoryDialog = false },
                    onSave = { name, color, _, icon, parentId ->
                        // Close only once the category is created and selected, so the
                        // modal dialog blocks Save / type changes until it lands.
                        viewModel.createAndSelectCategory(name, color, icon, parentId) { showAddCategoryDialog = false }
                    }
                )
            }

            // ── Budget Impact (INCOME only) ──
            if (uiState.transactionType == TransactionType.INCOME) {
                val activeBudgetCategories by viewModel.activeBudgetCategories.collectAsState()
                val refundCategories by viewModel.refundCategories.collectAsState()
                AddBudgetImpactSection(
                    budgetImpactType = uiState.budgetImpactType,
                    budgetCategory = uiState.budgetCategory,
                    activeBudgetCategories = if (uiState.budgetImpactType == BudgetImpactType.DEDUCT_SPENT) refundCategories else activeBudgetCategories,
                    onImpactTypeChange = viewModel::updateBudgetImpactType,
                    onCategoryChange = viewModel::updateBudgetCategory
                )
            }

            // ── Notes + Reference + Tags + Receipt, behind one row ──
            GroupedRow(
                position = ListItemPosition.Single,
                onClick = { showMoreDetails = !showMoreDetails }
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RowLabels(
                    title = stringResource(R.string.quick_add_more_details),
                    subtitle = stringResource(R.string.add_more_details_hint),
                    titleMaxLines = 2
                )
                Icon(
                    if (showMoreDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (showMoreDetails) R.string.accounts_collapse else R.string.accounts_expand
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = showMoreDetails,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())
            ) {
                GroupedList {
                    AddTextRow(
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                        label = stringResource(R.string.add_field_notes),
                        value = uiState.notes,
                        onValueChange = viewModel::updateTransactionNotes,
                        placeholder = stringResource(R.string.add_notes_placeholder),
                        shape = ListItemPosition.Top.toShape(),
                        singleLine = false
                    )

                    AddTextRow(
                        leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
                        label = stringResource(R.string.add_field_reference),
                        value = uiState.reference,
                        onValueChange = viewModel::updateTransactionReference,
                        placeholder = stringResource(R.string.add_reference_placeholder),
                        shape = ListItemPosition.Middle.toShape()
                    )

                    // Tags (create or select existing). TagInputField takes no colours, so
                    // match its container to the fields around it from here.
                    val allTagNames by viewModel.allTagNames.collectAsState()
                    val scheme = MaterialTheme.colorScheme
                    MaterialTheme(
                        colorScheme = scheme.copy(surfaceContainerHighest = scheme.surfaceContainerLow)
                    ) {
                        TagInputField(
                            selectedTags = uiState.tags,
                            allTags = allTagNames,
                            onAddTag = viewModel::addTransactionTag,
                            onRemoveTag = viewModel::removeTransactionTag
                        )
                    }

                    GroupedColumn(position = ListItemPosition.Bottom) {
                        ReceiptPickerSection(
                            receiptUri = uiState.receiptUri,
                            onReceiptSelected = { uri -> viewModel.updateReceiptUri(uri) },
                            onReceiptRemoved = { viewModel.updateReceiptUri(null) },
                            onCreateCameraUri = { viewModel.createCameraUri() }
                        )
                    }
                }
            }
        }

        // Says what Save is waiting for, instead of leaving a disabled button to explain itself.
        val missing = uiState.missingFieldHint()
        AddSaveBar(
            enabled = uiState.isValid && !uiState.isLoading,
            isLoading = uiState.isLoading,
            onClick = { viewModel.saveTransaction(onSuccess = onSave) },
            hint = if (missing != null && !uiState.isLoading) stringResource(missing) else null
        )
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.date
                .toLocalDate()
                .atStartOfDay()
                .toInstant(java.time.ZoneOffset.UTC)
                .toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.updateTransactionDate(millis)
                        }
                        showDatePicker = false
                    },
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.add_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.add_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time Picker Dialog
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = uiState.date.hour,
            initialMinute = uiState.date.minute
        )

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.add_select_time)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateTransactionTime(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    },
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.add_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.add_cancel)) }
            }
        )
    }
}

@Composable
fun ReceiptPickerSection(
    receiptUri: android.net.Uri?,
    onReceiptSelected: (android.net.Uri) -> Unit,
    onReceiptRemoved: () -> Unit,
    onCreateCameraUri: () -> android.net.Uri
) {
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { onReceiptSelected(it) } }

    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> if (success) cameraUri?.let { onReceiptSelected(it) } }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = stringResource(R.string.add_receipt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (receiptUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
            ) {
                AsyncImage(
                    model = receiptUri,
                    contentDescription = stringResource(R.string.add_receipt_image),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp),
                    contentScale = ContentScale.Crop
                )
                FilledIconButton(
                    onClick = onReceiptRemoved,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.xs),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.add_receipt_remove),
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.add_receipt_gallery))
                }
                OutlinedButton(
                    onClick = {
                        val uri = onCreateCameraUri()
                        cameraUri = uri
                        cameraLauncher.launch(uri)
                    },
                    modifier = Modifier.weight(1f),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.add_receipt_camera))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBudgetImpactSection(
    budgetImpactType: BudgetImpactType?,
    budgetCategory: String?,
    activeBudgetCategories: List<String>,
    onImpactTypeChange: (BudgetImpactType?) -> Unit,
    onCategoryChange: (String?) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = stringResource(R.string.txn_detail_budget_impact),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ConnectedChoiceRow(
            options = listOf(null, BudgetImpactType.DEDUCT_SPENT, BudgetImpactType.ADD_TO_LIMIT),
            selected = budgetImpactType,
            onSelect = onImpactTypeChange,
            label = {
                stringResource(
                    when (it) {
                        null -> R.string.txn_detail_budget_impact_none
                        BudgetImpactType.DEDUCT_SPENT -> R.string.txn_detail_budget_impact_refund
                        BudgetImpactType.ADD_TO_LIMIT -> R.string.txn_detail_budget_impact_extra
                    }
                )
            },
            textStyle = MaterialTheme.typography.labelSmall
        )

        if (budgetImpactType != null) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = budgetCategory ?: stringResource(R.string.txn_detail_budget_select_category),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.txn_detail_budget_category)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    if (activeBudgetCategories.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.txn_detail_budget_no_categories)) },
                            onClick = { expanded = false },
                            enabled = false
                        )
                    } else {
                        activeBudgetCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(categoryLabel(category)) },
                                onClick = {
                                    onCategoryChange(category)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
