package com.pennywiseai.tracker.presentation.add

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.pennywiseai.shared.domain.money.MinorUnits
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.model.AccountType
import com.pennywiseai.tracker.domain.model.AmountInput
import com.pennywiseai.tracker.domain.model.getAccountType
import com.pennywiseai.tracker.presentation.accounts.displayName
import com.pennywiseai.tracker.presentation.accounts.icon
import com.pennywiseai.tracker.presentation.categories.CategoryEditDialog
import com.pennywiseai.tracker.presentation.transactions.transactionTypeLabel
import com.pennywiseai.tracker.ui.components.AmountCursorAtEnd
import com.pennywiseai.tracker.ui.components.AmountKeyboardBar
import com.pennywiseai.tracker.ui.components.CategoryChip
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.QuickCategoryPickerSheet
import com.pennywiseai.tracker.ui.components.RoundedField
import com.pennywiseai.tracker.ui.components.TagInputField
import com.pennywiseai.tracker.ui.components.amountFieldValue
import com.pennywiseai.tracker.ui.components.amountSumHint
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.money
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.text.DecimalFormatSymbols
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Which account picker the shared account menu is currently assigning to. */
private enum class AccountPickerTarget { FROM, TO }

/** The types on the face of the form; the rest sit under "More types" until picked. */
private val MAIN_TYPES = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)

/**
 * The accounts to pick from, grouped by type, with an optional "none" first. [accounts] is
 * already narrowed by the caller (a transfer's legs share a currency and differ).
 */
