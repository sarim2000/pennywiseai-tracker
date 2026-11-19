package com.pennywiseai.tracker.presentation.rules

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.rules.RuleSharingCodec
import com.pennywiseai.tracker.domain.model.rule.ActionType
import com.pennywiseai.tracker.domain.model.rule.TransactionField
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import com.pennywiseai.tracker.domain.usecase.BatchApplyResult
import com.pennywiseai.tracker.domain.usecase.DryRunResult
import com.pennywiseai.tracker.ui.components.CategoryIcon
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.cards.GroupedColumn
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.toShape
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RulesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCreateRule: () -> Unit,
    onNavigateToEditRule: (String) -> Unit,
    onNavigateToDuplicateRule: (String) -> Unit,
    viewModel: RulesViewModel = hiltViewModel()
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val batchApplyProgress by viewModel.batchApplyProgress.collectAsStateWithLifecycle()
    val batchApplyResult by viewModel.batchApplyResult.collectAsStateWithLifecycle()
    val dryRunResult by viewModel.dryRunResult.collectAsStateWithLifecycle()
    val canCreateMoreRules by viewModel.canCreateMoreRules.collectAsStateWithLifecycle()
    val sharingMessage by viewModel.sharingMessage.collectAsStateWithLifecycle()
    var showUpgradeSheet by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Rule sharing (#741). CreateDocument/OpenDocument keep the file in the
    // user's own storage — nothing leaves the device unless they share it.
    val exportRulesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(RuleSharingCodec.MIME_TYPE)
    ) { uri -> uri?.let { viewModel.exportRules(it) } }
    val importRulesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importRules(it) } }

    var showBatchApplyDialog by remember { mutableStateOf(false) }
    var selectedRuleForBatch by remember { mutableStateOf<TransactionRule?>(null) }

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    // Reset dialog state needs to be outside the lambda
    var showResetDialog by remember { mutableStateOf(false) }

    val createRule = {
        if (canCreateMoreRules) onNavigateToCreateRule()
        else showUpgradeSheet = true
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.rules_title),
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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.rules_navigate_back)
                        )
                    }
                },
                actionContent = {
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.rules_more_options)
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            shape = MaterialTheme.shapes.large
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rules_menu_export)) },
                                leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    if (RuleSharingCodec.exportable(rules).isEmpty()) {
                                        viewModel.reportNothingToExport()
                                    } else {
                                        exportRulesLauncher.launch(
                                            "pennywise-rules.${RuleSharingCodec.FILE_EXTENSION}"
                                        )
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rules_menu_import)) },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    // Some file pickers don't offer JSON files under the
                                    // strict MIME type, so accept anything and let the
                                    // decoder reject what isn't a rule set.
                                    importRulesLauncher.launch(arrayOf("*/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rules_menu_reset)) },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showResetDialog = true
                                }
                            )
                        }
                    }
                },
                hazeState = hazeState
            )
        },
        floatingActionButton = {
            // Stays in reach however long the list grows (a starter set alone is a dozen rules).
            ExtendedFloatingActionButton(
                onClick = createRule,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.rules_new_rule)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { paddingValues ->

        sharingMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { viewModel.clearSharingMessage() },
                title = { Text(stringResource(R.string.rules_title)) },
                text = { Text(message.map { it.asString() }.joinToString(" ")) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearSharingMessage() }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.rules_ok))
                    }
                }
            )
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text(stringResource(R.string.rules_reset_title)) },
                text = { Text(stringResource(R.string.rules_reset_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.resetToDefaults()
                            showResetDialog = false
                        },
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text(stringResource(R.string.rules_reset))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.rules_cancel))
                    }
                }
            )
        }
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else {
            val lazyListState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .background(MaterialTheme.colorScheme.background)
                    .overScrollVertical(),
                contentPadding = PaddingValues(
                    start = Dimensions.Padding.content,
                    end = Dimensions.Padding.content,
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + Dimensions.Component.fabScrollClearance
                ),
                state = lazyListState,
                flingBehavior = rememberOverscrollFlingBehavior { lazyListState },
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // What a rule is, in a sentence, where the info card used to be.
                item(key = "intro") {
                    Text(
                        text = stringResource(R.string.rules_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.xs, end = Spacing.xs, bottom = Spacing.sm)
                    )
                }

                // One segmented block, in the order the engine runs them.
                itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                    // Re-read once "Apply to past" finishes, which adds applications.
                    val usageCount by remember(rule.id, batchApplyResult) { viewModel.getRuleApplicationCount(rule.id) }
                        .collectAsStateWithLifecycle(initialValue = null)
                    RuleCard(
                        rule = rule,
                        usageCount = usageCount,
                        position = ListItemPosition.from(index, rules.size),
                        badgeShape = ruleBadgeShapes()[index % RULE_BADGE_SHAPE_COUNT],
                        onToggle = { isActive ->
                            viewModel.toggleRule(rule.id, isActive)
                        },
                        onEdit = {
                            onNavigateToEditRule(rule.id)
                        },
                        onDuplicate = {
                            onNavigateToDuplicateRule(rule.id)
                        },
                        onDelete = {
                            viewModel.deleteRule(rule.id)
                        },
                        onApplyToPast = {
                            selectedRuleForBatch = rule
                            showBatchApplyDialog = true
                        },
                        modifier = Modifier.animateItem()
                    )
                }

                // Help text at the bottom
                item(key = "footer") {
                    Text(
                        text = stringResource(R.string.rules_footer_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)
                    )
                }
            }
        }
    }

    // Batch Apply Dialog
    if (showBatchApplyDialog && selectedRuleForBatch != null) {
        BatchApplyDialog(
            rule = selectedRuleForBatch!!,
            progress = batchApplyProgress,
            result = batchApplyResult,
            dryRunResult = dryRunResult,
            isLoading = isLoading,
            onDismiss = {
                showBatchApplyDialog = false
                selectedRuleForBatch = null
                viewModel.clearBatchApplyResult()
            },
            onPreview = {
                viewModel.previewRule(selectedRuleForBatch!!)
            },
            onApplyToAll = {
                viewModel.applyRuleToPastTransactions(selectedRuleForBatch!!, applyToUncategorizedOnly = false)
            },
            onApplyToUncategorized = {
                viewModel.applyRuleToPastTransactions(selectedRuleForBatch!!, applyToUncategorizedOnly = true)
            }
        )
    }

    if (showUpgradeSheet) {
        com.pennywiseai.tracker.presentation.paywall.UpgradeSheet(
            onDismiss = { showUpgradeSheet = false },
        )
    }
}

