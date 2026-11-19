package com.pennywiseai.tracker.presentation.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.preferences.BankAccountMergeStore
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.SupportDevelopmentDialog
import com.pennywiseai.tracker.ui.components.SupportNudgeCard
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.IconTile
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.mascot.Mascot
import com.pennywiseai.tracker.ui.components.mascot.MascotShape
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.sumByCurrency
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAccountsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddAccount: () -> Unit,
    onNavigateToBalanceHistory: (bankName: String, accountLast4: String) -> Unit,
    onNavigateToNetWorth: () -> Unit = {},
    viewModel: ManageAccountsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showUpdateDialog by remember { mutableStateOf(false) }
    var selectedAccount by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedAccountEntity by remember { mutableStateOf<com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showHiddenAccounts by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity?>(null) }
    // Account merge (#368) — single screen-level entry point; the sheet handles
    // source + target selection + confirmation in one self-contained flow.
    var showMergeSheet by remember { mutableStateOf(false) }
    val isProEntitled by viewModel.isProEntitled.collectAsStateWithLifecycle()
    var showUpgradeSheet by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    val pendingProfileReassign by viewModel.pendingProfileReassign.collectAsStateWithLifecycle()
    val dismissedDuplicates = remember { mutableStateListOf<String>() }
    val accountDuplicate = BankAccountMergeStore
        .duplicatePairs(uiState.accounts)
        .firstOrNull { (source, target) ->
            BankAccountMergeStore.mappingKey(source.bankName, source.currency, source.accountLast4) !in dismissedDuplicates &&
                !viewModel.isAccountHidden(source.bankName, source.accountLast4) &&
                !viewModel.isAccountHidden(target.bankName, target.accountLast4)
        }

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    // Ignoring moves the account into the collapsed "Ignored Accounts" section, so
    // expand it and offer an immediate Undo — otherwise it looks like it vanished.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.manage_accounts_undo)
    val ignoreAccount: (AccountBalanceEntity) -> Unit = { account ->
        viewModel.toggleAccountVisibility(account.bankName, account.accountLast4)
        showHiddenAccounts = true
        val label = AccountBalanceEntity.accountLabel(
            account.alias?.takeIf { it.isNotBlank() } ?: account.bankName,
            account.accountLast4
        )
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.manage_accounts_ignored_snackbar, label),
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            // Skip if it was already un-ignored from the menu meanwhile.
            if (result == SnackbarResult.ActionPerformed &&
                viewModel.isAccountHidden(account.bankName, account.accountLast4)
            ) {
                viewModel.toggleAccountVisibility(account.bankName, account.accountLast4)
            }
        }
    }

    // Editing can rename the account, which moves its ignored key: drop any pending
    // Undo so it can't silently no-op against the old name. The account stays in
    // the expanded Ignored section, so "Stop ignoring" is still one tap away.
    LaunchedEffect(showEditDialog) {
        if (showEditDialog) snackbarHostState.currentSnackbarData?.dismiss()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.manage_accounts_title),
                hasBackButton = true,
                navigationContent = {
                    IconButton(
                        onClick = onNavigateBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accounts_back))
                    }
                },
                actionContent = {
                    // Show Merge only when there are at least 2 accounts to choose between.
                    // Pro-only feature — free users see the icon (so the feature is
                    // discoverable) but the tap routes to the paywall instead.
                    if (uiState.accounts.size >= 2) {
                        IconButton(onClick = {
                            if (isProEntitled) showMergeSheet = true
                            else showUpgradeSheet = true
                        }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Default.Merge, contentDescription = stringResource(R.string.merge_accounts_title))
                        }
                    }
                },
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
        if (uiState.accounts.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                PennyWiseEmptyState(
                    icon = Icons.Default.AccountBalance,
                    headline = stringResource(R.string.manage_accounts_empty_title),
                    description = stringResource(R.string.manage_accounts_empty_description),
                    actionLabel = stringResource(R.string.add_account_title),
                    onAction = onNavigateToAddAccount,
                    mascot = { Mascot(size = Dimensions.Icon.extraLarge, shape = MascotShape.Wallet, interactive = true) }
                )
            }
        } else {
            // Loan accounts (#792) get their own section; the rest split into
            // bank accounts and credit cards.
            val (loanAccounts, nonLoanAccounts) = uiState.accounts.partition { it.isLoan }
            val (hiddenLoanAccounts, visibleLoanAccounts) = loanAccounts.partition {
                viewModel.isAccountHidden(it.bankName, it.accountLast4)
            }
            val visibleRegularAccounts = nonLoanAccounts.filter {
                !it.isCreditCard && !viewModel.isAccountHidden(it.bankName, it.accountLast4)
            }
            val visibleCreditCards = nonLoanAccounts.filter {
                it.isCreditCard && !viewModel.isAccountHidden(it.bankName, it.accountLast4)
            }
            val hiddenRegularAccounts = nonLoanAccounts.filter {
                !it.isCreditCard && viewModel.isAccountHidden(it.bankName, it.accountLast4)
            }
            val hiddenCreditCards = nonLoanAccounts.filter {
                it.isCreditCard && viewModel.isAccountHidden(it.bankName, it.accountLast4)
            }
            val allRegularAccounts = nonLoanAccounts.filter { !it.isCreditCard }
            val hiddenCount = hiddenRegularAccounts.size + hiddenCreditCards.size + hiddenLoanAccounts.size

            val bankAccountItem: @Composable (AccountBalanceEntity, Boolean, ListItemPosition) -> Unit = { account, isHidden, position ->
                AccountItem(
                    account = account,
                    // Cards link to bank accounts; a loan with the same last digits must not pick them up.
                    linkedCards = if (account.isLoan) emptyList() else uiState.linkedCards[account.accountLast4] ?: emptyList(),
                    isHidden = isHidden,
                    isUnanchored = (account.bankName to account.accountLast4) in uiState.unanchoredAccounts,
                    position = position,
                    onToggleVisibility = {
                        if (isHidden) viewModel.toggleAccountVisibility(account.bankName, account.accountLast4)
                        else ignoreAccount(account)
                    },
                    onUpdateBalance = {
                        selectedAccount = account.bankName to account.accountLast4
                        selectedAccountEntity = account
                        showUpdateDialog = true
                    },
                    onViewHistory = {
                        onNavigateToBalanceHistory(account.bankName, account.accountLast4)
                    },
                    onUnlinkCard = { cardId ->
                        viewModel.unlinkCard(cardId)
                    },
                    onDeleteAccount = {
                        accountToDelete = account.bankName to account.accountLast4
                        showDeleteConfirmDialog = true
                    },
                    onEditAccount = {
                        accountToEdit = account
                        showEditDialog = true
                    },
                    onSetProfile = { profileId ->
                        viewModel.setAccountProfile(account.bankName, account.accountLast4, profileId)
                    },
                    onSetAlias = { alias ->
                        viewModel.setAccountAlias(account.bankName, account.accountLast4, alias)
                    },
                    onSetLowBalanceThreshold = { threshold ->
                        viewModel.setLowBalanceThreshold(account.bankName, account.accountLast4, threshold)
                    }
                )
            }

            val creditCardItem: @Composable (AccountBalanceEntity, Boolean, ListItemPosition) -> Unit = { card, isHidden, position ->
                CreditCardItem(
                    card = card,
                    isHidden = isHidden,
                    position = position,
                    onToggleVisibility = {
                        if (isHidden) viewModel.toggleAccountVisibility(card.bankName, card.accountLast4)
                        else ignoreAccount(card)
                    },
                    onUpdateBalance = {
                        selectedAccount = card.bankName to card.accountLast4
                        selectedAccountEntity = card
                        showUpdateDialog = true
                    },
                    onViewHistory = {
                        onNavigateToBalanceHistory(card.bankName, card.accountLast4)
                    },
                    onDeleteAccount = {
                        accountToDelete = card.bankName to card.accountLast4
                        showDeleteConfirmDialog = true
                    },
                    onEditAccount = {
                        accountToEdit = card
                        showEditDialog = true
                    },
                    onSetStatementDay = { day ->
                        viewModel.setStatementDay(card.bankName, card.accountLast4, day)
                    }
                )
            }

            val lazyListState = rememberLazyListState()
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .background(MaterialTheme.colorScheme.background)
                    .overScrollVertical(),
                contentPadding = PaddingValues(
                    start = Dimensions.Padding.content,
                    end = Dimensions.Padding.content,
                    top = Spacing.sm + paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + Spacing.Layout.scrollBottomPadding
                ),
                // Rows of one section join into a segmented block; headers carry the
                // gap between sections.
                verticalArrangement = Arrangement.spacedBy(Spacing.Layout.groupedListGap),
                flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
            ) {
                uiState.successMessage?.let { message ->
                    item {
                        StatusBanner(
                            text = message.asString(),
                            icon = Icons.Default.CheckCircle,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // F-Droid tip nudge after a merge — persists past the transient
                // success banner; tapping opens the tip jar and dismisses it.
                if (uiState.showSupportNudge) {
                    item {
                        Box(Modifier.padding(bottom = Spacing.sm)) {
                            SupportNudgeCard(onClick = {
                                showSupportDialog = true
                                viewModel.dismissSupportNudge()
                            })
                        }
                    }
                }

                uiState.errorMessage?.let { message ->
                    item {
                        StatusBanner(
                            text = message.asString(),
                            icon = Icons.Default.Error,
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // What the visible accounts hold and what the visible cards owe,
                // each summed per currency (never across currencies).
                if (visibleRegularAccounts.isNotEmpty() || visibleCreditCards.isNotEmpty()) {
                    item(key = "summary") {
                        AccountsSummaryCards(
                            bankAccounts = visibleRegularAccounts,
                            creditCards = visibleCreditCards,
                            modifier = Modifier.padding(bottom = Spacing.sm)
                        )
                    }
                }

                item(key = "net_worth") {
                    GroupedRow(position = ListItemPosition.Single, onClick = onNavigateToNetWorth) {
                        IconTile(
                            icon = Icons.AutoMirrored.Filled.ShowChart,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                        RowLabels(
                            title = stringResource(R.string.net_worth_title),
                            subtitle = stringResource(R.string.net_worth_summary_hint)
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Dimensions.Icon.inline)
                        )
                    }
                }

                if (visibleRegularAccounts.isNotEmpty()) {
                    item { AccountsSectionHeader(stringResource(R.string.manage_accounts_section_bank)) }
                    itemsIndexed(visibleRegularAccounts, key = { _, a -> accountKey(a) }) { index, account ->
                        Box(Modifier.animateItem()) {
                            bankAccountItem(account, false, ListItemPosition.from(index, visibleRegularAccounts.size))
                        }
                    }
                }

                if (uiState.orphanedCards.isNotEmpty()) {
                    item { AccountsSectionHeader(stringResource(R.string.manage_accounts_section_unlinked_cards)) }
                    itemsIndexed(uiState.orphanedCards, key = { _, c -> "card|${c.id}" }) { index, card ->
                        Box(Modifier.animateItem()) {
                            OrphanedCardItem(
                                card = card,
                                accounts = allRegularAccounts,
                                position = ListItemPosition.from(index, uiState.orphanedCards.size),
                                onLinkToAccount = { accountLast4 ->
                                    viewModel.linkCardToAccount(card.id, accountLast4)
                                },
                                onDeleteCard = { cardId ->
                                    viewModel.deleteCard(cardId)
                                },
                                onUpdateCard = { bankName, cardType, nickname ->
                                    viewModel.updateCardDetails(card.id, bankName, cardType, nickname)
                                }
                            )
                        }
                    }
                }

                if (visibleCreditCards.isNotEmpty()) {
                    item { AccountsSectionHeader(stringResource(R.string.manage_accounts_section_credit_cards)) }
                    itemsIndexed(visibleCreditCards, key = { _, a -> accountKey(a) }) { index, card ->
                        Box(Modifier.animateItem()) {
                            creditCardItem(card, false, ListItemPosition.from(index, visibleCreditCards.size))
                        }
                    }
                }

                // Bank loans, not the Lent/Borrowed Loans screen. Loan accounts (#792)
                // reuse the bank-account row: it reads isLoan for the "Outstanding"
                // label and leaves out the low-balance alert.
                if (visibleLoanAccounts.isNotEmpty()) {
                    item { AccountsSectionHeader(stringResource(R.string.manage_accounts_section_loan_accounts)) }
                    itemsIndexed(visibleLoanAccounts, key = { _, a -> accountKey(a) }) { index, account ->
                        Box(Modifier.animateItem()) {
                            bankAccountItem(account, false, ListItemPosition.from(index, visibleLoanAccounts.size))
                        }
                    }
                }

                // Ignored accounts, collapsed under one row.
                if (hiddenCount > 0) {
                    item {
                        Spacer(Modifier.height(Spacing.md))
                        GroupedRow(
                            position = if (showHiddenAccounts) ListItemPosition.Top else ListItemPosition.Single,
                            onClick = { showHiddenAccounts = !showHiddenAccounts },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Icon(
                                Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.manage_accounts_hidden_header, hiddenCount),
                                style = PennyWiseText.rowTitle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (showHiddenAccounts) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showHiddenAccounts) stringResource(R.string.accounts_collapse) else stringResource(R.string.accounts_expand),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (showHiddenAccounts) {
                        // One block under the header row: banks, then loans, then cards.
                        val hidden = hiddenRegularAccounts + hiddenLoanAccounts + hiddenCreditCards
                        itemsIndexed(hidden, key = { _, a -> accountKey(a) }) { index, account ->
                            // The header row is the block's top, so no hidden row is.
                            val position = if (index == hidden.lastIndex) ListItemPosition.Bottom else ListItemPosition.Middle
                            Box(Modifier.animateItem()) {
                                if (account.isCreditCard) creditCardItem(account, true, position)
                                else bankAccountItem(account, true, position)
                            }
                        }
                    }
                }

                item(key = "add_account") {
                    OutlinedButton(
                        onClick = onNavigateToAddAccount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.md)
                            .heightIn(min = Dimensions.Component.listItemMinHeight),
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.inline))
                        Spacer(Modifier.width(Spacing.sm))
                        Text(stringResource(R.string.add_account_title), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }

    // Update Balance Dialog
    if (showUpdateDialog && selectedAccount != null && selectedAccountEntity != null) {
        if (selectedAccountEntity!!.isCreditCard) {
            // Credit Card Update Dialog
            UpdateCreditCardDialog(
                bankName = selectedAccount!!.first,
                accountLast4 = selectedAccount!!.second,
                currentOutstanding = selectedAccountEntity!!.balance,
                currentLimit = selectedAccountEntity!!.creditLimit ?: BigDecimal.ZERO,
                onDismiss = {
                    showUpdateDialog = false
                    selectedAccount = null
                    selectedAccountEntity = null
                },
                onConfirm = { newBalance, newLimit ->
                    viewModel.updateCreditCard(
                        selectedAccount!!.first,
                        selectedAccount!!.second,
                        newBalance,
                        newLimit
                    )
                    showUpdateDialog = false
                    selectedAccount = null
                    selectedAccountEntity = null
                }
            )
        } else {
            // Regular Account Update Dialog
            UpdateBalanceDialog(
                bankName = selectedAccount!!.first,
                accountLast4 = selectedAccount!!.second,
                onDismiss = {
                    showUpdateDialog = false
                    selectedAccount = null
                    selectedAccountEntity = null
                },
                onConfirm = { newBalance ->
                    viewModel.updateAccountBalance(
                        selectedAccount!!.first,
                        selectedAccount!!.second,
                        newBalance
                    )
                    showUpdateDialog = false
                    selectedAccount = null
                    selectedAccountEntity = null
                }
            )
        }
    }

    // Delete Account Confirmation Dialog
    if (showDeleteConfirmDialog && accountToDelete != null) {
        DeleteAccountConfirmDialog(
            bankName = accountToDelete!!.first,
            accountLast4 = accountToDelete!!.second,
            onDismiss = {
                showDeleteConfirmDialog = false
                accountToDelete = null
            },
            onConfirm = {
                viewModel.deleteAccount(accountToDelete!!.first, accountToDelete!!.second)
                showDeleteConfirmDialog = false
                accountToDelete = null
            }
        )
    }

    // Edit Account Dialog
    if (showEditDialog && accountToEdit != null) {
        EditAccountDialog(
            account = accountToEdit!!,
            onDismiss = {
                showEditDialog = false
                accountToEdit = null
            },
            onConfirm = { newBankName, newBalance, newCreditLimit, newCurrency, newType ->
                viewModel.editAccount(
                    oldBankName = accountToEdit!!.bankName,
                    accountLast4 = accountToEdit!!.accountLast4,
                    newBankName = newBankName,
                    newBalance = newBalance,
                    newCreditLimit = newCreditLimit,
                    newType = newType,
                    newCurrency = newCurrency
                )
                showEditDialog = false
                accountToEdit = null
            }
        )
    }

    if (!showMergeSheet && !showDeleteConfirmDialog && !showEditDialog && !showUpdateDialog &&
        !showUpgradeSheet && !showSupportDialog && pendingProfileReassign == null && accountDuplicate != null) {
        val (source, target) = accountDuplicate
        val duplicateKey = BankAccountMergeStore.mappingKey(source.bankName, source.currency, source.accountLast4)
        DuplicateAccountDialog(
            source = source,
            target = target,
            onMerge = {
                dismissedDuplicates.add(duplicateKey)
                viewModel.repairDuplicateAccounts(source, target)
            },
            onDismiss = { dismissedDuplicates.add(duplicateKey) }
        )
    }

    // Merge accounts sheet (#368)
    if (showMergeSheet) {
        MergeAccountsSheet(
            accounts = uiState.accounts,
            countTransactionsOn = { bankName, last4 ->
                viewModel.countTransactionsOn(bankName, last4)
            },
            onConfirm = { source, target ->
                viewModel.mergeAccounts(source, target)
                showMergeSheet = false
            },
            onDismiss = { showMergeSheet = false }
        )
    }

    if (showUpgradeSheet) {
        com.pennywiseai.tracker.presentation.paywall.UpgradeSheet(
            onDismiss = { showUpgradeSheet = false },
        )
    }

    if (showSupportDialog) {
        SupportDevelopmentDialog(onDismiss = { showSupportDialog = false })
    }

    // Offer to move existing transactions that carry an explicit, mismatched
    // profile after the account's profile changes (#420).
    pendingProfileReassign?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingProfileReassign() },
            title = { Text(stringResource(R.string.manage_accounts_reassign_title)) },
            text = {
                Text(
                    pluralStringResource(R.plurals.manage_accounts_reassign_message, pending.transactionCount, pending.transactionCount)
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.applyPendingProfileReassign() }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.manage_accounts_reassign_move))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPendingProfileReassign() }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.manage_accounts_reassign_keep))
                }
            }
        )
    }
}

