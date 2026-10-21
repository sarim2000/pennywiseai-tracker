package com.pennywiseai.tracker.presentation.statement

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.toShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.tracker.data.statement.StatementBalanceCheck
import com.pennywiseai.tracker.data.statement.StatementColumnMapping
import com.pennywiseai.tracker.data.statement.StatementTableParser
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.SupportDevelopmentDialog
import com.pennywiseai.tracker.ui.components.SupportNudgeCard
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.success
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.format.DateTimeFormatter

private val ROW_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/** MIME types the spreadsheet picker offers (.xls is listed so it can be refused with a hint). */
private val SPREADSHEET_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "text/csv",
    "text/comma-separated-values",
    "application/csv",
    "text/plain",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportStatementScreen(
    onNavigateBack: () -> Unit,
    viewModel: ImportStatementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val canImportThisMonth by viewModel.canImportThisMonth.collectAsStateWithLifecycle()
    val showSupportNudge by viewModel.showSupportNudge.collectAsStateWithLifecycle()
    var showUpgradeSheet by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let { viewModel.importStatement(it) }
        }
    )
    val spreadsheetPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { viewModel.importSpreadsheet(it) } }
    )

    // Free users get 1 import / calendar month; Pro is unlimited. Centralising
    // the gate here means every "select file" entry point shares the same
    // enforcement without sprinkling checks. PDFs and spreadsheets share it.
    val onTryLaunchPicker: () -> Unit = {
        if (canImportThisMonth) pdfPicker.launch("application/pdf")
        else showUpgradeSheet = true
    }
    val onTryLaunchSpreadsheet: () -> Unit = {
        if (canImportThisMonth) spreadsheetPicker.launch(SPREADSHEET_TYPES)
        else showUpgradeSheet = true
    }

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
                title = stringResource(R.string.import_statement_title),
                hasBackButton = true,
                navigationContent = {
                    Box(
                        modifier = Modifier
                            .animateContentSize()
                            .padding(start = 16.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onNavigateBack,
                            ),
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onBackground
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.import_statement_back),
                                modifier = Modifier.size(Dimensions.Icon.small)
                            )
                        }
                    }
                },
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
        val layoutDirection = LocalLayoutDirection.current
        val current = uiState
        // The preview is its own lazy list (it can be thousands of rows long), so
        // it can't live inside the scrolling column the other states share.
        if (current is ImportStatementUiState.Preview) {
            PreviewContent(
                state = current,
                onToggle = viewModel::toggleRow,
                onConfirm = viewModel::confirmImport,
                onEditMapping = viewModel::editMapping,
                onCancel = viewModel::resetState,
                contentPadding = PaddingValues(
                    start = paddingValues.calculateStartPadding(layoutDirection) + Dimensions.Padding.content,
                    top = paddingValues.calculateTopPadding() + Dimensions.Padding.content,
                    end = paddingValues.calculateEndPadding(layoutDirection) + Dimensions.Padding.content,
                    bottom = paddingValues.calculateBottomPadding() + Dimensions.Padding.content
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .background(MaterialTheme.colorScheme.background)
            )
        } else Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background)
                .overScrollVertical()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val state = current) {
                is ImportStatementUiState.Idle -> IdleContent(
                    onSelectPdf = onTryLaunchPicker,
                    onSelectSpreadsheet = onTryLaunchSpreadsheet
                )
                is ImportStatementUiState.Loading -> LoadingContent()
                is ImportStatementUiState.Mapping -> MappingContent(
                    state = state,
                    onMappingChange = viewModel::updateMapping,
                    onHeaderRow = viewModel::selectHeaderRow,
                    onConfirm = viewModel::confirmMapping,
                    onCancel = viewModel::resetState
                )
                is ImportStatementUiState.Preview -> Unit // drawn above
                is ImportStatementUiState.Success -> SuccessContent(
                    result = state.result,
                    balanceCheck = state.balanceCheck,
                    balanceApplied = state.balanceApplied,
                    onApplyBalance = viewModel::applyStatementBalance,
                    showSupportNudge = showSupportNudge,
                    onSupportClick = {
                        showSupportDialog = true
                        viewModel.dismissSupportNudge()
                    },
                    onImportAnother = {
                        viewModel.resetState()
                    },
                    onDone = onNavigateBack
                )
                is ImportStatementUiState.PasswordRequired -> {
                    IdleContent(onSelectPdf = onTryLaunchPicker, onSelectSpreadsheet = onTryLaunchSpreadsheet)
                    PdfPasswordDialog(
                        wrongPassword = state.wrongPassword,
                        onSubmit = { password -> viewModel.importStatement(state.uri, password) },
                        onDismiss = { viewModel.resetState() }
                    )
                }
                is ImportStatementUiState.Error -> ErrorContent(
                    message = state.message,
                    onTryAgain = {
                        viewModel.resetState()
                    }
                )
            }
        }
    }

    if (showUpgradeSheet) {
        com.pennywiseai.tracker.presentation.paywall.UpgradeSheet(
            onDismiss = { showUpgradeSheet = false },
        )
    }

    if (showSupportDialog) {
        SupportDevelopmentDialog(onDismiss = { showSupportDialog = false })
    }
}

