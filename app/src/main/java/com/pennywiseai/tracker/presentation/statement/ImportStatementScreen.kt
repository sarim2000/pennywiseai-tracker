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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
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

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let { viewModel.importStatement(it) }
        }
    )

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
                title = "Import Statement",
                hasBackButton = true,
                hasActionButton = true,
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
                                contentDescription = "Back",
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
                    onSelectPdf = { pdfPicker.launch("application/pdf") }
                )
                is ImportStatementUiState.Loading -> LoadingContent()
                is ImportStatementUiState.Success -> SuccessContent(
                    result = state.result,
                    onImportAnother = {
                        viewModel.resetState()
                        pdfPicker.launch("application/pdf")
                    },
                    onDone = onNavigateBack
                )
                is ImportStatementUiState.Error -> ErrorContent(
                    message = state.message,
                    onTryAgain = {
                        viewModel.resetState()
                        pdfPicker.launch("application/pdf")
                    }
                )
            }
        }
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
        text = "Import Statement",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )

    Text(
        text = "Import transactions from Google Pay or PhonePe PDF statements. Duplicates are automatically detected and skipped.",
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
        Text("Select PDF Statement")
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
        text = "Importing transactions...",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Text(
        text = "Parsing PDF and checking for duplicates",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    )
}

@Composable
private fun SuccessContent(
    result: com.pennywiseai.tracker.data.statement.StatementImportResult.Success,
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
        text = "Import Complete",
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
                label = "Transactions imported",
                value = "${result.imported}",
                isHighlighted = true
            )

            ResultRow(
                label = "Total parsed from PDF",
                value = "${result.totalParsed}"
            )

            if (result.skippedDuplicates > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))

                Text(
                    text = "Duplicates skipped: ${result.skippedDuplicates}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (result.skippedByHash > 0) {
                    ResultRow(
                        label = "Exact re-imports",
                        value = "${result.skippedByHash}",
                        indent = true
                    )
                }
                if (result.skippedByReference > 0) {
                    ResultRow(
                        label = "By UPI reference",
                        value = "${result.skippedByReference}",
                        indent = true
                    )
                }
                if (result.skippedByAmountDate > 0) {
                    ResultRow(
                        label = "By amount & date",
                        value = "${result.skippedByAmountDate}",
                        indent = true
                    )
                }
            }
        }
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
        Text("Import Another")
    }

    OutlinedButton(
        onClick = onDone,
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimensions.Component.buttonHeight),
        shape = RoundedCornerShape(Dimensions.CornerRadius.large)
    ) {
        Text("Done")
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
        text = "Import Failed",
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
        Text("Try Again")
    }
}
