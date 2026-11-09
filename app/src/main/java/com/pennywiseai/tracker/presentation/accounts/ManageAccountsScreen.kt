package com.pennywiseai.tracker.presentation.accounts

import com.pennywiseai.tracker.data.preferences.BankAccountMergeStore

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pennywiseai.tracker.ui.components.SupportDevelopmentDialog
import com.pennywiseai.tracker.ui.components.SupportNudgeCard
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.math.BigDecimal
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAccountsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddAccount: () -> Unit,
    onNavigateToBalanceHistory: (bankName: String, accountLast4: String) -> Unit,
    viewModel: ManageAccountsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
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
    val isProEntitled by viewModel.isProEntitled.collectAsState()
    var showUpgradeSheet by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    val pendingProfileReassign by viewModel.pendingProfileReassign.collectAsState()
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

    // Loan accounts (#792) reuse the bank-account card: AccountItem reads isLoan for
    // the "Outstanding" label and leaves out the low-balance alert.
    val loanAccountItem: @Composable (AccountBalanceEntity, Boolean) -> Unit = { account, isHidden ->
        AccountItem(
            account = account,
            isHidden = isHidden,
            onToggleVisibility = {
                if (isHidden) viewModel.toggleAccountVisibility(account.bankName, account.accountLast4)
                else ignoreAccount(account)
            },
            onUpdateBalance = {
                selectedAccount = account.bankName to account.accountLast4
                selectedAccountEntity = account
                showUpdateDialog = true
            },
            onViewHistory = { onNavigateToBalanceHistory(account.bankName, account.accountLast4) },
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
            }
        )
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
                    IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddAccount,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_account_title))
            }
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
                    description = stringResource(R.string.manage_accounts_empty_description)
                )
            }
        } else {
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
                    top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + Dimensions.Component.fabScrollClearance
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
            ) {
                // Show success message if available
                uiState.successMessage?.let { message ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(Dimensions.Padding.content),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text(
                                    text = message.asString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // F-Droid tip nudge after a merge — persists past the transient
                // success banner; tapping opens the tip jar and dismisses it.
                if (uiState.showSupportNudge) {
                    item {
                        SupportNudgeCard(onClick = {
                            showSupportDialog = true
                            viewModel.dismissSupportNudge()
                        })
                    }
                }

                // Show error message if available
                uiState.errorMessage?.let { message ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(Dimensions.Padding.content),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text(
                                    text = message.asString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
                
                // Loan accounts (#792) get their own section; the rest split into
                // bank accounts and credit cards as before.
                val (loanAccounts, nonLoanAccounts) = uiState.accounts.partition { it.isLoan }
                val (hiddenLoanAccounts, visibleLoanAccounts) = loanAccounts.partition {
                    viewModel.isAccountHidden(it.bankName, it.accountLast4)
                }

                // Separate visible and hidden accounts
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
                
                // Regular Bank Accounts Section (Visible Only)
                if (visibleRegularAccounts.isNotEmpty()) {
                    item {
                        SectionHeaderV2(title = stringResource(R.string.manage_accounts_section_bank))
                    }

                    items(visibleRegularAccounts) { account ->
                        AccountItem(
                            account = account,
                            linkedCards = uiState.linkedCards[account.accountLast4] ?: emptyList(),
                            isHidden = false,
                            isUnanchored = (account.bankName to account.accountLast4) in uiState.unanchoredAccounts,
                            onToggleVisibility = { ignoreAccount(account) },
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
                }

                // Orphaned Cards Section
                if (uiState.orphanedCards.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        SectionHeaderV2(title = stringResource(R.string.manage_accounts_section_unlinked_cards))
                    }
                    
                    items(uiState.orphanedCards) { card ->
                        OrphanedCardItem(
                            card = card,
                            accounts = allRegularAccounts,
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

                // Credit Cards Section (Visible Only)
                if (visibleCreditCards.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        SectionHeaderV2(title = stringResource(R.string.manage_accounts_section_credit_cards))
                    }

                    items(visibleCreditCards) { card ->
                        CreditCardItem(
                            card = card,
                            isHidden = false,
                            onToggleVisibility = { ignoreAccount(card) },
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
                }

                // Loan Accounts Section (Visible Only). Bank loans, not the Lent/Borrowed Loans screen.
                if (visibleLoanAccounts.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        SectionHeaderV2(title = stringResource(R.string.manage_accounts_section_loan_accounts))
                    }

                    items(visibleLoanAccounts) { account -> loanAccountItem(account, false) }
                }

                // Hidden Accounts Section (Collapsible)
                if (hiddenRegularAccounts.isNotEmpty() || hiddenCreditCards.isNotEmpty() || hiddenLoanAccounts.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showHiddenAccounts = !showHiddenAccounts },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Dimensions.Padding.content),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    Icon(
                                        Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(R.string.manage_accounts_hidden_header, hiddenRegularAccounts.size + hiddenCreditCards.size + hiddenLoanAccounts.size),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    if (showHiddenAccounts) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (showHiddenAccounts) stringResource(R.string.accounts_collapse) else stringResource(R.string.accounts_expand),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (showHiddenAccounts) {
                        // Hidden Bank Accounts
                        items(hiddenRegularAccounts) { account ->
                            AccountItem(
                                account = account,
                                linkedCards = uiState.linkedCards[account.accountLast4] ?: emptyList(),
                                isHidden = true,
                                isUnanchored = (account.bankName to account.accountLast4) in uiState.unanchoredAccounts,
                                onToggleVisibility = {
                                    viewModel.toggleAccountVisibility(account.bankName, account.accountLast4)
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

                        items(hiddenLoanAccounts) { account -> loanAccountItem(account, true) }

                        // Hidden Credit Cards
                        items(hiddenCreditCards) { card ->
                            CreditCardItem(
                                card = card,
                                isHidden = true,
                                onToggleVisibility = {
                                    viewModel.toggleAccountVisibility(card.bankName, card.accountLast4)
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
