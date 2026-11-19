package com.pennywiseai.tracker.presentation.add

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.model.VoiceEntry
import com.pennywiseai.tracker.ui.components.AmountCalculatorSheet
import com.pennywiseai.tracker.ui.components.CategoryChip
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.QuickCategoryPickerSheet
import com.pennywiseai.tracker.ui.components.RoundedField
import com.pennywiseai.tracker.ui.components.cards.GroupedColumn
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.money
import com.pennywiseai.tracker.ui.voice.VoiceInput
import com.pennywiseai.tracker.ui.voice.VoiceMicButton
import com.pennywiseai.tracker.ui.voice.VoiceStatusBar
import com.pennywiseai.tracker.ui.voice.rememberVoiceInput
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The quick add: an expense or income in a few taps. Amount (and its currency), a
 * category, an optional "where", and a one-line account/date summary that opens on
 * demand. The in-app "+" shows it over the tabs; [QuickAddActivity] shows it for the
 * widgets and the evening recap.
 *
 * Save waits for the row: only a confirmed insert closes the sheet ([onSaved]); a
 * refused or failed one keeps the draft open with the reason. Transfer, Subscription
 * and More details hand the draft to the full form instead.
 *
 * [listenFirst] (a mic opened it) starts listening: one spend or income said fills the
 * sheet; a question goes to [onAsk].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    listenFirst: Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onMoreDetails: (QuickAddDraft) -> Unit,
    onAsk: (String) -> Unit,
    onSubscription: ((QuickAddDraft) -> Unit)? = null,
    viewModel: QuickAddViewModel = hiltViewModel()
) {
    var setup by remember { mutableStateOf<QuickAddViewModel.Setup?>(null) }
    LaunchedEffect(Unit) { setup = viewModel.loadSetup() }
    val s = setup ?: return
    // Saveable: a rotation after the words were read shows the sheet, not the mic again.
    var listening by rememberSaveable { mutableStateOf(listenFirst) }
    // Only read as the sheet first fills; its own saveable fields carry it from there.
    var heard by remember { mutableStateOf<VoiceEntry?>(null) }
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        if (listening) {
            VoicePane(
                setup = s,
                onEntry = {
                    heard = it
                    listening = false
                },
                onAsk = onAsk,
                onType = { listening = false }
            )
        } else {
            QuickAddForm(
                setup = s,
                heard = heard,
                viewModel = viewModel,
                onListen = { listening = true },
                onSaved = { scope.launch { sheetState.hide() }.invokeOnCompletion { onSaved() } },
                onMoreDetails = onMoreDetails,
                onSubscription = onSubscription
            )
        }
    }
}

