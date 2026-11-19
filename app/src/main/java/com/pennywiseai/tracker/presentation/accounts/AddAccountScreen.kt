package com.pennywiseai.tracker.presentation.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.model.AccountType
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.toShape
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    onNavigateBack: () -> Unit,
    viewModel: ManageAccountsViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    var showTypeDropdown by remember { mutableStateOf(false) }
    var showCurrencyDropdown by remember { mutableStateOf(false) }

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
                title = stringResource(R.string.add_account_title),
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accounts_back))
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
                        text = stringResource(R.string.add_account_intro),
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
                            text = error.asString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            val acctColors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    value = formState.accountType.name.lowercase().let { s ->
                        if (s.isEmpty()) s else s.substring(0, 1).uppercase() + s.substring(1)
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.add_account_type_label), fontWeight = FontWeight.SemiBold) },
                    trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                    leadingIcon = {
                        Icon(
                            imageVector = formState.accountType.icon,
                            contentDescription = null
                        )
                    },
                    shape = ListItemPosition.Single.toShape(),
                    colors = acctColors
                )

                ExposedDropdownMenu(
                    expanded = showTypeDropdown,
                    onDismissRequest = { showTypeDropdown = false },
                    shape = MaterialTheme.shapes.large
                ) {
                    AccountType.values().forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Text(type.name.lowercase().let { s ->
                                    if (s.isEmpty()) s else s.substring(0, 1).uppercase() + s.substring(1)
                                })
                            },
                            onClick = {
                                viewModel.updateAccountType(type)
                                showTypeDropdown = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = type.icon,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            }

            // Currency
            ExposedDropdownMenuBox(
                expanded = showCurrencyDropdown,
                onExpandedChange = { showCurrencyDropdown = it }
            ) {
                TextField(
                    value = "${formState.currency}  ${CurrencyFormatter.getCurrencySymbol(formState.currency)}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.add_account_currency_label), fontWeight = FontWeight.SemiBold) },
                    trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) },
                    leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                    shape = ListItemPosition.Single.toShape(),
                    colors = acctColors
                )

                ExposedDropdownMenu(
                    expanded = showCurrencyDropdown,
                    onDismissRequest = { showCurrencyDropdown = false },
                    shape = MaterialTheme.shapes.large
                ) {
                    CurrencyFormatter.getSupportedCurrencies().sorted().forEach { code ->
                        DropdownMenuItem(
                            text = { Text("$code   ${CurrencyFormatter.getCurrencySymbol(code)}") },
                            onClick = {
                                viewModel.updateCurrency(code)
                                showCurrencyDropdown = false
                            }
                        )
                    }
                }
            }

            // Name, last 4 and balance join; a credit limit closes the stack.
            val credit = formState.accountType == AccountType.CREDIT
            val stackSize = if (credit) 4 else 3
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Layout.groupedListGap)
            ) {
                TextField(
                    value = formState.bankName,
                    onValueChange = viewModel::updateBankName,
                    label = { Text(stringResource(R.string.add_account_name_label), fontWeight = FontWeight.SemiBold) },
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    shape = ListItemPosition.from(0, stackSize).toShape(),
                    colors = acctColors
                )

                TextField(
                    value = formState.accountLast4,
                    onValueChange = viewModel::updateAccountLast4,
                    label = {
                        Text(
                            if (formState.accountType == AccountType.CASH) stringResource(R.string.add_account_identifier_label) else stringResource(R.string.add_account_last4_label),
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = ListItemPosition.from(1, stackSize).toShape(),
                    colors = acctColors
                )

                TextField(
                    value = formState.balance,
                    onValueChange = viewModel::updateBalance,
                    label = {
                        Text(
                            // A card's balance is what's owed on it, like a loan's; never the credit left.
                            stringResource(
                                if (formState.accountType == AccountType.LOAN || formState.accountType == AccountType.CREDIT) {
                                    R.string.manage_accounts_outstanding_balance
                                } else {
                                    R.string.add_account_balance_label
                                }
                            ),
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = ListItemPosition.from(2, stackSize).toShape(),
                    colors = acctColors
                )

                // Credit Limit (only for credit cards)
                if (credit) {
                    TextField(
                        value = formState.creditLimit,
                        onValueChange = viewModel::updateCreditLimit,
                        label = { Text(stringResource(R.string.add_account_credit_limit_label), fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.CreditScore, contentDescription = null) },
                        supportingText = { Text(stringResource(R.string.add_account_credit_limit_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = ListItemPosition.from(3, stackSize).toShape(),
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
                    .height(56.dp),
                shapes = ButtonDefaults.shapes()
            ) {
                Icon(Icons.Default.Done, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.accounts_action_save), style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }
}