/** A section heading inside the accounts list; carries the gap above it. */
@Composable
private fun AccountsSectionHeader(title: String) {
    SectionHeaderV2(title = title, topSpacing = Spacing.md)
}

/** A one-line outcome (merge done, update failed) on a tonal container. */
@Composable
private fun StatusBanner(
    text: String,
    icon: ImageVector,
    container: Color,
    content: Color,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.sm),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content
    ) {
        Row(
            modifier = Modifier.padding(Dimensions.Padding.content),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * A row key from the logical account (bank + last digits), not the latest
 * balance row's id, which changes with every new balance. Each account sits in
 * exactly one list, so the key is unique across the screen.
 */
private fun accountKey(account: AccountBalanceEntity) = "account|${account.bankName}|${account.accountLast4}"

/**
 * The two tonal cards heading the list: what the bank accounts hold and what the
 * cards owe. Each is totalled per currency, so a mixed set reads "₹1,250 · $600"
 * rather than one meaningless sum.
 */
@Composable
private fun AccountsSummaryCards(
    bankAccounts: List<AccountBalanceEntity>,
    creditCards: List<AccountBalanceEntity>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        if (bankAccounts.isNotEmpty()) {
            val held = bankAccounts.sumByCurrency(
                currencySelector = {
                    CurrencyFormatter.resolveAccountCurrency(
                        sourceType = it.sourceType,
                        storedCurrency = it.currency,
                        bankName = it.bankName
                    )
                },
                amountSelector = { it.balance }
            )
            AccountsSummaryCard(
                icon = Icons.Default.AccountBalance,
                label = stringResource(R.string.manage_accounts_summary_held),
                amount = CurrencyFormatter.formatByCurrency(held, keepSign = true),
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
        if (creditCards.isNotEmpty()) {
            val owed = creditCards.sumByCurrency(currencySelector = { it.currency }, amountSelector = { it.balance })
            AccountsSummaryCard(
                icon = Icons.Default.CreditCard,
                label = stringResource(R.string.manage_accounts_summary_owed),
                amount = CurrencyFormatter.formatByCurrency(owed, keepSign = true),
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun AccountsSummaryCard(
    icon: ImageVector,
    label: String,
    amount: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.Padding.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.medium))
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            val amountStyle = PennyWiseText.amountLarge
            Text(
                text = amount,
                style = amountStyle,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(PennyWiseText.autoSizeFloor, amountStyle.fontSize)
            )
        }
    }
}