// The starter rule is stored in English: RuleTemplateService seeds it under this
// name. Show it in the app's language until the user edits it.
private const val STARTER_RULE_NAME = "Small Payments to Food"
private const val STARTER_RULE_DESCRIPTION = "Categorize small expense payments (under 200) as Food & Dining"

@Composable
private fun TransactionRule.displayName(): String =
    if (isSystemTemplate && name == STARTER_RULE_NAME) stringResource(R.string.rule_template_small_payments_name) else name

@Composable
private fun TransactionRule.displayDescription(): String? =
    if (isSystemTemplate && name == STARTER_RULE_NAME && description == STARTER_RULE_DESCRIPTION)
        stringResource(R.string.rule_template_small_payments_description)
    else description

/** Rule badges cycle through these, so neighbouring rules don't look alike. */
private const val RULE_BADGE_SHAPE_COUNT = 4

@Composable
private fun ruleBadgeShapes(): List<Shape> = listOf(
    MaterialShapes.Cookie9Sided.toShape(),
    MaterialShapes.Clover4Leaf.toShape(),
    MaterialShapes.Sunny.toShape(),
    MaterialShapes.SoftBurst.toShape(),
)

/** What the rule mainly does, as a glyph: its category's own icon when it sets one. */
@Composable
private fun RuleGlyph(rule: TransactionRule, tint: Color) {
    val first = rule.actions.firstOrNull()
    if (first?.actionType == ActionType.SET && first.field == TransactionField.CATEGORY && first.value.isNotBlank()) {
        CategoryIcon(category = first.value, size = Dimensions.Icon.medium, tint = tint)
        return
    }
    val icon: ImageVector = when {
        rule.actions.any { it.actionType == ActionType.BLOCK } -> Icons.Default.Block
        first?.field == TransactionField.MERCHANT -> Icons.Default.Storefront
        first?.field == TransactionField.TAGS -> Icons.AutoMirrored.Filled.Label
        first?.field == TransactionField.NARRATION -> Icons.AutoMirrored.Filled.Notes
        first?.field == TransactionField.TYPE -> Icons.Default.SwapHoriz
        first?.field == TransactionField.TRANSFER_FROM_ACCOUNT ||
            first?.field == TransactionField.TRANSFER_TO_ACCOUNT -> Icons.Default.SyncAlt
        else -> Icons.Default.Rule
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Dimensions.Icon.medium))
}

/**
 * One rule as two readable lines, WHEN it matches and THEN what it does, under
 * its name and an on/off switch. A tap opens its actions (edit, duplicate, apply
 * to past transactions, delete).
 */
