package com.pennywiseai.tracker.presentation.statement

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.SupportDevelopmentDialog
import com.pennywiseai.tracker.ui.components.SupportNudgeCard
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

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

    // Free users get 1 import / calendar month; Pro is unlimited. Centralising
    // the gate here means all three "select PDF" entry points (Idle / Success /
    // Error) share the same enforcement without sprinkling checks.
    val onTryLaunchPicker: () -> Unit = {
        if (canImportThisMonth) pdfPicker.launch("application/pdf")
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
        Column(
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
            when (val state = uiState) {
                is ImportStatementUiState.Idle -> IdleContent(
                    onSelectPdf = onTryLaunchPicker
                )
                is ImportStatementUiState.Loading -> LoadingContent()
                is ImportStatementUiState.Success -> SuccessContent(
                    result = state.result,
                    showSupportNudge = showSupportNudge,
                    onSupportClick = {
                        showSupportDialog = true
                        viewModel.dismissSupportNudge()
                    },
                    onImportAnother = {
                        viewModel.resetState()
                        onTryLaunchPicker()
                    },
                    onDone = onNavigateBack
                )
                is ImportStatementUiState.Error -> ErrorContent(
                    message = state.message,
                    onTryAgain = {
                        viewModel.resetState()
                        onTryLaunchPicker()
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
private fun IdleContent(onSelectPdf: () -> Unit) {
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

@Composable
private fun SuccessContent(
    result: com.pennywiseai.tracker.data.statement.StatementImportResult.Success,
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
