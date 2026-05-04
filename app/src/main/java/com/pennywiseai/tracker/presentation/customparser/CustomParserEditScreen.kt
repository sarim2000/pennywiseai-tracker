package com.pennywiseai.tracker.presentation.customparser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import com.pennywiseai.tracker.domain.service.CustomParserRuleBuilder
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomParserEditScreen(
    ruleId: Long,
    onNavigateBack: () -> Unit,
    viewModel: CustomParserViewModel = hiltViewModel()
) {
    LaunchedEffect(ruleId) { viewModel.loadForEdit(ruleId) }
    val state by viewModel.editorState.collectAsStateWithLifecycle()
    val pickerCandidates by viewModel.pickerCandidates.collectAsStateWithLifecycle()
    val pickerQuery by viewModel.smsPickerQuery.collectAsStateWithLifecycle()
    val backfillState by viewModel.backfillState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var sheetTokenIndex by remember { mutableStateOf<Int?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    PennyWiseScaffold(
        title = if (ruleId > 0) "Edit Custom Parser" else "New Custom Parser",
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = { Text("Rule name") },
                placeholder = { Text("e.g. My credit union") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.senderPattern,
                onValueChange = viewModel::updateSenderPattern,
                label = { Text("SMS sender (or pattern)") },
                placeholder = { Text("e.g. AD-MYBANK or 8775905546") },
                singleLine = true,
                supportingText = { Text("Substring or regex matched against the sender.") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = state.bankNameDisplay,
                    onValueChange = viewModel::updateBankName,
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.currency,
                    onValueChange = viewModel::updateCurrency,
                    label = { Text("Currency") },
                    singleLine = true,
                    modifier = Modifier.weight(0.6f)
                )
            }

            HorizontalDivider()

            Text(
                text = "Sample SMS",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Pick from your inbox or paste a real SMS. Tap each highlighted token to label it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { showPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Inbox, contentDescription = null)
                Text("  Pick from your SMS")
            }
            OutlinedTextField(
                value = state.sampleSms,
                onValueChange = viewModel::updateSample,
                placeholder = { Text("…or paste a sample SMS here") },
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                minLines = 3,
                maxLines = 6
            )

            if (state.sampleSms.isNotBlank()) {
                TokenGrid(
                    sample = state.sampleSms,
                    tags = state.tags,
                    onTokenTap = { tokenIndex -> sheetTokenIndex = tokenIndex }
                )
                LegendRow()
            }

            HorizontalDivider()

            PreviewPanel(state = state)

            Button(
                onClick = {
                    viewModel.save { onNavigateBack() }
                },
                enabled = state.isValid && backfillState == CustomParserViewModel.BackfillState.Idle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Text("  Save", fontWeight = FontWeight.Medium)
            }
        }

        BackfillDialog(
            state = backfillState,
            onDismiss = {
                viewModel.dismissBackfill()
                onNavigateBack()
            },
            onApply = { viewModel.applyBackfill() }
        )

        if (showPicker) {
            ModalBottomSheet(
                onDismissRequest = {
                    showPicker = false
                    viewModel.updatePickerQuery("")
                },
                sheetState = pickerSheetState
            ) {
                SmsPickerSheet(
                    candidates = pickerCandidates,
                    query = pickerQuery,
                    onQueryChange = viewModel::updatePickerQuery,
                    onPick = { sms ->
                        viewModel.pickSms(sms)
                        scope.launch { pickerSheetState.hide() }.invokeOnCompletion {
                            showPicker = false
                            viewModel.updatePickerQuery("")
                        }
                    }
                )
            }
        }

        val tokenIndex = sheetTokenIndex
        if (tokenIndex != null) {
            val tokens = remember(state.sampleSms) {
                state.sampleSms.split(Regex("""\s+""")).filter { it.isNotEmpty() }
            }
            val token = tokens.getOrNull(tokenIndex) ?: ""
            val currentTag = state.tags.firstOrNull { it.tokenIndex == tokenIndex }?.tag

            ModalBottomSheet(
                onDismissRequest = { sheetTokenIndex = null },
                sheetState = sheetState
            ) {
                TagPickerSheet(
                    token = token,
                    currentTag = currentTag,
                    onPick = { newTag ->
                        viewModel.setTag(tokenIndex, newTag)
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            sheetTokenIndex = null
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TokenGrid(
    sample: String,
    tags: List<CustomParserRuleBuilder.TaggedToken>,
    onTokenTap: (Int) -> Unit
) {
    val tokens = remember(sample) {
        sample.split(Regex("""\s+""")).filter { it.isNotEmpty() }
    }
    val tagByIndex = tags.associateBy { it.tokenIndex }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        tokens.forEachIndexed { index, token ->
            TokenChip(
                text = token,
                tag = tagByIndex[index]?.tag,
                onClick = { onTokenTap(index) }
            )
        }
    }
}

@Composable
private fun TokenChip(
    text: String,
    tag: CustomParserRuleBuilder.TokenTag?,
    onClick: () -> Unit
) {
    val (bg, fg) = tagColors(tag)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        contentColor = fg
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp)
        )
    }
}

@Composable
private fun tagColors(tag: CustomParserRuleBuilder.TokenTag?): Pair<Color, Color> {
    return when (tag) {
        CustomParserRuleBuilder.TokenTag.AMOUNT ->
            MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        CustomParserRuleBuilder.TokenTag.MERCHANT ->
            MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
        CustomParserRuleBuilder.TokenTag.ACCOUNT ->
            MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
        CustomParserRuleBuilder.TokenTag.EXPENSE_KEYWORD ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        CustomParserRuleBuilder.TokenTag.INCOME_KEYWORD ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        null ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegendRow() {
    val items = listOf(
        CustomParserRuleBuilder.TokenTag.AMOUNT to "Amount",
        CustomParserRuleBuilder.TokenTag.MERCHANT to "Merchant",
        CustomParserRuleBuilder.TokenTag.ACCOUNT to "Account",
        CustomParserRuleBuilder.TokenTag.EXPENSE_KEYWORD to "Expense word",
        CustomParserRuleBuilder.TokenTag.INCOME_KEYWORD to "Income word"
    )
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        items.forEach { (tag, label) ->
            val (bg, _) = tagColors(tag)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(bg)
                )
                Text(
                    text = "  $label",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PreviewPanel(state: CustomParserViewModel.EditorState) {
    PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Live preview",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        if (state.sampleSms.isBlank()) {
            Text(
                text = "Paste a sample SMS and tag tokens to see what gets extracted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs)
            )
            return@PennyWiseCardV2
        }
        Column(modifier = Modifier.padding(top = Spacing.xs)) {
            PreviewRow("Amount", state.preview.amount?.toPlainString() ?: "—")
            PreviewRow("Type", state.preview.type?.toDisplay() ?: "—")
            PreviewRow("Merchant", state.preview.merchant ?: "—")
            PreviewRow("Account", state.preview.accountLast4 ?: "—")
        }
    }
}

private fun TransactionType.toDisplay(): String = when (this) {
    TransactionType.INCOME -> "Income"
    TransactionType.EXPENSE -> "Expense"
    TransactionType.TRANSFER -> "Transfer"
    TransactionType.INVESTMENT -> "Investment"
    TransactionType.CREDIT -> "Credit (card)"
    TransactionType.BALANCE_UPDATE -> "Balance update"
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun TagPickerSheet(
    token: String,
    currentTag: CustomParserRuleBuilder.TokenTag?,
    onPick: (CustomParserRuleBuilder.TokenTag?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = "What is \"$token\"?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "PennyWise will use the surrounding tokens as anchors to find this in future SMS.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.sm)
        )

        TagOption("Amount", "The transaction amount", CustomParserRuleBuilder.TokenTag.AMOUNT, currentTag, onPick)
        TagOption("Merchant", "Who got paid / who paid you. Tap each word if the name spans multiple tokens.", CustomParserRuleBuilder.TokenTag.MERCHANT, currentTag, onPick)
        TagOption("Account number", "Last 4 digits of account / card", CustomParserRuleBuilder.TokenTag.ACCOUNT, currentTag, onPick)
        TagOption("Expense keyword", "Word that means money went out (e.g. \"debited\")", CustomParserRuleBuilder.TokenTag.EXPENSE_KEYWORD, currentTag, onPick)
        TagOption("Income keyword", "Word that means money came in (e.g. \"credited\")", CustomParserRuleBuilder.TokenTag.INCOME_KEYWORD, currentTag, onPick)

        TextButton(
            onClick = { onPick(null) },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm)
        ) {
            Text(if (currentTag == null) "Skip" else "Clear tag")
        }
    }
}

@Composable
private fun TagOption(
    title: String,
    description: String,
    tag: CustomParserRuleBuilder.TokenTag,
    currentTag: CustomParserRuleBuilder.TokenTag?,
    onPick: (CustomParserRuleBuilder.TokenTag) -> Unit
) {
    val selected = currentTag == tag
    val (bg, fg) = tagColors(tag)
    Surface(
        onClick = { onPick(tag) },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) bg else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) fg else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(bg)
            )
            Column(modifier = Modifier.padding(start = Spacing.sm).weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) fg else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) Icon(Icons.Default.Check, contentDescription = null)
        }
    }
}