@Composable
private fun IdleContent(onSelectPdf: () -> Unit, onSelectSpreadsheet: () -> Unit) {
    Spacer(modifier = Modifier.height(Spacing.xl))

    Icon(
        imageVector = Icons.Default.Description,
        contentDescription = null,
        modifier = Modifier.size(80.dp),
        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    )

    Spacer(modifier = Modifier.height(Spacing.md))

    Text(
        text = stringResource(R.string.import_statement_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )

    Text(
        text = stringResource(R.string.import_statement_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = Spacing.md)
    )

    Spacer(modifier = Modifier.height(Spacing.lg))

    Button(
        onClick = onSelectPdf,
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimensions.Component.buttonHeight),
        shape = RoundedCornerShape(Dimensions.CornerRadius.large)
    ) {
        Icon(
            imageVector = Icons.Default.PictureAsPdf,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.medium)
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(stringResource(R.string.import_statement_select_pdf))
    }

    FilledTonalButton(
        onClick = onSelectSpreadsheet,
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimensions.Component.buttonHeight),
        shape = RoundedCornerShape(Dimensions.CornerRadius.large)
    ) {
        Icon(
            imageVector = Icons.Default.TableChart,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.medium)
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(stringResource(R.string.import_statement_select_spreadsheet))
    }

    Text(
        text = stringResource(R.string.import_statement_xls_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun LoadingContent() {
    Spacer(modifier = Modifier.height(Spacing.xxxl))

    CircularProgressIndicator(
        modifier = Modifier.size(48.dp),
        strokeWidth = 4.dp
    )

    Spacer(modifier = Modifier.height(Spacing.md))

    Text(
        text = stringResource(R.string.import_statement_loading_title),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Text(
        text = stringResource(R.string.import_statement_loading_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ── Column mapping ──────────────────────────────────────────────────────────

@Composable
private fun MappingContent(
    state: ImportStatementUiState.Mapping,
    onMappingChange: (StatementColumnMapping) -> Unit,
    onHeaderRow: (Int) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val mapping = state.mapping
    val header = state.rows.getOrNull(mapping.headerRow).orEmpty()
    val columnCount = state.rows.drop(mapping.headerRow).take(PREVIEW_ROWS + 1).maxOfOrNull { it.size } ?: 0
    val columns = (0 until columnCount).map { i ->
        val letter = columnLetter(i)
        val name = header.getOrNull(i)?.trim().orEmpty()
        i to if (name.isEmpty()) stringResource(R.string.import_statement_column_label, letter) else "$letter · $name"
    }
    val none = StatementColumnMapping.NONE to stringResource(R.string.import_statement_col_none)

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = stringResource(R.string.import_statement_map_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.import_statement_map_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.import_statement_header_row, mapping.headerRow + 1),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onHeaderRow(mapping.headerRow - 1) }, enabled = mapping.headerRow > 0) {
                    Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.import_statement_header_row_up))
                }
                IconButton(
                    onClick = { onHeaderRow(mapping.headerRow + 1) },
                    enabled = mapping.headerRow < state.rows.lastIndex
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.import_statement_header_row_down))
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            TablePreview(
                rows = state.rows.drop(mapping.headerRow).take(PREVIEW_ROWS + 1),
                columnCount = columnCount,
                // Excel day numbers shown as dates, when that's what the column holds.
                serialDateColumn = mapping.dateColumn.takeIf { state.detectedDateFormat == StatementTableParser.EXCEL_SERIAL }
            )
        }

        ColumnPicker(stringResource(R.string.import_statement_col_date), listOf(none) + columns, mapping.dateColumn) {
            onMappingChange(mapping.copy(dateColumn = it))
        }
        val detected = state.detectedDateFormat?.takeIf { mapping.dateFormat == null }
            ?.let { if (it == StatementTableParser.EXCEL_SERIAL) stringResource(R.string.import_statement_date_excel) else it }
        val autoLabel = detected?.let { stringResource(R.string.import_statement_date_auto_found, it) }
            ?: stringResource(R.string.import_statement_date_auto)
        val formats = listOf<Pair<String?, String>>(null to autoLabel) + StatementTableParser.DATE_FORMATS.map { it to it }
        Picker(stringResource(R.string.import_statement_col_date_format), formats, mapping.dateFormat) {
            onMappingChange(mapping.copy(dateFormat = it))
        }
        ColumnPicker(stringResource(R.string.import_statement_col_description), listOf(none) + columns, mapping.descriptionColumn) {
            onMappingChange(mapping.copy(descriptionColumn = it))
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = mapping.usesDebitCredit,
                onClick = {
                    if (!mapping.usesDebitCredit) {
                        onMappingChange(mapping.copy(amountColumn = StatementColumnMapping.NONE, typeColumn = StatementColumnMapping.NONE, debitColumn = mapping.amountColumn.takeIf { it >= 0 } ?: 0))
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) { Text(stringResource(R.string.import_statement_amount_split)) }
            SegmentedButton(
                selected = !mapping.usesDebitCredit,
                onClick = {
                    if (mapping.usesDebitCredit) {
                        onMappingChange(mapping.copy(amountColumn = mapping.debitColumn, debitColumn = StatementColumnMapping.NONE, creditColumn = StatementColumnMapping.NONE))
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(1, 2)
            ) { Text(stringResource(R.string.import_statement_amount_single)) }
        }

        if (mapping.usesDebitCredit) {
            ColumnPicker(stringResource(R.string.import_statement_col_debit), listOf(none) + columns, mapping.debitColumn) {
                onMappingChange(mapping.copy(debitColumn = it))
            }
            ColumnPicker(stringResource(R.string.import_statement_col_credit), listOf(none) + columns, mapping.creditColumn) {
                onMappingChange(mapping.copy(creditColumn = it))
            }
        } else {
            ColumnPicker(stringResource(R.string.import_statement_col_amount), listOf(none) + columns, mapping.amountColumn) {
                onMappingChange(mapping.copy(amountColumn = it))
            }
            ColumnPicker(stringResource(R.string.import_statement_col_type), listOf(none) + columns, mapping.typeColumn) {
                onMappingChange(mapping.copy(typeColumn = it))
            }
        }
        ColumnPicker(stringResource(R.string.import_statement_col_balance), listOf(none) + columns, mapping.balanceColumn) {
            onMappingChange(mapping.copy(balanceColumn = it))
        }
        ColumnPicker(stringResource(R.string.import_statement_col_reference), listOf(none) + columns, mapping.referenceColumn) {
            onMappingChange(mapping.copy(referenceColumn = it))
        }

        val accountOptions = listOf<Pair<Pair<String, String>?, String>>(null to stringResource(R.string.import_statement_account_none)) +
            state.accounts.map { (it.bankName to it.accountLast4) to it.displayLabel }
        val selectedAccount = mapping.accountBankName?.let { bank -> mapping.accountLast4?.let { bank to it } }
        Picker(stringResource(R.string.import_statement_account), accountOptions, selectedAccount) {
            onMappingChange(mapping.copy(accountBankName = it?.first, accountLast4 = it?.second))
        }

        PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.import_statement_sample_title),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(Spacing.xs))
            if (state.parsedCount == 0) {
                Text(
                    text = stringResource(R.string.import_statement_sample_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = stringResource(R.string.import_statement_sample_count, state.parsedCount, state.skippedRows),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.sample.forEach { TransactionLine(it) }
            }
        }

        Button(
            onClick = onConfirm,
            enabled = state.parsedCount > 0,
            modifier = Modifier.fillMaxWidth().height(Dimensions.Component.buttonHeight),
            shape = RoundedCornerShape(Dimensions.CornerRadius.large)
        ) { Text(stringResource(R.string.import_statement_preview_button)) }
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(Dimensions.Component.buttonHeight),
            shape = RoundedCornerShape(Dimensions.CornerRadius.large)
        ) { Text(stringResource(R.string.import_statement_cancel)) }
    }
}

