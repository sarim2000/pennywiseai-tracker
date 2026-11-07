package com.pennywiseai.tracker.presentation.categories

import com.pennywiseai.tracker.ui.theme.pennyWiseButtonShapes
import androidx.annotation.StringRes
import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.ui.components.CategoryAvatar
import com.pennywiseai.tracker.ui.components.CategoryIcon
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.cards.toShape
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onNavigateBack: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val showAddEditDialog by viewModel.showAddEditDialog.collectAsStateWithLifecycle()
    val editingCategory by viewModel.editingCategory.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Show snackbar messages
    val snackbarText = snackbarMessage?.asString()
    LaunchedEffect(snackbarMessage) {
        snackbarText?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.clearSnackbarMessage()
            }
        }
    }
    
    // Group categories by type
    val expenseCategories = categories.filter { !it.isIncome }
    val incomeCategories = categories.filter { it.isIncome }

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.categories_title),
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.categories_back))
                    }
                },
                // In the bar: a FAB here sat on top of the rows' visibility toggles.
                actionContent = {
                    IconButton(onClick = { viewModel.showAddDialog() }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.categories_add))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        val lazyListState = rememberLazyListState()
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .overScrollVertical(),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + Dimensions.Component.fabScrollClearance
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
        ) {
            categorySection(
                titleRes = R.string.categories_expense_section,
                categories = expenseCategories,
                isFirstSection = true,
                onEdit = viewModel::showEditDialog,
                onDelete = viewModel::deleteCategory,
                onToggleHidden = { viewModel.toggleCategoryHidden(it.id) }
            )
            categorySection(
                titleRes = R.string.categories_income_section,
                categories = incomeCategories,
                isFirstSection = expenseCategories.isEmpty(),
                onEdit = viewModel::showEditDialog,
                onDelete = viewModel::deleteCategory,
                onToggleHidden = { viewModel.toggleCategoryHidden(it.id) }
            )
        }
    }
    
    // Add/Edit Dialog
    if (showAddEditDialog) {
        CategoryEditDialog(
            category = editingCategory,
            // A built-in keeps its type and stays top-level; only name/colour/icon change.
            lockType = editingCategory?.isSystem == true,
            parentOptions = if (editingCategory?.isSystem == true) emptyList() else categories,
            onDismiss = { viewModel.hideDialog() },
            onSave = { name, color, isIncome, icon, parentId ->
                viewModel.saveCategory(name, color, isIncome, icon, parentId)
            },
            onDelete = editingCategory?.let { cat ->
                {
                    viewModel.deleteCategory(cat)
                    viewModel.hideDialog()
                }
            }
        )
    }
}

/**
 * One heading and one grouped list. Rows stay in the order they arrive so a
 * sub-category sits under its parent, whichever of the two is built-in. That's
 * why built-ins are named once in the heading and only custom rows carry a chip.
 */
private fun LazyListScope.categorySection(
    @StringRes titleRes: Int,
    categories: List<CategoryEntity>,
    isFirstSection: Boolean,
    onEdit: (CategoryEntity) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
    onToggleHidden: (CategoryEntity) -> Unit
) {
    if (categories.isEmpty()) return
    item(key = "header-$titleRes") {
        Column {
            SectionHeaderV2(
                title = stringResource(titleRes),
                topSpacing = if (isFirstSection) Spacing.none else Spacing.sm
            )
            if (categories.any { it.isSystem }) {
                Text(
                    text = stringResource(
                        if (categories.all { it.isSystem }) R.string.categories_all_builtin
                        else R.string.categories_builtin_unless_marked
                    ),
                    style = PennyWiseText.metadata,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    item(key = "list-$titleRes") {
        GroupedList {
            categories.forEachIndexed { index, category ->
                // Keyed by id so a row's swipe state never lands on its neighbour.
                key(category.id) {
                    SwipeableCategoryItem(
                        category = category,
                        position = ListItemPosition.from(index, categories.size),
                        onEdit = { onEdit(category) },
                        onDelete = { onDelete(category) },
                        onToggleHidden = { onToggleHidden(category) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableCategoryItem(
    category: CategoryEntity,
    position: ListItemPosition,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleHidden: () -> Unit
) {
    // Only EndToStart has an anchor, and only for non-system categories, so any dismiss is a delete.
    // Not rememberSaveable: a restored row (e.g. after Undo) must start at rest, or onDismiss re-fires.
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val dismissState = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    // M3 re-runs onDismiss whenever the lambda changes while dismissed, so keep it stable.
    val currentOnDelete by rememberUpdatedState(onDelete)
    val onDismiss = remember { { _: SwipeToDismissBoxValue -> currentOnDelete() } }
    val rowShape = position.toShape()

    SwipeToDismissBox(
        state = dismissState,
        onDismiss = onDismiss,
        backgroundContent = {
            if (!category.isSystem) {
                val color by animateColorAsState(
                    when (dismissState.targetValue) {
                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                        else -> Color.Transparent
                    },
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "background color"
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(rowShape)
                        .background(color)
                        .padding(horizontal = Dimensions.Padding.content),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.categories_action_delete),
                            tint = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }
        },
        content = {
            // See-through rows: the page travels with the swipe.
            Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                CategoryItem(
                    category = category,
                    position = position,
                    onClick = onEdit,
                    onToggleHidden = onToggleHidden
                )
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !category.isSystem
    )
}

@Composable
private fun CategoryItem(
    category: CategoryEntity,
    position: ListItemPosition,
    onClick: () -> Unit,
    onToggleHidden: () -> Unit
) {
    val name = categoryLabel(category.name)
    // Dim only the identity of a hidden category, so it reads as "tucked away"
    // while the control that brings it back keeps full contrast.
    val identityAlpha = if (category.isHidden) Dimensions.Alpha.medium else 1f

    GroupedRow(
        position = position,
        onClick = onClick,
        horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
        // Sub-categories sit indented under their parent (#374). The hide button's
        // 48dp target supplies the end inset.
        contentPadding = PaddingValues(
            start = if (category.parentId != null) Spacing.lg else Spacing.none,
            end = Spacing.none,
            top = Spacing.xs,
            bottom = Spacing.xs
        )
    ) {
        // The same glyph as Transactions; the name beside it already speaks for it.
        Box(
            modifier = Modifier
                .alpha(identityAlpha)
                .clearAndSetSemantics {}
        ) {
            CategoryAvatar(category = category.name)
        }

        FlowRow(
            modifier = Modifier
                .weight(1f)
                .alpha(identityAlpha),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = PennyWiseText.rowTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!category.isSystem) {
                Text(
                    text = stringResource(R.string.categories_custom_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                )
            }
        }

        // Hide / unhide toggle — available for every category, including
        // system defaults (the whole point of #736).
        IconButton(onClick = onToggleHidden, shapes = IconButtonDefaults.shapes()) {
            Icon(
                imageVector = if (category.isHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = stringResource(
                    if (category.isHidden) R.string.categories_show_named else R.string.categories_hide_named,
                    name
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.Icon.inline)
            )
        }
    }
}
