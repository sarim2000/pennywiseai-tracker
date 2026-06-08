package com.pennywiseai.tracker.presentation.accounts

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
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.math.BigDecimal
import com.pennywiseai.tracker.data.database.entity.ProfileEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAccountsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddAccount: () -> Unit,
    viewModel: ManageAccountsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showUpdateDialog by remember { mutableStateOf(false) }
    var selectedAccount by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedAccountEntity by remember { mutableStateOf<com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var historyAccount by remember { mutableStateOf<Pair<String, String>?>(null) }
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
    val pendingProfileReassign by viewModel.pendingProfileReassign.collectAsState()

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = "Accounts",
                hasBackButton = true,
                hasActionButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        }) {
                            Icon(Icons.Default.Merge, contentDescription = "Merge accounts")
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
                Icon(Icons.Default.Add, contentDescription = "Add Account")
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
                    headline = "No Accounts",
                    description = "Add your first bank account to start tracking."
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
                    bottom = 0.dp
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
                                    text = message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
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
                                    text = message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
                
                // Separate visible and hidden accounts
                val visibleRegularAccounts = uiState.accounts.filter {
                    !it.isCreditCard && !viewModel.isAccountHidden(it.bankName, it.accountLast4)
                }
                val visibleCreditCards = uiState.accounts.filter {
                    it.isCreditCard && !viewModel.isAccountHidden(it.bankName, it.accountLast4)
                }
                val hiddenRegularAccounts = uiState.accounts.filter {
                    !it.isCreditCard && viewModel.isAccountHidden(it.bankName, it.accountLast4)
                }
                val hiddenCreditCards = uiState.accounts.filter {
                    it.isCreditCard && viewModel.isAccountHidden(it.bankName, it.accountLast4)
                }
                val allRegularAccounts = uiState.accounts.filter { !it.isCreditCard }
                
                // Regular Bank Accounts Section (Visible Only)
                if (visibleRegularAccounts.isNotEmpty()) {
                    item {
                        SectionHeaderV2(title = "Bank Accounts")
                    }

                    items(visibleRegularAccounts) { account ->
                        AccountItem(
                            account = account,
                            linkedCards = uiState.linkedCards[account.accountLast4] ?: emptyList(),
                            isHidden = false,
                            onToggleVisibility = {
                                viewModel.toggleAccountVisibility(account.bankName, account.accountLast4)
                            },
                            onUpdateBalance = {
                                selectedAccount = account.bankName to account.accountLast4
                                selectedAccountEntity = account
                                showUpdateDialog = true
                            },
                            onViewHistory = {
                                historyAccount = account.bankName to account.accountLast4
                                viewModel.loadBalanceHistory(account.bankName, account.accountLast4)
                                showHistoryDialog = true
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
                            }
                        )
                    }
                }

                // Orphaned Cards Section
                if (uiState.orphanedCards.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        SectionHeaderV2(title = "Unlinked Cards")
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
                        SectionHeaderV2(title = "Credit Cards")
                    }

                    items(visibleCreditCards) { card ->
                        CreditCardItem(
                            card = card,
                            isHidden = false,
                            onToggleVisibility = {
                                viewModel.toggleAccountVisibility(card.bankName, card.accountLast4)
                            },
                            onUpdateBalance = {
                                selectedAccount = card.bankName to card.accountLast4
                                selectedAccountEntity = card
                                showUpdateDialog = true
                            },
                            onViewHistory = {
                                historyAccount = card.bankName to card.accountLast4
                                viewModel.loadBalanceHistory(card.bankName, card.accountLast4)
                                showHistoryDialog = true
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

                // Hidden Accounts Section (Collapsible)
                if (hiddenRegularAccounts.isNotEmpty() || hiddenCreditCards.isNotEmpty()) {
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
                                        text = "Hidden Accounts (${hiddenRegularAccounts.size + hiddenCreditCards.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    if (showHiddenAccounts) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (showHiddenAccounts) "Collapse" else "Expand",
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
                                onToggleVisibility = {
                                    viewModel.toggleAccountVisibility(account.bankName, account.accountLast4)
                                },
                                onUpdateBalance = {
                                    selectedAccount = account.bankName to account.accountLast4
                                    selectedAccountEntity = account
                                    showUpdateDialog = true
                                },
                                onViewHistory = {
                                    historyAccount = account.bankName to account.accountLast4
                                    viewModel.loadBalanceHistory(account.bankName, account.accountLast4)
                                    showHistoryDialog = true
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
                                }
                            )
                        }

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
                                    historyAccount = card.bankName to card.accountLast4
                                    viewModel.loadBalanceHistory(card.bankName, card.accountLast4)
                                    showHistoryDialog = true
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
    
    // Balance History Dialog
    if (showHistoryDialog && historyAccount != null) {
        BalanceHistoryDialog(
            bankName = historyAccount!!.first,
            accountLast4 = historyAccount!!.second,
            balanceHistory = uiState.balanceHistory,
            onDismiss = {
                showHistoryDialog = false
                historyAccount = null
                viewModel.clearBalanceHistory()
            },
            onDeleteBalance = { id ->
                viewModel.deleteBalanceRecord(id, historyAccount!!.first, historyAccount!!.second)
            },
            onUpdateBalance = { id, newBalance ->
                viewModel.updateBalanceRecord(id, newBalance, historyAccount!!.first, historyAccount!!.second)
            }
        )
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
            onConfirm = { newBankName, newBalance, newCreditLimit ->
                viewModel.editAccount(
                    oldBankName = accountToEdit!!.bankName,
                    accountLast4 = accountToEdit!!.accountLast4,
                    newBankName = newBankName,
                    newBalance = newBalance,
                    newCreditLimit = newCreditLimit,
                    isCreditCard = accountToEdit!!.isCreditCard
                )
                showEditDialog = false
                accountToEdit = null
            }
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

    // Offer to move existing transactions that carry an explicit, mismatched
    // profile after the account's profile changes (#420).
    pendingProfileReassign?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingProfileReassign() },
            title = { Text("Move existing transactions?") },
            text = {
                Text(
                    "${pending.transactionCount} transaction(s) from this account are set to a " +
                        "different profile. Move them to the new profile too?"
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.applyPendingProfileReassign() }) {
                    Text("Move them")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPendingProfileReassign() }) {
                    Text("Keep as is")
                }
            }
        )
    }
}