/** Listening: the words as they're heard, then routed once when the user stops talking. */
@Composable
private fun VoicePane(
    setup: QuickAddViewModel.Setup,
    onEntry: (VoiceEntry) -> Unit,
    onAsk: (String) -> Unit,
    onType: () -> Unit
) {
    val context = LocalContext.current
    var transcript by rememberSaveable { mutableStateOf("") }
    var unclear by rememberSaveable { mutableStateOf(false) }
    // A question, with the Chat tab hidden: not the user's mistake, so a different line.
    var chatHidden by rememberSaveable { mutableStateOf(false) }
    // Starts by itself once; a rotation doesn't start it again.
    var started by rememberSaveable { mutableStateOf(false) }
    // Words from a recording still going: only these are routed after a rotation, not
    // ones already routed or left by a recording that failed.
    var midRecording by rememberSaveable { mutableStateOf(false) }
    val categories = setup.expenseCategories + setup.incomeCategories
    fun route(text: String) {
        midRecording = false
        when (
            val route = QuickAddActivity.voiceRoute(text, categories, setup.baseCurrency, setup.chatOpen, setup.modelReady) {
                CategoryMapping.label(context, it)
            }
        ) {
            is QuickAddActivity.VoiceRoute.Fill -> onEntry(route.entry)
            QuickAddActivity.VoiceRoute.Chat -> onAsk(text)
            QuickAddActivity.VoiceRoute.Unclear -> unclear = true
            QuickAddActivity.VoiceRoute.ChatHidden -> chatHidden = true
        }
    }
    val voice = rememberVoiceInput(
        onText = {
            transcript = it
            unclear = false
            chatHidden = false
            midRecording = true
        },
        onDone = ::route
    )
    LaunchedEffect(voice) {
        snapshotFlow { voice.note }.collect { if (it?.isError == true) midRecording = false }
    }
    LaunchedEffect(Unit) {
        // A widget drawn before the phone lost its recognizer: type instead.
        if (!voice.available) return@LaunchedEffect onType()
        // A rotation stopped the mic mid-sentence: use the words it kept, as if the user stopped.
        if (started && midRecording && transcript.isNotEmpty()) return@LaunchedEffect route(transcript)
        if (!started) {
            // Until the recognizer says which languages it has, Arabic on Google's (online
            // only) would start on-device and fail, or skip the consent question.
            val ready = withTimeoutOrNull(LANGUAGES_WAIT_MS) { snapshotFlow { voice.ready }.first { it } } != null
            started = true
            // Still unanswered: leave the mic for the user to tap rather than guess the recognizer.
            if (ready && !voice.active) voice.toggle("")
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.dialog)
            .padding(bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.quick_add_voice_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    voice.cancel()
                    onType()
                },
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.quick_add_voice_type))
            }
        }
        Text(
            text = transcript.ifEmpty { stringResource(R.string.quick_add_voice_hint) },
            // Arabic said reads right to left.
            style = (if (transcript.isEmpty()) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineSmall)
                .copy(textDirection = TextDirection.Content),
            color = if (transcript.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )
        if (unclear || chatHidden) {
            Text(
                text = stringResource(if (chatHidden) R.string.quick_add_voice_chat_hidden else R.string.quick_add_voice_unclear),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            VoiceStatusBar(voice, Modifier.weight(1f))
            VoiceMicButton(
                voice = voice,
                enabled = true,
                onTap = {
                    unclear = false
                    chatHidden = false
                    // A new recording: the last one's words are done with.
                    if (!voice.active) midRecording = false
                    voice.toggle("")
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddForm(
    setup: QuickAddViewModel.Setup,
    heard: VoiceEntry?,
    viewModel: QuickAddViewModel,
    onListen: () -> Unit,
    onSaved: () -> Unit,
    onMoreDetails: (QuickAddDraft) -> Unit,
    onSubscription: ((QuickAddDraft) -> Unit)?
) {
    val context = LocalContext.current
    // Saveable: a rotation, the dark theme or a font size change recreates the sheet.
    var amountText by rememberSaveable { mutableStateOf(heard?.amount?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var where by rememberSaveable { mutableStateOf(heard?.merchant.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(heard?.category) }
    var income by rememberSaveable { mutableStateOf(heard?.type == TransactionType.INCOME) }
    var currency by rememberSaveable { mutableStateOf(heard?.currency ?: setup.baseCurrency) }
    // A day said out loud ("yesterday") or picked; null is now.
    var day by rememberSaveable { mutableStateOf(heard?.date?.takeIf { it != LocalDate.now() }) }
    // Cash only, in the entry's currency: what no SMS reports, and a balance moves by the raw amount.
    val cashAccounts = remember(currency) { QuickAddActivity.cashAccounts(setup.balances, currency, setup.profileId) }
    var accountKey by rememberSaveable { mutableStateOf(cashAccounts.firstOrNull()?.let(QuickAddActivity::keyOf)) }
    val account = cashAccounts.firstOrNull { QuickAddActivity.keyOf(it) == accountKey }
    // The category the words (or the full picker) chose leads the row, so the pick is in view.
    var pinned by rememberSaveable { mutableStateOf(heard?.category) }
    val ofType = if (income) setup.incomeCategories else setup.expenseCategories
    val categories = remember(income, pinned) { ofType.sortedBy { it.name != pinned } }
    val type = if (income) TransactionType.INCOME else TransactionType.EXPENSE
    var showCalculator by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var summaryOpen by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    val gate = remember { SaveGate() }
    val scope = rememberCoroutineScope()
    val amount = amountText.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val decimalComma = remember {
        DecimalFormatSymbols.getInstance(context.resources.configuration.locales[0]).decimalSeparator == ','
    }
    // Filled by voice: what's left is a tap on a category, not typing.
    LaunchedEffect(Unit) { if (heard == null || amountText.isEmpty()) focus.requestFocus() }

    fun draft(t: TransactionType = type) = QuickAddDraft(amountText, where, category, t, currency, account, day)

    fun save() {
        val a = amount ?: return
        val c = category ?: return
        val d = draft()
        scope.launch {
            saving = true
            error = null
            when (val outcome = gate.save { viewModel.insert(d, a, c) }) {
                SaveOutcome.Saved -> {
                    viewModel.afterSave()
                    Toast.makeText(
                        context,
                        context.getString(R.string.quick_add_saved, CurrencyFormatter.formatCurrency(a, d.currency), CategoryMapping.label(context, c)),
                        Toast.LENGTH_SHORT
                    ).show()
                    onSaved()
                }
                SaveOutcome.Busy -> Unit
                SaveOutcome.Refused -> {
                    error = R.string.add_error_duplicate
                    saving = false
                }
                is SaveOutcome.Failed -> {
                    Log.e("QuickAddSheet", "Quick add failed", outcome.error)
                    error = R.string.add_error_save_transaction
                    saving = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.dialog)
            .padding(bottom = Spacing.lg)
            .imePadding()
            // Short phones and landscape: scroll rather than run off the sheet.
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.add_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            // Only where the phone has a recognizer; otherwise the mic would bounce straight back.
            if (remember { VoiceInput.isAvailable(context) }) {
                IconButton(onClick = onListen, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Mic, contentDescription = stringResource(R.string.quick_add_voice_title))
                }
            }
        }
        ConnectedChoiceRow(
            options = listOf(false, true),
            selected = income,
            onSelect = { isIncome ->
                if (income != isIncome) {
                    income = isIncome
                    // Categories are per type.
                    category = null
                }
            },
            label = { stringResource(if (it) R.string.txn_type_income else R.string.txn_type_expense) }
        )
        OutlinedTextField(
            value = amountText,
            onValueChange = { typed -> QuickAddActivity.normalizeAmount(typed, decimalComma)?.let { amountText = it } },
            label = { Text(stringResource(R.string.add_field_amount)) },
            leadingIcon = {
                CurrencyButton(currency, onPick = { picked ->
                    if (picked != currency) {
                        currency = picked
                        // The cash accounts are per currency: start from the new one's last used.
                        accountKey = QuickAddActivity.cashAccounts(setup.balances, picked, setup.profileId)
                            .firstOrNull()?.let(QuickAddActivity::keyOf)
                    }
                })
            },
            textStyle = MaterialTheme.typography.headlineSmall.money(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            trailingIcon = {
                IconButton(
                    onClick = {
                        // The sheet has its own keypad; the keyboard would cover it.
                        focusManager.clearFocus()
                        showCalculator = true
                    },
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = stringResource(R.string.calculator_open))
                }
            },
            shape = RoundedField.shape,
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )
        OutlinedTextField(
            value = where,
            onValueChange = { where = it },
            placeholder = { Text(stringResource(R.string.quick_add_where_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            shape = RoundedField.shape,
            modifier = Modifier.fillMaxWidth()
        )
        // One scrolling row, most-used first, after the full searchable list: the usual
        // pick is in view and Save stays above the keyboard.
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            AssistChip(
                onClick = {
                    focusManager.clearFocus()
                    showPicker = true
                },
                label = { Text(stringResource(R.string.quick_add_all_categories)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.List, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) }
            )
            categories.forEach { c ->
                FilterChip(
                    selected = category == c.name,
                    shapes = FilterChipDefaults.shapes(),
                    colors = FilterChipDefaults.filterChipColors(),
                    onClick = {
                        category = c.name
                        // Typing is done once the category is picked; on a short phone the keyboard hides Save.
                        focusManager.clearFocus()
                    },
                    label = { Text(categoryLabel(c.name)) },
                    leadingIcon = { CategoryChip(category = c, showText = false) }
                )
            }
        }
        // Account and date: a summary line; the choices open under it on demand.
        val dayText = when (day) {
            null -> stringResource(R.string.quick_add_today)
            LocalDate.now().minusDays(1) -> stringResource(R.string.quick_add_yesterday)
            else -> day!!.format(AppDates.dateFormatter())
        }
        val noAccount = stringResource(R.string.add_txn_no_account)
        val summary = if (cashAccounts.isEmpty()) dayText else stringResource(R.string.quick_add_summary, account?.displayLabel ?: noAccount, dayText)
        GroupedList {
            GroupedRow(
                position = if (summaryOpen) ListItemPosition.Top else ListItemPosition.Single,
                onClick = { summaryOpen = !summaryOpen },
                modifier = Modifier.semantics {
                    if (summaryOpen) collapse { summaryOpen = false; true } else expand { summaryOpen = true; true }
                }
            ) {
                Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                RowLabels(title = summary)
                Icon(
                    if (summaryOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (summaryOpen) {
                GroupedColumn(position = ListItemPosition.Bottom) {
                    if (cashAccounts.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            (listOf(null) + cashAccounts).forEach { a ->
                                FilterChip(
                                    selected = account?.let(QuickAddActivity::keyOf) == a?.let(QuickAddActivity::keyOf),
                                    shapes = FilterChipDefaults.shapes(),
                                    colors = FilterChipDefaults.filterChipColors(),
                                    onClick = { accountKey = a?.let(QuickAddActivity::keyOf) },
                                    label = { Text(a?.displayLabel ?: noAccount) }
                                )
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        val yesterday = LocalDate.now().minusDays(1)
                        FilterChip(
                            selected = day == null,
                            shapes = FilterChipDefaults.shapes(),
                            colors = FilterChipDefaults.filterChipColors(),
                            onClick = { day = null },
                            label = { Text(stringResource(R.string.quick_add_today)) }
                        )
                        FilterChip(
                            selected = day == yesterday,
                            shapes = FilterChipDefaults.shapes(),
                            colors = FilterChipDefaults.filterChipColors(),
                            onClick = { day = yesterday },
                            label = { Text(stringResource(R.string.quick_add_yesterday)) }
                        )
                        val other = day?.takeIf { it != yesterday }
                        FilterChip(
                            selected = other != null,
                            shapes = FilterChipDefaults.shapes(),
                            colors = FilterChipDefaults.filterChipColors(),
                            onClick = { showDate = true },
                            label = { Text(other?.format(AppDates.dateFormatter()) ?: stringResource(R.string.quick_add_other_date)) }
                        )
                    }
                }
            }
        }
        error?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        // The full form, with what's typed here: as a transfer, a subscription, or with every field.
        FlowRow(verticalArrangement = Arrangement.Center) {
            TextButton(
                onClick = { onMoreDetails(draft(TransactionType.TRANSFER)) },
                enabled = !saving,
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(R.string.txn_type_transfer)) }
            onSubscription?.let { open ->
                TextButton(onClick = { open(draft()) }, enabled = !saving, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.add_tab_subscription))
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onMoreDetails(draft()) }, enabled = !saving, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.quick_add_more_details))
            }
        }
        Button(
            onClick = ::save,
            enabled = amount != null && category != null && !saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.Component.minTouchTarget),
            shapes = ButtonDefaults.shapes()
        ) {
            if (saving) {
                LoadingIndicator(modifier = Modifier.size(Dimensions.Icon.medium))
            } else {
                Text(stringResource(R.string.add_save))
            }
        }
    }

    if (showCalculator) {
        AmountCalculatorSheet(
            initialAmount = amountText,
            currency = currency,
            onApply = { result ->
                QuickAddActivity.normalizeAmount(result.toPlainString(), decimalComma)?.let { amountText = it }
                showCalculator = false
            },
            onDismiss = { showCalculator = false }
        )
    }
    if (showPicker) {
        QuickCategoryPickerSheet(
            currentCategory = category.orEmpty(),
            categories = ofType,
            onCategorySelected = {
                category = it
                pinned = it
                showPicker = false
            },
            onDismiss = { showPicker = false },
            mostUsed = setup.mostUsed
        )
    }
    if (showDate) {
        AddDatePickerDialog(
            initial = day ?: LocalDate.now(),
            onPick = { millis ->
                // The picker's millis are UTC midnight of the chosen day.
                val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                day = picked.takeIf { it != LocalDate.now() }
            },
            onDismiss = { showDate = false }
        )
    }
}

/** How long the mic waits for the language list before starting anyway. */
private const val LANGUAGES_WAIT_MS = 3_000L
