package com.pennywiseai.tracker.presentation.budgetgroups

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pennywiseai.tracker.ui.components.CategoryIcon
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetGroupEditScreen(
    viewModel: BudgetGroupEditViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddCategoryDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.saveComplete) {
        if (uiState.saveComplete) {
            onNavigateBack()
        }
    }

    val isEditing = (uiState.groupId ?: -1L) > 0
    val overallAmount = uiState.overallAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val categoryTotal = uiState.categories.fold(BigDecimal.ZERO) { acc, c -> acc + c.amount }
    val canSave = uiState.name.isNotBlank() && overallAmount > BigDecimal.ZERO && !uiState.isSaving

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = if (isEditing) "Edit Budget" else "New Budget",
                hasBackButton = true,
                hasActionButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                hazeState = hazeState
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = Dimensions.Elevation.bottomBar,
                shadowElevation = Dimensions.Elevation.bottomBar,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.sm)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    if (isEditing) {
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true)
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
                    Button(
                        onClick = { viewModel.save() },
                        modifier = Modifier.weight(if (isEditing) 1.5f else 1f),
                        enabled = canSave,
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(Dimensions.Icon.small),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                        }
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(if (isEditing) "Save Changes" else "Create Budget")
                    }
                }
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val lazyListState = rememberLazyListState()
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
                .overScrollVertical(),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + Spacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
        ) {
            // Decorative Header
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Savings,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            if (isEditing) "Edit your budget" else "Set your budget",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            "Track spending and stay on target",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Budget Details Section
            item {
                PennyWiseCardV2(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        OutlinedTextField(
                            value = uiState.name,
                            onValueChange = { viewModel.updateName(it) },
                            label = { Text("Budget Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                        )

                        OutlinedTextField(
                            value = uiState.overallAmount,
                            onValueChange = { viewModel.updateOverallAmount(it) },
                            label = { Text("Budget Amount") },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            prefix = { Text(CurrencyFormatter.getCurrencySymbol(uiState.currency)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                        )

                        // Currency Selector
                        var showCurrencyMenu by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = showCurrencyMenu,
                            onExpandedChange = { showCurrencyMenu = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = "${CurrencyFormatter.getCurrencySymbol(uiState.currency)} ${uiState.currency}",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Currency") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCurrencyMenu) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                            )
                            ExposedDropdownMenu(
                                expanded = showCurrencyMenu,
                                onDismissRequest = { showCurrencyMenu = false }
                            ) {
                                uiState.availableCurrencies.forEach { currency ->
                                    DropdownMenuItem(
                                        text = { Text("${CurrencyFormatter.getCurrencySymbol(currency)} $currency") },
                                        onClick = {
                                            viewModel.updateCurrency(currency)
                                            showCurrencyMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Categories Section
            item {
                SectionHeaderV2(title = "Category Limits (optional)")
                Spacer(modifier = Modifier.height(Spacing.xs))
                PennyWiseCardV2(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        if (uiState.categories.isEmpty()) {
                            Text(
                                text = "No categories added. This budget will track all expenses.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Spacing.sm)
                            )
                        }

                        uiState.categories.forEach { cat ->
                            CategoryBudgetRow(
                                categoryName = cat.categoryName,
                                amount = cat.amount,
                                currentSpending = cat.currentSpending,
                                currency = uiState.currency,
                                onAmountChange = { viewModel.updateCategoryAmount(cat.categoryName, it) },
                                onRemove = { viewModel.removeCategory(cat.categoryName) }
                            )
                            if (cat != uiState.categories.last()) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(
                                        alpha = Dimensions.Alpha.divider
                                    )
                                )
                            }
                        }

                        // Unallocated / Over-allocated info
                        if (uiState.categories.isNotEmpty() && overallAmount > BigDecimal.ZERO) {
                            val diff = overallAmount - categoryTotal
                            when {
                                diff > BigDecimal.ZERO -> {
                                    Text(
                                        text = "Unallocated: ${CurrencyFormatter.formatCurrency(diff, uiState.currency)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                diff < BigDecimal.ZERO -> {
                                    Text(
                                        text = "Category limits exceed budget by ${CurrencyFormatter.formatCurrency(diff.abs(), uiState.currency)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        // Add Category button
                        Box {
                            FilledTonalButton(
                                onClick = { showAddCategoryDropdown = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = uiState.availableCategories.isNotEmpty(),
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.Icon.small)
                                )
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text("Add Category")
                            }

                            DropdownMenu(
                                expanded = showAddCategoryDropdown,
                                onDismissRequest = { showAddCategoryDropdown = false }
                            ) {
                                uiState.availableCategories.forEach { categoryName ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val catInfo = CategoryMapping.categories[categoryName]
                                                    ?: CategoryMapping.categories["Others"]!!
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(catInfo.color.copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CategoryIcon(category = categoryName, size = 18.dp)
                                                }
                                                Text(categoryName)
                                            }
                                        },
                                        onClick = {
                                            viewModel.addCategory(categoryName)
                                            showAddCategoryDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Delete confirmation dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Budget") },
                text = { Text("Are you sure you want to delete \"${uiState.name}\"? This cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            viewModel.deleteGroup()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun CategoryBudgetRow(
    categoryName: String,
    amount: BigDecimal,
    currentSpending: BigDecimal,
    currency: String,
    onAmountChange: (BigDecimal) -> Unit,
    onRemove: () -> Unit
) {
    var amountText by remember(amount) {
        mutableStateOf(if (amount.compareTo(BigDecimal.ZERO) == 0) "" else amount.toPlainString())
    }

    val categoryInfo = CategoryMapping.categories[categoryName]
        ?: CategoryMapping.categories["Others"]!!

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(categoryInfo.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            CategoryIcon(category = categoryName, size = 22.dp)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.bodyMedium
            )
            if (currentSpending > BigDecimal.ZERO) {
                Text(
                    text = "Spent: ${CurrencyFormatter.formatCurrency(currentSpending, currency)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { value ->
                if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                    amountText = value
                    val parsed = value.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    onAmountChange(parsed)
                }
            },
            prefix = { Text(CurrencyFormatter.getCurrencySymbol(currency)) },
            singleLine = true,
            modifier = Modifier.width(140.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
        )

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(Dimensions.Component.chipHeight)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                modifier = Modifier.size(Dimensions.Icon.small),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
