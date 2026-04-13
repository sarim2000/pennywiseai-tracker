package com.pennywiseai.tracker.presentation.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    onNavigateBack: () -> Unit,
    viewModel: ManageAccountsViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsState()
    var showTypeDropdown by remember { mutableStateOf(false) }

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
                title = "Add Account",
                hasBackButton = true,
                hasActionButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(Dimensions.Padding.content)
                .imePadding()
                .overScrollVertical()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Info Card
            PennyWiseCardV2(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                contentPadding = 12.dp
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(Dimensions.Icon.medium)
                    )
                    Text(
                        text = "Add accounts not tracked via SMS like cash, wallets, credit cards, or investment accounts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // Error Message
            formState.errorMessage?.let { error ->
                PennyWiseCardV2(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    contentPadding = 12.dp
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(Dimensions.Icon.medium)
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            
            val acctFullShape = RoundedCornerShape(16.dp)
            val acctTopShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
            val acctMiddleShape = RoundedCornerShape(4.dp)
            val acctBottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
            val acctColors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                disabledIndicatorColor = Color.Transparent,
                disabledLabelColor = MaterialTheme.colorScheme.primary,
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Account Type
            ExposedDropdownMenuBox(
                expanded = showTypeDropdown,
                onExpandedChange = { showTypeDropdown = it }
            ) {
                TextField(
                    value = formState.accountType.name.lowercase()
                        .replaceFirstChar { it.uppercase() },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Account Type", fontWeight = FontWeight.SemiBold) },
                    trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                    leadingIcon = {
                        Icon(
                            imageVector = when (formState.accountType) {
                                AccountType.SAVINGS, AccountType.CURRENT -> Icons.Default.AccountBalance
                                AccountType.CREDIT -> Icons.Default.CreditCard
                                AccountType.CASH -> Icons.Default.Money
                            },
                            contentDescription = null
                        )
                    },
                    shape = acctFullShape,
                    colors = acctColors
                )

                ExposedDropdownMenu(
                    expanded = showTypeDropdown,
                    onDismissRequest = { showTypeDropdown = false }
                ) {
                    AccountType.values().forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Text(type.name.lowercase().replaceFirstChar { it.uppercase() })
                            },
                            onClick = {
                                viewModel.updateAccountType(type)
                                showTypeDropdown = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (type) {
                                        AccountType.SAVINGS, AccountType.CURRENT -> Icons.Default.AccountBalance
                                        AccountType.CREDIT -> Icons.Default.CreditCard
                                        AccountType.CASH -> Icons.Default.Money
                                    },
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            }

            // Account Name + Last 4 + Balance (connected group)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                TextField(
                    value = formState.bankName,
                    onValueChange = viewModel::updateBankName,
                    label = { Text("Account Name *", fontWeight = FontWeight.SemiBold) },
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    shape = acctTopShape,
                    colors = acctColors
                )

                TextField(
                    value = formState.accountLast4,
                    onValueChange = viewModel::updateAccountLast4,
                    label = {
                        Text(
                            if (formState.accountType == AccountType.CASH) "Identifier (Optional)" else "Last 4 Digits *",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = if (formState.accountType == AccountType.CREDIT) acctMiddleShape else acctBottomShape,
                    colors = acctColors
                )

                TextField(
                    value = formState.balance,
                    onValueChange = viewModel::updateBalance,
                    label = { Text("Current Balance *", fontWeight = FontWeight.SemiBold) },
                    leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = if (formState.accountType == AccountType.CREDIT) acctMiddleShape else acctFullShape,
                    colors = acctColors
                )

                // Credit Limit (only for credit cards)
                if (formState.accountType == AccountType.CREDIT) {
                    TextField(
                        value = formState.creditLimit,
                        onValueChange = viewModel::updateCreditLimit,
                        label = { Text("Credit Limit", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.CreditScore, contentDescription = null) },
                        supportingText = { Text("Optional: Set credit limit for utilization tracking") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = acctBottomShape,
                        colors = acctColors
                    )
                }
            }

            // Save Button
            Button(
                onClick = {
                    viewModel.addAccount()
                    if (formState.errorMessage == null) {
                        onNavigateBack()
                    }
                },
                enabled = formState.isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Default.Done, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Save", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }
}