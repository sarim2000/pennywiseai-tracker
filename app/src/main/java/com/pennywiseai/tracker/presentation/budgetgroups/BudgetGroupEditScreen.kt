package com.pennywiseai.tracker.presentation.budgetgroups

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    var isEditingName by remember { mutableStateOf(false) }
    var isEditingAmount by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.saveComplete) {
        if (uiState.saveComplete) {
            onNavigateBack()
        }
    }

    val isEditing = (uiState.groupId ?: -1L) > 0
    val overallAmount = uiState.overallAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val categoryTotal = uiState.categories.fold(BigDecimal.ZERO) { acc, c -> acc + c.amount }
    val canSave = uiState.name.isNotBlank() && overallAmount > BigDecimal.ZERO && !uiState.isSaving
    val currencySymbol = CurrencyFormatter.getCurrencySymbol(uiState.currency)

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
                actionContent = {
                    if (isEditing) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete budget",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.sm)
                        .navigationBarsPadding()
                ) {
                    Button(
                        onClick = { viewModel.save() },
                        modifier = Modifier.fillMaxWidth(),
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
            // Budget Name + Amount compact row
            item {
                BudgetHeaderCard(
                    name = uiState.name,
                    amount = uiState.overallAmount,
                    currencySymbol = currencySymbol,
                    currency = uiState.currency,
                    isEditingName = isEditingName,
                    isEditingAmount = isEditingAmount,
                    onNameTap = { isEditingName = true },
                    onAmountTap = { isEditingAmount = true },
                    onNameChange = { viewModel.updateName(it) },
                    onAmountChange = { viewModel.updateOverallAmount(it) },
                    onNameDone = { isEditingName = false },
                    onAmountDone = { isEditingAmount = false }
                )
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
private fun BudgetHeaderCard(
    name: String,
    amount: String,
    currencySymbol: String,
    currency: String,
    isEditingName: Boolean,
    isEditingAmount: Boolean,
    onNameTap: () -> Unit,
    onAmountTap: () -> Unit,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onNameDone: () -> Unit,
    onAmountDone: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val nameFocusRequester = remember { FocusRequester() }
    val amountFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditingName) {
        if (isEditingName) nameFocusRequester.requestFocus()
    }
    LaunchedEffect(isEditingAmount) {
        if (isEditingAmount) amountFocusRequester.requestFocus()
    }

    PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Budget name (left side)
            Box(modifier = Modifier.weight(1f)) {
                if (isEditingName) {
                    BasicTextField(
                        value = name,
                        onValueChange = onNameChange,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            onNameDone()
                            focusManager.clearFocus()
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocusRequester),
                        decorationBox = { innerTextField ->
                            Box {
                                if (name.isEmpty()) {
                                    Text(
                                        text = "Budget name",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                } else {
                    Text(
                        text = name.ifEmpty { "Budget name" },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (name.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable { onNameTap() }
                            .padding(vertical = Spacing.xs)
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            // Budget amount (right side)
            if (isEditingAmount) {
                BasicTextField(
                    value = amount,
                    onValueChange = { value ->
                        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                            onAmountChange(value)
                        }
                    },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        onAmountDone()
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier
                        .widthIn(min = 100.dp, max = 160.dp)
                        .focusRequester(amountFocusRequester),
                    decorationBox = { innerTextField ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currencySymbol,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Box {
                                if (amount.isEmpty()) {
                                    Text(
                                        text = "0",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        }
                    }
                )
            } else {
                val displayAmount = amount.toBigDecimalOrNull()?.let {
                    CurrencyFormatter.formatCurrency(it, currency)
                } ?: "${currencySymbol}0"

                Text(
                    text = displayAmount,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (amount.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clickable { onAmountTap() }
                        .padding(vertical = Spacing.xs)
                )
            }
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