@Composable
private fun RuleCard(
    rule: TransactionRule,
    usageCount: Int?,
    position: ListItemPosition,
    badgeShape: Shape,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onApplyToPast: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showActionsMenu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    // An off rule keeps its words readable but loses its colour.
    val accent = if (rule.isActive) colors.primary else colors.onSurfaceVariant
    val clickLabel = stringResource(R.string.rules_more_actions)

    Box(modifier = modifier) {
        GroupedColumn(
            position = position,
            onClick = { showActionsMenu = true },
            modifier = Modifier.semantics {
                onClick(label = clickLabel) {
                    showActionsMenu = true
                    true
                }
            },
            contentPadding = PaddingValues(Dimensions.Padding.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.smd),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(Dimensions.Icon.avatarLarge)
                        .clip(badgeShape)
                        .background(if (rule.isActive) colors.primaryContainer else colors.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    RuleGlyph(rule, tint = if (rule.isActive) colors.onPrimaryContainer else colors.onSurfaceVariant)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                ) {
                    Text(
                        text = rule.displayName(),
                        style = PennyWiseText.rowTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = if (rule.isActive) colors.onSurface else colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = listOfNotNull(
                        usageCount?.let { pluralStringResource(R.plurals.rules_used_times, it, it) },
                        // Only a non-default priority is worth a mention.
                        rule.priority.takeIf { it != 100 }?.let { stringResource(R.string.rules_priority_badge, it) }
                    )
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta.joinToString(" · "),
                            style = PennyWiseText.metadata,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = rule.isActive,
                    onCheckedChange = onToggle
                )
            }

            rule.displayDescription()?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = PennyWiseText.rowSubtitle,
                    color = colors.onSurfaceVariant
                )
            }

            val whenText = ruleConditionsText(rule.conditions)
            val thenText = ruleActionsText(rule.actions)
            if (whenText != null || thenText != null) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    val clauseCount = listOfNotNull(whenText, thenText).size
                    whenText?.let {
                        RuleClause(
                            label = stringResource(R.string.rules_clause_when),
                            text = it.capitalized(),
                            labelContainer = colors.surfaceContainerLowest,
                            labelContent = colors.onSurfaceVariant,
                            position = ListItemPosition.from(0, clauseCount),
                            emphasized = false
                        )
                    }
                    thenText?.let {
                        RuleClause(
                            label = stringResource(R.string.rules_clause_then),
                            text = it.capitalized(),
                            labelContainer = accent,
                            labelContent = if (rule.isActive) colors.onPrimary else colors.surfaceContainerLowest,
                            position = ListItemPosition.from(clauseCount - 1, clauseCount),
                            emphasized = rule.isActive
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showActionsMenu,
            onDismissRequest = { showActionsMenu = false },
            shape = MaterialTheme.shapes.large
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rules_menu_edit)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    showActionsMenu = false
                    onEdit()
                }
            )
            // Opens the editor prefilled as a new rule.
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rules_menu_duplicate)) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                onClick = {
                    showActionsMenu = false
                    onDuplicate()
                }
            )
            // The engine skips an off rule, so applying one to the past would match nothing.
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rules_menu_apply_past)) },
                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                enabled = rule.isActive,
                onClick = {
                    showActionsMenu = false
                    onApplyToPast()
                }
            )
            // Only custom rules can be deleted; templates can be switched off.
            if (!rule.isSystemTemplate) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rules_delete_title)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showActionsMenu = false
                        showDeleteDialog = true
                    }
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.rules_delete_title)) },
            text = { Text(stringResource(R.string.rules_delete_body, rule.displayName())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.rules_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.rules_cancel))
                }
            }
        )
    }
}