/** The header row and the first data rows, so the user sees what each column holds. */
@Composable
private fun TablePreview(rows: List<List<String>>, columnCount: Int, serialDateColumn: Int?) {
    Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Row {
            (0 until columnCount).forEach { i ->
                Text(
                    text = columnLetter(i),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(Dimensions.Component.tableCellWidth).padding(end = Spacing.sm)
                )
            }
        }
        rows.forEachIndexed { r, row ->
            Row(modifier = Modifier.padding(top = Spacing.xxs)) {
                (0 until columnCount).forEach { i ->
                    val raw = row.getOrNull(i).orEmpty()
                    val serialDate = if (r > 0 && i == serialDateColumn) {
                        StatementTableParser.parseDate(raw, StatementTableParser.EXCEL_SERIAL)
                    } else {
                        null
                    }
                    Text(
                        text = serialDate?.format(ROW_DATE) ?: raw,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (r == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(Dimensions.Component.tableCellWidth).padding(end = Spacing.sm)
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnPicker(label: String, options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) =
    Picker(label, options, selected, onSelect)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Picker(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ── Preview ─────────────────────────────────────────────────────────────────

@Composable
private fun PreviewContent(
    state: ImportStatementUiState.Preview,
    onToggle: (Int) -> Unit,
    onConfirm: () -> Unit,
    onEditMapping: () -> Unit,
    onCancel: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val newIndices = state.plan.newIndices
    val selected = newIndices.count { it !in state.excluded }

    // Lazy: a long statement can bring thousands of new rows; only visible ones are built.
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = stringResource(R.string.import_statement_preview_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                state.accountLabel?.let {
                    Text(
                        text = stringResource(R.string.import_statement_preview_account, it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CountTile(
                        value = newIndices.size,
                        label = stringResource(R.string.import_statement_preview_new),
                        highlight = true,
                        modifier = Modifier.weight(1f)
                    )
                    CountTile(
                        value = state.plan.matchedCount,
                        label = stringResource(R.string.import_statement_preview_matched),
                        highlight = false,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (newIndices.isEmpty()) {
                    Text(
                        text = stringResource(R.string.import_statement_preview_all_matched),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column {
                        Text(
                            text = stringResource(R.string.import_statement_preview_new_rows),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.import_statement_preview_new_rows_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        itemsIndexed(newIndices, key = { _, index -> index }) { i, index ->
            val checked = index !in state.excluded
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (i < newIndices.lastIndex) Spacing.Layout.groupedListGap else Spacing.none)
                    .clip(ListItemPosition.from(i, newIndices.size).toShape())
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .defaultMinSize(minHeight = Dimensions.Component.minTouchTarget)
                    .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(index) })
            ) {
                Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(horizontal = Spacing.smd))
                TransactionLine(state.plan.rows[index].parsed, modifier = Modifier.weight(1f).padding(end = Spacing.smd))
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().height(Dimensions.Component.buttonHeight),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.large)
                ) {
                    Text(
                        if (selected > 0) pluralStringResource(R.plurals.import_statement_confirm, selected, selected)
                        else stringResource(R.string.import_statement_confirm_nothing)
                    )
                }
                if (state.fromSpreadsheet) {
                    TextButton(onClick = onEditMapping, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.import_statement_edit_mapping))
                    }
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().height(Dimensions.Component.buttonHeight),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.large)
                ) { Text(stringResource(R.string.import_statement_cancel)) }
            }
        }
    }
}

@Composable
private fun CountTile(value: Int, label: String, highlight: Boolean, modifier: Modifier = Modifier) {
    PennyWiseCardV2(modifier = modifier) {
        Text(
            text = "$value",
            style = PennyWiseText.amountLarge,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Date, description and currency-tagged amount of one statement row. */
@Composable
private fun TransactionLine(txn: ParsedTransaction, modifier: Modifier = Modifier) {
    val income = txn.type == TransactionType.INCOME
    Row(modifier = modifier.padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = txn.merchant ?: "—",
                style = PennyWiseText.rowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = StatementBalanceCheck.dateOf(txn).format(ROW_DATE),
                style = PennyWiseText.metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = (if (income) "+" else "−") + CurrencyFormatter.formatCurrency(txn.amount, txn.currency),
            style = PennyWiseText.amountRow,
            color = if (income) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Result ──────────────────────────────────────────────────────────────────

@Composable
private fun SuccessContent(
    result: com.pennywiseai.tracker.data.statement.StatementImportResult.Success,
    balanceCheck: StatementBalanceCheck?,
    balanceApplied: Boolean,
    onApplyBalance: () -> Unit,
    showSupportNudge: Boolean = false,
    onSupportClick: () -> Unit = {},
    onImportAnother: () -> Unit,
    onDone: () -> Unit
) {
    Spacer(modifier = Modifier.height(Spacing.lg))

    Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.primary
    )

    Spacer(modifier = Modifier.height(Spacing.md))

    Text(
        text = stringResource(R.string.import_statement_complete),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )

    Spacer(modifier = Modifier.height(Spacing.sm))

    // Results card
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(Dimensions.CornerRadius.large)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            ResultRow(
                label = stringResource(R.string.import_statement_result_imported),
                value = "${result.imported}",
                isHighlighted = true
            )

            if (result.enriched > 0) {
                ResultRow(
                    label = stringResource(R.string.import_statement_result_enriched),
                    value = "${result.enriched}",
                    isHighlighted = true
                )
            }

            ResultRow(
                label = stringResource(R.string.import_statement_result_total_parsed),
                value = "${result.totalParsed}"
            )

            if (result.skippedDuplicates > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))

                Text(
                    text = stringResource(R.string.import_statement_result_duplicates, result.skippedDuplicates),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (result.skippedByHash > 0) {
                    ResultRow(
                        label = stringResource(R.string.import_statement_result_exact),
                        value = "${result.skippedByHash}",
                        indent = true
                    )
                }
                if (result.skippedByReference > 0) {
                    ResultRow(
                        label = stringResource(R.string.import_statement_result_by_reference),
                        value = "${result.skippedByReference}",
                        indent = true
                    )
                }
                if (result.skippedByAmountDate > 0) {
                    ResultRow(
                        label = stringResource(R.string.import_statement_result_by_amount_date),
                        value = "${result.skippedByAmountDate}",
                        indent = true
                    )
                }
            }
        }
    }

    balanceCheck?.let { BalanceCheckCard(it, balanceApplied, onApplyBalance) }

    if (showSupportNudge) {
        Spacer(modifier = Modifier.height(Spacing.md))
        SupportNudgeCard(onClick = onSupportClick)
    }

    Spacer(modifier = Modifier.height(Spacing.lg))

    Button(
        onClick = onImportAnother,
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimensions.Component.buttonHeight),
        shape = RoundedCornerShape(Dimensions.CornerRadius.large)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.medium)
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(stringResource(R.string.import_statement_import_another))
    }

    OutlinedButton(
        onClick = onDone,
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimensions.Component.buttonHeight),
        shape = RoundedCornerShape(Dimensions.CornerRadius.large)
    ) {
        Text(stringResource(R.string.import_statement_done))
    }
}

/** "Statement says X, PennyWise says Y (difference Z)", with a one-tap fix. */
@Composable
private fun BalanceCheckCard(check: StatementBalanceCheck, applied: Boolean, onApply: () -> Unit) {
    fun money(amount: java.math.BigDecimal) = CurrencyFormatter.formatCurrency(amount, check.currency)
    PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.import_statement_balance_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.import_statement_balance_subtitle, check.accountLabel, check.asOf.format(ROW_DATE)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.sm))
        ResultRow(stringResource(R.string.import_statement_balance_statement), money(check.statementBalance))
        ResultRow(
            stringResource(R.string.import_statement_balance_app),
            check.appBalance?.let(::money) ?: stringResource(R.string.import_statement_balance_unknown)
        )
        check.difference?.let { diff ->
            ResultRow(
                label = stringResource(R.string.import_statement_balance_difference),
                value = (if (diff.signum() > 0) "+" else if (diff.signum() < 0) "−" else "") + money(diff.abs()),
                isHighlighted = diff.signum() != 0
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        val corrected = check.correction
        when {
            applied && corrected != null -> Text(
                text = stringResource(R.string.import_statement_balance_applied, money(corrected)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.success
            )
            check.matches -> Text(
                text = stringResource(R.string.import_statement_balance_match),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.success
            )
            // No safe correction (see StatementBalanceCheck.correctionFor): say why.
            corrected == null -> if (check.difference != null) Text(
                text = stringResource(R.string.import_statement_balance_newer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> {
                FilledTonalButton(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.import_statement_balance_set, money(corrected)))
                }
                if (corrected.compareTo(check.statementBalance) != 0) {
                    Text(
                        text = stringResource(R.string.import_statement_balance_set_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
    isHighlighted: Boolean = false,
    indent: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (indent) Modifier.padding(start = Spacing.md) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (isHighlighted) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            color = if (isHighlighted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isHighlighted) FontWeight.Medium else FontWeight.Normal
        )
        Text(
            text = value,
            style = if (isHighlighted) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onTryAgain: () -> Unit
) {
    Spacer(modifier = Modifier.height(Spacing.xxxl))

    Icon(
        imageVector = Icons.Default.Error,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.error
    )

    Spacer(modifier = Modifier.height(Spacing.md))

    Text(
        text = stringResource(R.string.import_statement_failed),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )

    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = Spacing.md)
    )

    Spacer(modifier = Modifier.height(Spacing.lg))

    Button(
        onClick = onTryAgain,
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimensions.Component.buttonHeight),
        shape = RoundedCornerShape(Dimensions.CornerRadius.large)
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.medium)
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(stringResource(R.string.import_statement_try_again))
    }
}

/** Asks for a locked statement's password (#874). Used only to open the file; never stored. */
@Composable
private fun PdfPasswordDialog(
    wrongPassword: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_statement_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.import_statement_password_body))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.import_statement_password_label)) },
                    singleLine = true,
                    isError = wrongPassword,
                    supportingText = if (wrongPassword) {
                        { Text(stringResource(R.string.import_statement_password_wrong)) }
                    } else null,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(password) }, enabled = password.isNotEmpty()) {
                Text(stringResource(R.string.import_statement_password_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.import_statement_password_cancel)) }
        }
    )
}

private const val PREVIEW_ROWS = 5

/** 0 → "A", 26 → "AA". */
private fun columnLetter(index: Int): String {
    var n = index + 1
    val sb = StringBuilder()
    while (n > 0) {
        val rem = (n - 1) % 26
        sb.insert(0, 'A' + rem)
        n = (n - 1) / 26
    }
    return sb.toString()
}