@Composable
private fun BackfillDialog(
    state: CustomParserViewModel.BackfillState,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    if (state == CustomParserViewModel.BackfillState.Idle) return

    val title = when (state) {
        CustomParserViewModel.BackfillState.DryRunning -> "Scanning past SMS…"
        is CustomParserViewModel.BackfillState.Preview -> "Apply to past SMS?"
        is CustomParserViewModel.BackfillState.Applying -> "Applying…"
        is CustomParserViewModel.BackfillState.Done -> "Done"
        CustomParserViewModel.BackfillState.Idle -> ""
    }

    AlertDialog(
        onDismissRequest = {
            // Don't let user dismiss while we're working.
            if (state is CustomParserViewModel.BackfillState.Preview ||
                state is CustomParserViewModel.BackfillState.Done) {
                onDismiss()
            }
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                when (state) {
                    CustomParserViewModel.BackfillState.DryRunning -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            "Checking which past SMS this rule would parse.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is CustomParserViewModel.BackfillState.Preview -> {
                        Text(
                            text = "Scanned ${state.dryRun.totalScanned} unrecognised SMS:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${state.dryRun.totalMatched} would be parsed as transactions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        if (state.dryRun.samples.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
                            Text(
                                text = "Sample:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                            state.dryRun.samples.forEach { sample ->
                                BackfillSampleRow(sample)
                            }
                            if (state.dryRun.totalMatched > state.dryRun.samples.size) {
                                Text(
                                    text = "…and ${state.dryRun.totalMatched - state.dryRun.samples.size} more.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    is CustomParserViewModel.BackfillState.Applying -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            "Saving ${state.processed} of ${state.dryRun.totalMatched}…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    is CustomParserViewModel.BackfillState.Done -> {
                        Text(
                            text = "Parsed ${state.saved} past SMS as transactions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "You can review them in the Transactions screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    CustomParserViewModel.BackfillState.Idle -> {}
                }
            }
        },
        confirmButton = {
            when (state) {
                is CustomParserViewModel.BackfillState.Preview -> {
                    TextButton(onClick = onApply) { Text("Apply") }
                }
                is CustomParserViewModel.BackfillState.Done -> {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                else -> {}
            }
        },
        dismissButton = {
            if (state is CustomParserViewModel.BackfillState.Preview) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text("Skip") }
            }
        }
    )
}

@Composable
private fun BackfillSampleRow(sample: com.pennywiseai.tracker.domain.service.CustomParserService.DryRunMatch) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp)) {
            Text(
                text = sample.sms.sender,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            val parsed = sample.parsed
            Text(
                text = "${parsed.type.name.lowercase().replaceFirstChar { it.uppercase() }} · ${parsed.currency} ${parsed.amount.toPlainString()}" +
                    (parsed.merchant?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun SmsPickerSheet(
    candidates: List<UnrecognizedSmsEntity>,
    query: String,
    onQueryChange: (String) -> Unit,
    onPick: (UnrecognizedSmsEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .padding(bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = "Pick a sample SMS",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "These are SMS that no built-in parser recognised. Pick one to seed your rule.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search sender or body") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (candidates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .padding(vertical = Spacing.lg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (query.isBlank())
                        "No unrecognised SMS yet. Once an SMS arrives that no built-in parser handles, it'll show up here."
                    else
                        "No SMS match \"$query\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                items(candidates, key = { it.id }) { sms ->
                    SmsPickerRow(sms = sms, onClick = { onPick(sms) })
                }
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
    }
}

@Composable
private fun SmsPickerRow(sms: UnrecognizedSmsEntity, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            Text(
                text = sms.sender,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = sms.smsBody,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )
        }
    }
}
