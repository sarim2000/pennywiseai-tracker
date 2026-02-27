package com.pennywiseai.tracker.presentation.budgetgroups

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

private val PRESET_COLORS = listOf(
    "#1565C0", "#4CAF50", "#00D09C", "#FC8019", "#E50914",
    "#673AB7", "#FF9800", "#00BCD4", "#795548", "#607D8B"
)

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
                title = if (isEditing) "Edit Group" else "New Group",
                hasBackButton = true,
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
                        onClick = { viewModel.save(forceEmpty = false) },
                        modifier = Modifier.weight(if (isEditing) 1.5f else 1f),
                        enabled = uiState.name.isNotBlank() && !uiState.isSaving,
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
                        Text(if (isEditing) "Save Changes" else "Create Group")
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
                .overScrollVertical()
                .padding(paddingValues),
            contentPadding = PaddingValues(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
        ) {
            // Budget Details Section
            item {
                SectionHeaderV2(title = "Budget Details")
                Spacer(modifier = Modifier.height(Spacing.xs))
                PennyWiseCardV2(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        OutlinedTextField(
                            value = uiState.name,
                            onValueChange = { viewModel.updateName(it) },
                            label = { Text("Group Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
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

            // Type Selector Section
            item {
                SectionHeaderV2(title = "Budget Type")
                Spacer(modifier = Modifier.height(Spacing.xs))
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    BudgetGroupType.entries.forEach { type ->
                        val isSelected = uiState.type == type
                        val typeInfo = getBudgetTypeInfo(type)
                        val containerColor by animateColorAsState(
                            targetValue = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerLow,
                            label = "typeCardColor"
                        )

                        PennyWiseCardV2(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.updateType(type) },
                            colors = CardDefaults.cardColors(containerColor = containerColor),
                            border = if (isSelected) BorderStroke(
                                2.dp,
                                MaterialTheme.colorScheme.primary
                            ) else null
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Icon(
                                    imageVector = typeInfo.icon,
                                    contentDescription = null,
                                    tint = if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(Dimensions.Icon.medium)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = typeInfo.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = typeInfo.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = Dimensions.Alpha.subtitle)
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(Dimensions.Icon.medium)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Color Picker Section
            item {
                SectionHeaderV2(title = "Color")
                Spacer(modifier = Modifier.height(Spacing.xs))
                PennyWiseCardV2(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        items(PRESET_COLORS) { colorHex ->
                            val color = try {
                                Color(android.graphics.Color.parseColor(colorHex))
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.primary
                            }
                            val isSelected = uiState.color == colorHex
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(
                                                3.dp,
                                                MaterialTheme.colorScheme.onSurface,
                                                CircleShape
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { viewModel.updateColor(colorHex) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(Dimensions.Icon.small)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Categories Section
            item {
                val total = uiState.categories.fold(BigDecimal.ZERO) { acc, c -> acc + c.amount }
                SectionHeaderV2(
                    title = "Categories",
                    action = {
                        if (total > BigDecimal.ZERO) {
                            Text(
                                text = "Total: ${CurrencyFormatter.formatCurrency(total, uiState.currency)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
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
                                text = "No categories added yet. This group will track all expenses.",
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
                                groupColor = uiState.color,
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
                                        text = { Text(categoryName) },
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

            // Bottom spacing for navigation bar
            item { Spacer(modifier = Modifier.height(Spacing.md)) }
        }

        // Delete confirmation dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Group") },
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

        // Empty categories warning dialog
        if (uiState.showEmptyCategoriesWarning) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissEmptyWarning() },
                title = { Text("Track All Expenses") },
                text = { Text("No categories selected. This group will track ALL expenses. You can add specific categories later to limit tracking.") },
                confirmButton = {
                    Button(
                        onClick = { viewModel.save(forceEmpty = true) }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissEmptyWarning() }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private data class BudgetTypeInfo(
    val title: String,
    val description: String,
    val icon: ImageVector
)

private fun getBudgetTypeInfo(type: BudgetGroupType): BudgetTypeInfo {
    return when (type) {
        BudgetGroupType.LIMIT -> BudgetTypeInfo(
            title = "Limit",
            description = "Spending cap. Over = bad.",
            icon = Icons.Default.Block
        )
        BudgetGroupType.TARGET -> BudgetTypeInfo(
            title = "Target",
            description = "Savings goal. Over = good.",
            icon = Icons.AutoMirrored.Filled.TrendingUp
        )
        BudgetGroupType.EXPECTED -> BudgetTypeInfo(
            title = "Expected",
            description = "Fixed costs. Shows deviation.",
            icon = Icons.Default.Balance
        )
    }
}

@Composable
private fun CategoryBudgetRow(
    categoryName: String,
    amount: BigDecimal,
    currentSpending: BigDecimal,
    currency: String,
    groupColor: String,
    onAmountChange: (BigDecimal) -> Unit,
    onRemove: () -> Unit
) {
    var amountText by remember(amount) {
        mutableStateOf(if (amount.compareTo(BigDecimal.ZERO) == 0) "" else amount.toPlainString())
    }

    val dotColor = try {
        Color(android.graphics.Color.parseColor(groupColor))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Colored dot
        Box(
            modifier = Modifier
                .size(Spacing.sm)
                .clip(CircleShape)
                .background(dotColor)
        )

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