@Composable
internal fun AccountMenu(
    expanded: Boolean,
    accounts: List<AccountBalanceEntity>,
    selected: AccountBalanceEntity?,
    onPick: (AccountBalanceEntity?) -> Unit,
    onDismiss: () -> Unit,
    noneLabel: String? = null,
    noneHint: String? = null
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, shape = MaterialTheme.shapes.large) {
        if (noneLabel != null) {
            SelectableDropdownMenuItem(
                selected = selected == null,
                shapes = MenuDefaults.itemShape(0, 1),
                colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                text = {
                    Column {
                        Text(noneLabel)
                        noneHint?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                onClick = { onPick(null) },
                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
            )
        }
        accounts.groupBy { it.getAccountType() }.forEach { (accountType, accountList) ->
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
                    selected = selected?.id == account.id,
                    shapes = MenuDefaults.itemShape(index, accountList.size),
                    colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                    text = {
                        Column {
                            Text(account.displayLabel)
                            Text(
                                CurrencyFormatter.formatCurrency(account.balance, account.currency),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = { onPick(account) },
                    leadingIcon = { Icon(accountType.icon, contentDescription = null) },
                    selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                )
            }
        }
    }
}

/** The leading glyph of an account row: the account's type, or a generic bank. */
@Composable
internal fun AccountGlyph(account: AccountBalanceEntity?) {
    Icon(
        (account?.getAccountType() ?: AccountType.SAVINGS).icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * The transaction form, in the order a person fills it: type → amount → merchant →
 * category / account / date → the optional rest folded under "More details" → Save,
 * kept above the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionTabContent(
    viewModel: AddViewModel,
    onSave: () -> Unit
) {
    val uiState by viewModel.transactionUiState.collectAsStateWithLifecycle()
    val categories by viewModel.transactionCategories.collectAsStateWithLifecycle()
    val mostUsedCategories by viewModel.mostUsedCategories.collectAsStateWithLifecycle()
    // Non-null while the create dialog is open: its pre-filled name (#1058).
    var addCategoryName by remember { mutableStateOf<String?>(null) }
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }
    // Closing the picker would hand focus back to a text field and pop its keyboard up again.
    val focusManager = LocalFocusManager.current
    var showTimePicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showMoreTypes by remember { mutableStateOf(false) }
    // Which account picker is open (null = closed). For a TRANSFER this routes the
    // chosen account to either the FROM or TO row; for other types only FROM is used.
    var accountPickerTarget by remember { mutableStateOf<AccountPickerTarget?>(null) }
    // A sum typed into the amount ("120+35"); null while it holds a plain amount. The form's
    // amount is always what the sum comes to (empty until it's finished), so Save checks that.
    var amountSum by rememberSaveable { mutableStateOf<String?>(null) }
    var amountFocused by remember { mutableStateOf(false) }
    var amountCursor by remember { mutableStateOf(AmountCursorAtEnd) }
    val locale = LocalConfiguration.current.locales[0]
    val decimalComma = remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator == ',' }
    val digits = MinorUnits.digits(uiState.entryCurrency)
    fun setAmountText(text: String) {
        if (AmountInput.hasOperator(text)) {
            amountSum = text
            val result = AmountInput.value(text, digits)?.takeIf { it.signum() > 0 }
            viewModel.updateTransactionAmount(result?.toPlainString().orEmpty())
        } else {
            amountSum = null
            viewModel.updateTransactionAmount(text)
        }
    }
    fun foldAmount() {
        amountSum?.let { sum ->
            AmountInput.fold(sum, digits)?.let {
                setAmountText(it)
                amountCursor = AmountCursorAtEnd
            }
        }
    }
    var moreOpen by rememberSaveable { mutableStateOf(false) }

    val isTransfer = uiState.transactionType == TransactionType.TRANSFER
    // A budget impact without its category can't be saved: keep that field in view.
    val moreExpanded = moreOpen || (uiState.budgetImpactType != null && uiState.budgetCategory == null)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.Padding.content, vertical = Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // ── Type: the usual three, and the one picked under More types once it is ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConnectedChoiceRow(
                    options = MAIN_TYPES + listOfNotNull(uiState.transactionType.takeIf { it !in MAIN_TYPES }),
                    selected = uiState.transactionType,
                    onSelect = viewModel::updateTransactionType,
                    label = { stringResource(transactionTypeLabel(it)) },
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { showMoreTypes = true }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.MoreHoriz, contentDescription = stringResource(R.string.add_more_types))
                    }
                    DropdownMenu(
                        expanded = showMoreTypes,
                        onDismissRequest = { showMoreTypes = false },
                        shape = MaterialTheme.shapes.large
                    ) {
                        TransactionType.entries.filter { it !in MAIN_TYPES }.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(stringResource(transactionTypeLabel(type))) },
                                onClick = {
                                    viewModel.updateTransactionType(type)
                                    showMoreTypes = false
                                }
                            )
                        }
                    }
                }
            }

            // ── Amount (or a sum, with the operator row on the keyboard) and its currency
            // (a transfer's is the From account's) ──
            val sumHint = amountSum?.let { amountSumHint(it, uiState.entryCurrency) }
            TextField(
                value = amountFieldValue(amountSum ?: uiState.amount, amountCursor),
                onValueChange = { typed ->
                    AmountInput.normalize(typed.text, decimalComma)?.let {
                        setAmountText(it)
                        amountCursor = if (it == typed.text) typed.selection else AmountCursorAtEnd
                    }
                },
                label = { Text(stringResource(R.string.add_field_amount)) },
                textStyle = MaterialTheme.typography.headlineSmall.money(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = sumHint?.isError ?: (uiState.amountError != null),
                supportingText = when {
                    sumHint != null -> { { Text(sumHint.text) } }
                    else -> uiState.amountError?.let { { Text(it.asString()) } }
                },
                leadingIcon = {
                    CurrencyButton(
                        currency = uiState.entryCurrency,
                        onPick = viewModel::updateTransactionCurrency,
                        enabled = !(isTransfer && uiState.selectedAccount != null)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        // Leaving the field finishes the sum.
                        if (amountFocused && !state.isFocused) foldAmount()
                        amountFocused = state.isFocused
                    },
                singleLine = true,
                shape = RoundedField.shape,
                colors = RoundedField.colors()
            )

            // ── Merchant: a transfer moves money between own accounts, so it has none ──
            if (!isTransfer) {
                TextField(
                    value = uiState.merchant,
                    onValueChange = viewModel::updateTransactionMerchant,
                    label = { Text(stringResource(R.string.quick_add_where_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                    isError = uiState.merchantError != null,
                    supportingText = uiState.merchantError?.let { { Text(it.asString()) } },
                    shape = RoundedField.shape,
                    colors = RoundedField.colors()
                )
            }

            // ── Category / Account (From + To for a transfer) / Date ──
            val noAccount = stringResource(R.string.add_txn_no_account)
            val select = stringResource(R.string.add_select)
            val rows = 3
            GroupedList {
                if (isTransfer) {
                    PickerRow(
                        position = ListItemPosition.from(0, rows),
                        label = stringResource(R.string.add_txn_from_account),
                        value = uiState.selectedAccount?.displayLabel ?: select,
                        onClick = { focusManager.clearFocus(); accountPickerTarget = AccountPickerTarget.FROM },
                        leading = { AccountGlyph(uiState.selectedAccount) },
                        menu = {
                            AccountMenu(
                                expanded = accountPickerTarget == AccountPickerTarget.FROM,
                                accounts = accounts.filter { it.id != uiState.toAccount?.id },
                                selected = uiState.selectedAccount,
                                onPick = { viewModel.updateSelectedAccount(it); accountPickerTarget = null },
                                onDismiss = { accountPickerTarget = null }
                            )
                        }
                    )
                    PickerRow(
                        position = ListItemPosition.from(1, rows),
                        label = stringResource(R.string.add_txn_to_account),
                        value = uiState.toAccount?.displayLabel ?: select,
                        onClick = { focusManager.clearFocus(); accountPickerTarget = AccountPickerTarget.TO },
                        leading = { AccountGlyph(uiState.toAccount) },
                        menu = {
                            // Only a different account in the same currency can take the money.
                            val from = uiState.selectedAccount
                            AccountMenu(
                                expanded = accountPickerTarget == AccountPickerTarget.TO,
                                accounts = accounts.filter { from == null || (it.id != from.id && it.currency == from.currency) },
                                selected = uiState.toAccount,
                                onPick = { viewModel.updateToAccount(it); accountPickerTarget = null },
                                onDismiss = { accountPickerTarget = null }
                            )
                        }
                    )
                } else {
                    val selectedCategory = categories.find { it.name == uiState.category }
                    PickerRow(
                        position = ListItemPosition.from(0, rows),
                        label = stringResource(R.string.add_field_category),
                        value = categoryLabel(uiState.category),
                        onClick = { focusManager.clearFocus(); showCategoryPicker = true },
                        leading = {
                            if (selectedCategory != null) {
                                CategoryChip(category = selectedCategory, showText = false)
                            } else {
                                Icon(Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        supporting = uiState.categoryError?.asString()
                    )
                    PickerRow(
                        position = ListItemPosition.from(1, rows),
                        label = stringResource(R.string.add_field_account),
                        value = uiState.selectedAccount?.displayLabel ?: noAccount,
                        valueColor = if (uiState.selectedAccount != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        onClick = { focusManager.clearFocus(); accountPickerTarget = AccountPickerTarget.FROM },
                        leading = { AccountGlyph(uiState.selectedAccount) },
                        menu = {
                            AccountMenu(
                                expanded = accountPickerTarget == AccountPickerTarget.FROM,
                                accounts = accounts,
                                selected = uiState.selectedAccount,
                                onPick = { viewModel.updateSelectedAccount(it); accountPickerTarget = null },
                                onDismiss = { accountPickerTarget = null },
                                noneLabel = noAccount,
                                noneHint = stringResource(R.string.add_txn_no_account_hint)
                            )
                        }
                    )
                }
                PickerRow(
                    position = ListItemPosition.from(2, rows),
                    label = stringResource(R.string.add_field_date),
                    value = uiState.date.format(AppDates.dateFormatter()),
                    onClick = { focusManager.clearFocus(); showDatePicker = true },
                    leading = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
            }

            // ── More details: time, notes, reference, tags, budget impact, receipt ──
            val filled = listOfNotNull(
                stringResource(R.string.add_detail_notes).takeIf { uiState.notes.isNotBlank() },
                stringResource(R.string.add_detail_reference).takeIf { uiState.reference.isNotBlank() },
                "${stringResource(R.string.add_detail_tags)} (${uiState.tags.size})".takeIf { uiState.tags.isNotEmpty() },
                stringResource(R.string.txn_detail_budget_impact).takeIf { uiState.budgetImpactType != null },
                stringResource(R.string.add_detail_receipt).takeIf { uiState.receiptUri != null }
            )
            val timeText = uiState.date.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(AppDates.locale()))
            GroupedList {
                MoreDetailsRow(
                    position = if (moreExpanded) ListItemPosition.Top else ListItemPosition.Single,
                    expanded = moreExpanded,
                    summary = filled.joinToString(" · ").ifEmpty { stringResource(R.string.add_more_details_hint) },
                    onToggle = { moreOpen = !moreExpanded }
                )
                if (moreExpanded) {
                    PickerRow(
                        position = ListItemPosition.Bottom,
                        label = stringResource(R.string.add_field_time),
                        value = timeText,
                        onClick = { focusManager.clearFocus(); showTimePicker = true },
                        leading = { Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                }
            }
            if (moreExpanded) {
                TextField(
                    value = uiState.notes,
                    onValueChange = viewModel::updateTransactionNotes,
                    label = { Text(stringResource(R.string.add_field_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                    shape = RoundedField.shape,
                    colors = RoundedField.colors()
                )
                TextField(
                    value = uiState.reference,
                    onValueChange = viewModel::updateTransactionReference,
                    label = { Text(stringResource(R.string.add_field_reference)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
                    shape = RoundedField.shape,
                    colors = RoundedField.colors()
                )
                val allTagNames by viewModel.allTagNames.collectAsStateWithLifecycle()
                TagInputField(
                    selectedTags = uiState.tags,
                    allTags = allTagNames,
                    onAddTag = viewModel::addTransactionTag,
                    onRemoveTag = viewModel::removeTransactionTag
                )
                if (uiState.transactionType == TransactionType.INCOME) {
                    val activeBudgetCategories by viewModel.activeBudgetCategories.collectAsStateWithLifecycle()
                    val refundCategories by viewModel.refundCategories.collectAsStateWithLifecycle()
                    AddBudgetImpactSection(
                        budgetImpactType = uiState.budgetImpactType,
                        budgetCategory = uiState.budgetCategory,
                        activeBudgetCategories = if (uiState.budgetImpactType == BudgetImpactType.DEDUCT_SPENT) refundCategories else activeBudgetCategories,
                        onImpactTypeChange = viewModel::updateBudgetImpactType,
                        onCategoryChange = viewModel::updateBudgetCategory
                    )
                }
                ReceiptPickerSection(
                    receiptUri = uiState.receiptUri,
                    onReceiptSelected = { uri -> viewModel.updateReceiptUri(uri) },
                    onReceiptRemoved = { viewModel.updateReceiptUri(null) },
                    onCreateCameraUri = { viewModel.createCameraUri() }
                )
            }

            // Room for the Save bar laid over the end of the form.
            Spacer(modifier = Modifier.height(Dimensions.Component.fab + Spacing.xxl))
        }

        // A sheet, not a dropdown menu, so its search field can get the keyboard (#1058).
        if (showCategoryPicker) {
            QuickCategoryPickerSheet(
                currentCategory = uiState.category,
                categories = categories,
                onCategorySelected = {
                    viewModel.updateTransactionCategory(it)
                    showCategoryPicker = false
                },
                onDismiss = { showCategoryPicker = false },
                mostUsed = mostUsedCategories,
                onAddCategory = {
                    showCategoryPicker = false
                    addCategoryName = it
                }
            )
        }

        addCategoryName?.let { initialName ->
            CategoryEditDialog(
                defaultIsIncome = uiState.transactionType == TransactionType.INCOME,
                lockType = true,
                initialName = initialName,
                parentOptions = categories,
                onDismiss = { addCategoryName = null },
                onSave = { name, color, _, icon, parentId ->
                    // Close only once the category is created and selected, so the
                    // modal dialog blocks Save / type changes until it lands.
                    viewModel.createAndSelectCategory(name, color, icon, parentId) { addCategoryName = null }
                }
            )
        }

        // While the amount is being typed, the operator row takes Save's place on the keyboard.
        if (amountFocused && WindowInsets.isImeVisible) {
            AmountKeyboardBar(
                onOperator = { op ->
                    setAmountText(AmountInput.append(amountSum ?: uiState.amount, op))
                    amountCursor = AmountCursorAtEnd
                },
                onEquals = ::foldAmount,
                onDone = {
                    foldAmount()
                    focusManager.clearFocus()
                },
                // The screen's scaffold already pads for the navigation bar, which the
                // keyboard's inset includes: only the rest of the keyboard's height here.
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
            )
        } else {
            SaveBar(
                error = uiState.error?.asString(),
                enabled = uiState.isValid && !uiState.isLoading,
                loading = uiState.isLoading,
                onClick = { viewModel.saveTransaction(onSuccess = onSave) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (showDatePicker) {
        AddDatePickerDialog(
            initial = uiState.date.toLocalDate(),
            onPick = viewModel::updateTransactionDate,
            onDismiss = { showDatePicker = false }
        )
    }

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

/**
 * Save, pinned to the bottom above the keyboard, with a save failure said right above it
 * (the draft stays, so the user can fix it and try again).
 */
@Composable
internal fun SaveBar(
    error: String?,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Fade in above the bar only; behind it stays solid, so a disabled
            // (translucent) Save never shows the form through it.
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
                    endY = with(LocalDensity.current) { Spacing.lg.toPx() }
                )
            )
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = Dimensions.Padding.content)
            .padding(top = Spacing.lg, bottom = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimensions.Component.fab),
            shapes = ButtonDefaults.shapes()
        ) {
            if (loading) {
                LoadingIndicator(modifier = Modifier.size(Dimensions.Icon.medium))
            } else {
                Icon(Icons.Default.Done, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.add_save), style = MaterialTheme.typography.titleMedium)
            }
        }
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
                        .padding(Spacing.xs)
                        .size(28.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.add_receipt_remove),
                        modifier = Modifier.size(16.dp),
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
                    shape = RoundedField.shape,
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
                    onDismissRequest = { expanded = false },
                    shape = MaterialTheme.shapes.large
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