/** One clause of a rule: a small WHEN / THEN tag, then the clause in words. */
@Composable
private fun RuleClause(
    label: String,
    text: String,
    labelContainer: Color,
    labelContent: Color,
    position: ListItemPosition,
    emphasized: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = position.toShape(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.smd, vertical = Spacing.smd),
            horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = labelContainer,
                contentColor = labelContent
            ) {
                Text(
                    text = label,
                    style = PennyWiseText.overline,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (emphasized) FontWeight.SemiBold else null,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun String.capitalized(): String = replaceFirstChar { it.titlecase(Locale.getDefault()) }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BatchApplyDialog(
    rule: com.pennywiseai.tracker.domain.model.rule.TransactionRule,
    progress: Pair<Int, Int>?,
    result: BatchApplyResult?,
    dryRunResult: DryRunResult?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onPreview: () -> Unit,
    onApplyToAll: () -> Unit,
    onApplyToUncategorized: () -> Unit
) {
    val title = when {
        progress != null -> stringResource(R.string.rules_batch_applying)
        isLoading && dryRunResult == null -> stringResource(R.string.rules_batch_previewing)
        dryRunResult != null && result == null -> stringResource(R.string.rules_batch_preview_title, rule.displayName())
        else -> stringResource(R.string.rules_batch_title)
    }

    AlertDialog(
        onDismissRequest = {
            if (progress == null && !isLoading) {
                onDismiss()
            }
        },
        title = { Text(text = title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                when {
                    // Loading preview
                    isLoading && dryRunResult == null && progress == null && result == null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(stringResource(R.string.rules_batch_scanning), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Preview result (dry run) — before actual apply
                    dryRunResult != null && result == null && progress == null -> {
                        if (dryRunResult.totalMatched == 0) {
                            Text(
                                text = stringResource(R.string.rules_batch_no_matches, dryRunResult.totalScanned),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                text = pluralStringResource(R.plurals.rules_batch_scanned, dryRunResult.totalScanned, dryRunResult.totalScanned),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.rules_batch_would_update, dryRunResult.totalWouldUpdate),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            if (dryRunResult.totalWouldBlock > 0) {
                                Text(
                                    text = stringResource(R.string.rules_batch_would_block, dryRunResult.totalWouldBlock),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (dryRunResult.samples.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
                                Text(
                                    text = stringResource(R.string.rules_batch_sample_changes),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                dryRunResult.samples.take(5).forEach { diff ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (diff.isBlock)
                                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                            else
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(Spacing.sm)) {
                                            val orig = diff.original
                                            Text(
                                                text = "${orig.merchantName} - ${com.pennywiseai.tracker.utils.CurrencyFormatter.formatCurrency(orig.amount, orig.currency)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (diff.isBlock) {
                                                Text(
                                                    text = stringResource(R.string.rules_batch_sample_blocked),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            } else if (diff.modified != null) {
                                                val mod = diff.modified
                                                if (orig.category != mod.category) {
                                                    Text(
                                                        text = stringResource(R.string.rules_batch_change_category, categoryLabel(orig.category), categoryLabel(mod.category)),
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                                if (orig.merchantName != mod.merchantName) {
                                                    Text(
                                                        text = stringResource(R.string.rules_batch_change_merchant, orig.merchantName, mod.merchantName),
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                                if (orig.transactionType != mod.transactionType) {
                                                    Text(
                                                        text = stringResource(R.string.rules_batch_change_type, orig.transactionType, mod.transactionType),
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                                if (orig.description != mod.description) {
                                                    Text(
                                                        text = stringResource(
                                                            R.string.rules_batch_change_description,
                                                            orig.description ?: stringResource(R.string.rules_batch_none),
                                                            mod.description ?: stringResource(R.string.rules_batch_none)
                                                        ),
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                                if (diff.tagChanges.isNotEmpty()) {
                                                    Text(
                                                        text = stringResource(R.string.rules_batch_change_tags, diff.tagChanges.joinToString(", ")),
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (dryRunResult.totalMatched > 5) {
                                    Text(
                                        text = stringResource(R.string.rules_batch_and_more, dryRunResult.totalMatched - 5),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Initial state — show options with preview button
                    progress == null && result == null -> {
                        Text(
                            text = stringResource(R.string.rules_batch_confirm, rule.displayName()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.rules_batch_preview_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Processing state
                    progress != null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = pluralStringResource(R.plurals.rules_batch_processing, progress.second, progress.first, progress.second),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // Result state
                    result != null -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (result.errors.isEmpty()) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (result.errors.isEmpty())
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = stringResource(R.string.rules_batch_completed),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))

                            Text(
                                text = stringResource(R.string.rules_batch_result_processed, result.totalProcessed),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = stringResource(R.string.rules_batch_result_updated, result.totalUpdated),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            if (result.totalDeleted > 0) {
                                Text(
                                    text = stringResource(R.string.rules_batch_result_blocked, result.totalDeleted),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (result.errors.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.rules_batch_result_errors, result.errors.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                // Loading — no buttons
                isLoading -> {}

                // Preview result — show Apply or Cancel
                dryRunResult != null && result == null && progress == null -> {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.rules_cancel)) }
                        if (dryRunResult.totalMatched > 0) {
                            TextButton(onClick = onApplyToUncategorized, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.rules_batch_apply_uncategorized)) }
                            TextButton(onClick = onApplyToAll, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.rules_batch_apply_all)) }
                        }
                    }
                }

                // Initial state — show Preview + direct apply buttons
                progress == null && result == null -> {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.rules_cancel)) }
                        OutlinedButton(onClick = onPreview, shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Default.Preview, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(stringResource(R.string.rules_batch_preview))
                        }
                    }
                }

                // Done — close button
                result != null -> {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.rules_close)) }
                }
            }
        }
    )
}