@Composable
private fun CreditCardItem(
    card: com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity,
    isHidden: Boolean,
    onToggleVisibility: () -> Unit,
    onUpdateBalance: () -> Unit,
    onViewHistory: () -> Unit,
    onDeleteAccount: () -> Unit,
    onEditAccount: () -> Unit = {},
    onSetStatementDay: (Int?) -> Unit = {}
) {
    var showStatementDayDialog by remember { mutableStateOf(false) }
    val isManualAccount = card.sourceType == "MANUAL"
    val available = (card.creditLimit ?: BigDecimal.ZERO) - card.balance
    val utilization = if (card.creditLimit != null && card.creditLimit > BigDecimal.ZERO) {
        ((card.balance.toDouble() / card.creditLimit.toDouble()) * 100).toInt()
    } else {
        0
    }
    
    val utilizationColor = when {
        utilization > 70 -> MaterialTheme.colorScheme.error
        utilization > 30 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isHidden) {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Credit Card Header
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
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = card.bankName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "••${card.accountLast4}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isHidden) {
                                Icon(
                                    Icons.Default.VisibilityOff,
                                    contentDescription = "Hidden",
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Credit Card Details
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                // Outstanding Balance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Outstanding",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.formatCurrency(card.balance, card.currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (card.balance > BigDecimal.ZERO) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                
                // Available Credit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.formatCurrency(available, card.currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Credit Limit with Utilization
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Credit Limit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = CurrencyFormatter.formatCurrency(card.creditLimit ?: BigDecimal.ZERO, card.currency),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "($utilization% used)",
                            style = MaterialTheme.typography.bodySmall,
                            color = utilizationColor,
                            fontWeight = FontWeight.Medium
                        )
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
                    onClick = if (isManualAccount) onEditAccount else onUpdateBalance
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(if (isManualAccount) "Edit" else "Update")
                }

                // Overflow menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More options"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("History") },
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
                            text = { Text(if (isHidden) "Show" else "Hide") },
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
                            text = {
                                Text(
                                    if (card.statementDay != null) "Statement date: ${card.statementDay}"
                                    else "Set statement date"
                                )
                            },
                            onClick = {
                                showMenu = false
                                showStatementDayDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
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

    if (showStatementDayDialog) {
        StatementDayPickerDialog(
            currentDay = card.statementDay,
            onDismiss = { showStatementDayDialog = false },
            onConfirm = { day ->
                onSetStatementDay(day)
                showStatementDayDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatementDayPickerDialog(
    currentDay: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit
) {
    var selectedDay by remember { mutableIntStateOf(currentDay ?: 1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Statement date") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Day of month when your credit card statement closes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { if (selectedDay > 1) selectedDay-- }
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease")
                    }
                    Text(
                        text = "$selectedDay",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = Spacing.lg)
                    )
                    IconButton(
                        onClick = { if (selectedDay < 28) selectedDay++ }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedDay) }) {
                Text("Save")
            }
        },
        dismissButton = {
            if (currentDay != null) {
                TextButton(onClick = { onConfirm(null) }) {
                    Text("Clear")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun AccountItem(
    account: com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity,
    linkedCards: List<com.pennywiseai.tracker.data.database.entity.CardEntity> = emptyList(),
    isHidden: Boolean,
    onToggleVisibility: () -> Unit,
    onUpdateBalance: () -> Unit,
    onViewHistory: () -> Unit,
    onUnlinkCard: (cardId: Long) -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onEditAccount: () -> Unit = {},
    onSetProfile: (Long) -> Unit = {},
    onSetAlias: (String?) -> Unit = {}
) {
    val isManualAccount = account.sourceType == "MANUAL"
    var showAliasDialog by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isHidden) {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
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
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
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
                                overflow = TextOverflow.Ellipsis
                            )
                            if (alias == null) {
                                Text(
                                    text = "••${account.accountLast4}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (account.profileId == ProfileEntity.BUSINESS_ID) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        text = "Business",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            if (isHidden) {
                                Icon(
                                    Icons.Default.VisibilityOff,
                                    contentDescription = "Hidden",
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        // When an alias is set, keep the underlying bank/last-4
                        // visible as a subtitle so the account stays identifiable.
                        if (alias != null) {
                            Text(
                                text = "${account.bankName} ••${account.accountLast4}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Balance
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = CurrencyFormatter.formatCurrency(account.balance, account.currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Balance",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Linked Cards Section
            if (linkedCards.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = Spacing.sm)
                ) {
                    Text(
                        text = "Linked Cards",
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
                                                    text = "(Inactive)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                        // Show last transaction date if available
                                        if (card.lastBalanceDate != null) {
                                            Text(
                                                text = "Updated: ${card.lastBalanceDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd"))}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { onUnlinkCard(card.id) },
                                    modifier = Modifier.size(Dimensions.Icon.medium)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LinkOff,
                                        contentDescription = "Unlink card",
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
                    onClick = if (isManualAccount) onEditAccount else onUpdateBalance
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(if (isManualAccount) "Edit" else "Update Balance")
                }

                // Overflow menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More options"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("History") },
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
                            text = { Text(if (isHidden) "Show" else "Hide") },
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
                            text = { Text(if (account.profileId == ProfileEntity.BUSINESS_ID) "Mark as Personal" else "Mark as Business") },
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
                            text = { Text(if (account.alias.isNullOrBlank()) "Set alias" else "Rename") },
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
                        DropdownMenuItem(
                            text = { Text("Delete") },
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
            accountLabel = "${account.bankName} ••${account.accountLast4}",
            onDismiss = { showAliasDialog = false },
            onConfirm = { newAlias ->
                onSetAlias(newAlias)
                showAliasDialog = false
            }
        )
    }
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
        title = { Text("Rename account") },
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
                    label = { Text("Alias") },
                    placeholder = { Text("e.g. Salary account") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Leave blank to clear the alias.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(aliasText.trim().ifBlank { null }) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateBalanceDialog(
    bankName: String,
    accountLast4: String,
    onDismiss: () -> Unit,
    onConfirm: (BigDecimal) -> Unit
) {
    var balanceText by remember { mutableStateOf("") }
    var isValid by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Update Balance")
                Text(
                    text = "$bankName ••$accountLast4",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            TextField(
                value = balanceText,
                onValueChange = { text ->
                    if (text.isEmpty() || text.matches(Regex("^\\d*\\.?\\d*$"))) {
                        balanceText = text
                        isValid = text.isNotBlank() && text.toDoubleOrNull() != null
                    }
                },
                label = { Text("New Balance") },
                placeholder = { Text("0.00") },
                leadingIcon = {
                    Text(
                        text = CurrencyFormatter.getCurrencySymbol(CurrencyFormatter.getBankBaseCurrency(bankName)),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    balanceText.toBigDecimalOrNull()?.let { balance ->
                        onConfirm(balance)
                    }
                },
                enabled = isValid
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateCreditCardDialog(
    bankName: String,
    accountLast4: String,
    currentOutstanding: BigDecimal,
    currentLimit: BigDecimal,
    onDismiss: () -> Unit,
    onConfirm: (BigDecimal, BigDecimal) -> Unit
) {
    var outstandingText by remember { mutableStateOf(currentOutstanding.toString()) }
    var limitText by remember { mutableStateOf(currentLimit.toString()) }
    var isValid by remember { mutableStateOf(false) }
    
    LaunchedEffect(outstandingText, limitText) {
        isValid = outstandingText.isNotBlank() && 
                  outstandingText.toDoubleOrNull() != null &&
                  limitText.isNotBlank() && 
                  limitText.toDoubleOrNull() != null
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Update Credit Card")
                Text(
                    text = "$bankName ••$accountLast4",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                TextField(
                    value = outstandingText,
                    onValueChange = { text ->
                        if (text.isEmpty() || text.matches(Regex("^\\d*\\.?\\d*$"))) {
                            outstandingText = text
                        }
                    },
                    label = { Text("Outstanding Balance") },
                    placeholder = { Text("0.00") },
                    leadingIcon = {
                        Text(
                            text = CurrencyFormatter.getCurrencySymbol(CurrencyFormatter.getBankBaseCurrency(bankName)),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    supportingText = {
                        Text("Amount currently owed on the card")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                TextField(
                    value = limitText,
                    onValueChange = { text ->
                        if (text.isEmpty() || text.matches(Regex("^\\d*\\.?\\d*$"))) {
                            limitText = text
                        }
                    },
                    label = { Text("Credit Limit") },
                    placeholder = { Text("50000.00") },
                    leadingIcon = {
                        Text(
                            text = CurrencyFormatter.getCurrencySymbol(CurrencyFormatter.getBankBaseCurrency(bankName)),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    supportingText = {
                        Text("Total credit limit of the card")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Show available credit
                val outstanding = outstandingText.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val limit = limitText.toBigDecimalOrNull() ?: BigDecimal.ZERO
                if (limit > BigDecimal.ZERO) {
                    val available = limit - outstanding
                    val utilization = ((outstanding.toDouble() / limit.toDouble()) * 100).toInt()
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Available Credit:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = CurrencyFormatter.formatCurrency(available, CurrencyFormatter.getBankBaseCurrency(bankName)),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Utilization:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "$utilization%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = when {
                                        utilization > 70 -> MaterialTheme.colorScheme.error
                                        utilization > 30 -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val outstanding = outstandingText.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    val limit = limitText.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    onConfirm(outstanding, limit)
                },
                enabled = isValid
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun OrphanedCardItem(
    card: com.pennywiseai.tracker.data.database.entity.CardEntity,
    accounts: List<com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity>,
    onLinkToAccount: (String) -> Unit,
    onDeleteCard: (Long) -> Unit,
    onUpdateCard: (
        bankName: String,
        cardType: com.pennywiseai.tracker.data.database.entity.CardType,
        nickname: String?
    ) -> Unit = { _, _, _ -> }
) {
    var showLinkDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var expandedSource by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expandedSource = !expandedSource },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.Padding.content),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${card.bankName} ••${card.cardLast4}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${if (card.cardType == com.pennywiseai.tracker.data.database.entity.CardType.CREDIT) "Credit" else "Debit"} Card (Unlinked)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Show last known balance if available
                    if (card.lastBalance != null) {
                        Text(
                            text = "Last Balance: ${CurrencyFormatter.formatCurrency(card.lastBalance, card.currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Show source SMS that triggered card detection
                    if (card.lastBalanceSource != null) {
                        Text(
                            text = if (expandedSource) {
                                "SMS: ${card.lastBalanceSource}"
                            } else {
                                "SMS: ${card.lastBalanceSource.take(80)}... (tap to expand)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expandedSource) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    OutlinedButton(
                        onClick = { showLinkDialog = true }
                    ) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Link")
                    }

                    OutlinedButton(
                        onClick = { showEditDialog = true }
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Edit")
                    }

                    OutlinedButton(
                        onClick = { onDeleteCard(card.id) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Delete")
                    }
                }
            }
        }
    }
    
    if (showLinkDialog) {
        LinkCardDialog(
            card = card,
            accounts = accounts.filter { it.bankName == card.bankName },
            onDismiss = { showLinkDialog = false },
            onConfirm = { accountLast4 ->
                onLinkToAccount(accountLast4)
                showLinkDialog = false
            }
        )
    }

    if (showEditDialog) {
        EditCardDialog(
            card = card,
            onDismiss = { showEditDialog = false },
            onConfirm = { bankName, cardType, nickname ->
                onUpdateCard(bankName, cardType, nickname)
                showEditDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCardDialog(
    card: com.pennywiseai.tracker.data.database.entity.CardEntity,
    onDismiss: () -> Unit,
    onConfirm: (
        bankName: String,
        cardType: com.pennywiseai.tracker.data.database.entity.CardType,
        nickname: String?
    ) -> Unit
) {
    var bankName by remember { mutableStateOf(card.bankName) }
    var cardType by remember { mutableStateOf(card.cardType) }
    var nickname by remember { mutableStateOf(card.nickname.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit card") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = "••${card.cardLast4}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("Bank") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = "Card type",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val options = listOf(
                            com.pennywiseai.tracker.data.database.entity.CardType.DEBIT to "Debit",
                            com.pennywiseai.tracker.data.database.entity.CardType.CREDIT to "Credit"
                        )
                        options.forEachIndexed { index, (type, label) ->
                            SegmentedButton(
                                selected = cardType == type,
                                onClick = { cardType = type },
                                shape = SegmentedButtonDefaults.itemShape(index, options.size)
                            ) { Text(label) }
                        }
                    }
                }
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Nickname (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(bankName, cardType, nickname.ifBlank { null })
                },
                enabled = bankName.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkCardDialog(
    card: com.pennywiseai.tracker.data.database.entity.CardEntity,
    accounts: List<com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedAccount by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Link Card to Account")
                Text(
                    text = "${card.bankName} ••${card.cardLast4}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (accounts.isEmpty()) {
                    Text(
                        text = "No ${card.bankName} accounts found. Add an account first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Select an account to link this card to:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    accounts.forEach { account ->
                        Surface(
                            onClick = { selectedAccount = account.accountLast4 },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (selectedAccount == account.accountLast4) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(
                                1.dp,
                                if (selectedAccount == account.accountLast4) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.sm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "••${account.accountLast4}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = CurrencyFormatter.formatCurrency(account.balance, account.currency),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selectedAccount == account.accountLast4) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(Dimensions.Icon.medium)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedAccount?.let(onConfirm) },
                enabled = selectedAccount != null
            ) {
                Text("Link")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteAccountConfirmDialog(
    bankName: String,
    accountLast4: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text("Delete Account?")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = "Are you sure you want to delete this account?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = bankName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Account ending in $accountLast4",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    text = "This will permanently delete all balance history for this account. Any linked cards will be unlinked. This action cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAccountDialog(
    account: com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity,
    onDismiss: () -> Unit,
    onConfirm: (bankName: String, balance: BigDecimal, creditLimit: BigDecimal?) -> Unit
) {
    var bankNameText by remember { mutableStateOf(account.bankName) }
    var balanceText by remember { mutableStateOf(account.balance.toString()) }
    var creditLimitText by remember { mutableStateOf(account.creditLimit?.toString() ?: "") }
    var isValid by remember { mutableStateOf(false) }

    LaunchedEffect(bankNameText, balanceText, creditLimitText) {
        isValid = bankNameText.isNotBlank() &&
                  balanceText.isNotBlank() &&
                  balanceText.toDoubleOrNull() != null &&
                  (if (account.isCreditCard) creditLimitText.isNotBlank() && creditLimitText.toDoubleOrNull() != null else true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Edit Account")
                Text(
                    text = if (account.isCreditCard) "Credit Card" else "Bank Account",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Bank Name (Editable)
                TextField(
                    value = bankNameText,
                    onValueChange = { bankNameText = it },
                    label = { Text("Bank Name") },
                    leadingIcon = {
                        Icon(
                            if (account.isCreditCard) Icons.Default.CreditCard else Icons.Default.AccountBalance,
                            contentDescription = null
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Account Number (Read-only)
                TextField(
                    value = "••${account.accountLast4}",
                    onValueChange = {},
                    label = { Text("Account Number") },
                    enabled = false,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        disabledIndicatorColor = Color.Transparent,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = "Read-only")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (account.isCreditCard) {
                    // Outstanding Balance (Credit Card)
                    TextField(
                        value = balanceText,
                        onValueChange = { text ->
                            if (text.isEmpty() || text.matches(Regex("^\\d*\\.?\\d*$"))) {
                                balanceText = text
                            }
                        },
                        label = { Text("Outstanding Balance") },
                        placeholder = { Text("0.00") },
                        leadingIcon = {
                            Text(
                                text = CurrencyFormatter.getCurrencySymbol(account.currency),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        supportingText = {
                            Text("Amount currently owed on the card")
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Credit Limit
                    TextField(
                        value = creditLimitText,
                        onValueChange = { text ->
                            if (text.isEmpty() || text.matches(Regex("^\\d*\\.?\\d*$"))) {
                                creditLimitText = text
                            }
                        },
                        label = { Text("Credit Limit") },
                        placeholder = { Text("50000.00") },
                        leadingIcon = {
                            Text(
                                text = CurrencyFormatter.getCurrencySymbol(account.currency),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        supportingText = {
                            Text("Total credit limit of the card")
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Show available credit preview
                    val outstanding = balanceText.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    val limit = creditLimitText.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    if (limit > BigDecimal.ZERO) {
                        val available = limit - outstanding
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.sm),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Available Credit:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = CurrencyFormatter.formatCurrency(available, account.currency),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                } else {
                    // Account Balance (Regular Account)
                    TextField(
                        value = balanceText,
                        onValueChange = { text ->
                            if (text.isEmpty() || text.matches(Regex("^\\d*\\.?\\d*$"))) {
                                balanceText = text
                            }
                        },
                        label = { Text("Account Balance") },
                        placeholder = { Text("0.00") },
                        leadingIcon = {
                            Text(
                                text = CurrencyFormatter.getCurrencySymbol(account.currency),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val balance = balanceText.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    val creditLimit = if (account.isCreditCard) {
                        creditLimitText.toBigDecimalOrNull()
                    } else null
                    onConfirm(bankNameText, balance, creditLimit)
                },
                enabled = isValid
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
