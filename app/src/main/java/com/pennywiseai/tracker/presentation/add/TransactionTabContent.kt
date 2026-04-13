package com.pennywiseai.tracker.presentation.add

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.model.displayName
import com.pennywiseai.tracker.domain.model.getAccountType
import com.pennywiseai.tracker.presentation.accounts.AccountType
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.time.format.DateTimeFormatter
import java.util.Locale

// Reusable filled text field colors with no indicator
@Composable
private fun filledFieldColors() = TextFieldDefaults.colors(
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

private val topShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
private val bottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
private val middleShape = RoundedCornerShape(4.dp)
private val fullShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionTabContent(
    viewModel: AddViewModel,
    onSave: () -> Unit
) {
    val uiState by viewModel.transactionUiState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showCurrencyMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.Padding.content, vertical = Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // ── Amount ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Top
            ) {
                ExposedDropdownMenuBox(
                    expanded = showCurrencyMenu,
                    onExpandedChange = { showCurrencyMenu = it },
                    modifier = Modifier.width(130.dp)
                ) {
                    TextField(
                        value = "${CurrencyFormatter.getCurrencySymbol(uiState.currency)} ${uiState.currency}",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCurrencyMenu) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        singleLine = true,
                        shape = fullShape,
                        colors = filledFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = showCurrencyMenu,
                        onDismissRequest = { showCurrencyMenu = false }
                    ) {
                        CurrencyFormatter.getSupportedCurrencies().forEach { currency ->
                            DropdownMenuItem(
                                text = { Text("${CurrencyFormatter.getCurrencySymbol(currency)} $currency") },
                                onClick = {
                                    viewModel.updateTransactionCurrency(currency)
                                    showCurrencyMenu = false
                                }
                            )
                        }
                    }
                }

                TextField(
                    value = uiState.amount,
                    onValueChange = viewModel::updateTransactionAmount,
                    label = { Text("Amount *", fontWeight = FontWeight.SemiBold) },
                    textStyle = MaterialTheme.typography.headlineSmall,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = uiState.amountError != null,
                    supportingText = uiState.amountError?.let { { Text(it) } },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = fullShape,
                    colors = filledFieldColors()
                )
            }

            // ── Transaction Type chips ──
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                TransactionType.values().forEach { type ->
                    FilterChip(
                        selected = uiState.transactionType == type,
                        onClick = { viewModel.updateTransactionType(type) },
                        label = {
                            Text(type.name.lowercase(Locale.getDefault())
                                .replaceFirstChar { it.titlecase(Locale.getDefault()) })
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
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(0.7f),
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

            // ── Date + Time row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                        )
                        .padding(Spacing.sm)
                        .clickable(
                            onClick = { showDatePicker = true },
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.size(Spacing.sm))
                        Column {
                            Text(
                                text = uiState.date.format(DateTimeFormatter.ofPattern("yyyy")),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = uiState.date.format(DateTimeFormatter.ofPattern("dd MMMM")),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Time display
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.sm)
                        .clickable { showTimePicker = true },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        val hour = if (uiState.date.hour % 12 == 0) 12 else uiState.date.hour % 12
                        val minute = uiState.date.minute
                        val amPm = if (uiState.date.hour < 12) "AM" else "PM"

                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            Text(
                                text = String.format("%02d", hour),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(5.dp)
                            )
                        }
                        Text(
                            text = ":",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        )
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            Text(
                                text = String.format("%02d", minute),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(5.dp)
                            )
                        }
                        Box(modifier = Modifier.padding(4.dp)) {
                            Text(
                                text = amPm,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // ── Account + Category (connected cards) ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                // Account card
                val accountInteractionSource = remember { MutableInteractionSource() }
                Card(
                    onClick = { showAccountMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = topShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            when (uiState.selectedAccount?.getAccountType()) {
                                AccountType.CASH -> Icons.Default.Money
                                AccountType.CREDIT -> Icons.Default.CreditCard
                                AccountType.SAVINGS, AccountType.CURRENT -> Icons.Default.AccountBalance
                                null -> Icons.Default.AccountBalance
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.selectedAccount?.bankName ?: "Select Account",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (uiState.selectedAccount != null)
                                    MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (uiState.selectedAccount != null) {
                                Text(
                                    text = "••${uiState.selectedAccount?.accountLast4}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (uiState.selectedAccount != null) {
                            IconButton(
                                onClick = { viewModel.updateSelectedAccount(null) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Category field
                ExposedDropdownMenuBox(
                    expanded = showCategoryMenu,
                    onExpandedChange = { showCategoryMenu = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = uiState.category,
                        onValueChange = {},
                        label = { Text("Category", fontWeight = FontWeight.SemiBold) },
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape = bottomShape,
                        leadingIcon = {
                            Icon(Icons.Default.Category, contentDescription = null)
                        },
                        trailingIcon = {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                        },
                        isError = uiState.categoryError != null,
                        supportingText = uiState.categoryError?.let { { Text(it) } },
                        colors = filledFieldColors()
                    )

                    ExposedDropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    viewModel.updateTransactionCategory(category.name)
                                    showCategoryMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Account selection dropdown menu
            DropdownMenu(
                expanded = showAccountMenu,
                onDismissRequest = { showAccountMenu = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("No account (Manual Entry)")
                            Text(
                                "Won't affect account balance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        viewModel.updateSelectedAccount(null)
                        showAccountMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) }
                )
                HorizontalDivider()
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
                    accountList.forEach { account ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("${account.bankName} ••${account.accountLast4}")
                                    Text(
                                        CurrencyFormatter.formatCurrency(account.balance, account.currency),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            onClick = {
                                viewModel.updateSelectedAccount(account)
                                showAccountMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    when (accountType) {
                                        AccountType.CASH -> Icons.Default.Money
                                        AccountType.CREDIT -> Icons.Default.CreditCard
                                        else -> Icons.Default.AccountBalance
                                    },
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                if (uiState.selectedAccount?.id == account.id) {
                                    Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    }
                }
            }

            // ── Merchant + Notes (connected cards) ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                TextField(
                    value = uiState.merchant,
                    onValueChange = viewModel::updateTransactionMerchant,
                    label = { Text("Merchant", fontWeight = FontWeight.SemiBold) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = topShape,
                    leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                    isError = uiState.merchantError != null,
                    supportingText = uiState.merchantError?.let { { Text(it) } },
                    colors = filledFieldColors()
                )

                TextField(
                    value = uiState.notes,
                    onValueChange = viewModel::updateTransactionNotes,
                    label = { Text("Notes (Optional)", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = bottomShape,
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                    colors = filledFieldColors()
                )
            }

            // ── Receipt ──
            ReceiptPickerSection(
                receiptUri = uiState.receiptUri,
                onReceiptSelected = { uri -> viewModel.updateReceiptUri(uri) },
                onReceiptRemoved = { viewModel.updateReceiptUri(null) },
                onCreateCameraUri = { viewModel.createCameraUri() }
            )

            // Bottom padding for save button overlay
            Spacer(modifier = Modifier.height(72.dp))
        }

        // ── Sticky Save Button ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Button(
                onClick = { viewModel.saveTransaction(onSuccess = onSave) },
                enabled = uiState.isValid && !uiState.isLoading,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = Dimensions.Padding.content)
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimensions.Icon.small),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Done, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Save", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
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
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
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
            title = { Text("Select Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateTransactionTime(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
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
            text = "Receipt (Optional)",
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
                    contentDescription = "Receipt",
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
                    )
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove receipt",
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
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Gallery")
                }
                OutlinedButton(
                    onClick = {
                        val uri = onCreateCameraUri()
                        cameraUri = uri
                        cameraLauncher.launch(uri)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Camera")
                }
            }
        }
    }
}
