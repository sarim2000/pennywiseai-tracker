package com.pennywiseai.tracker.presentation.unrecognized

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.parser.core.AmountFormat
import com.pennywiseai.parser.core.MessageTemplate
import com.pennywiseai.parser.core.MessageTemplates
import com.pennywiseai.parser.core.TemplateField
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.notification.BankNotificationConfig
import com.pennywiseai.tracker.data.repository.SmsInboxReader
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private data class FieldColors(val container: Color, val content: Color)

@Composable
private fun TeachField.colors(): FieldColors = when (this) {
    TeachField.AMOUNT -> FieldColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    TeachField.MERCHANT -> FieldColors(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    TeachField.ACCOUNT -> FieldColors(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
}

/** Keeps every character in place, so tap positions still line up while amounts are hidden. */
private fun maskedDigits(text: String): String =
    if (AmountPrivacy.hidden) text.map { if (it.isDigit()) '•' else it }.joinToString("") else text

/**
 * One sheet: the message, tappable, above the transaction it adds up to. The card is the
 * preview, and the Save button states the result, so what's saved is what's on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeachMessageSheet(
    state: TeachState,
    onTap: (Int) -> Unit,
    onSelectField: (TeachField) -> Unit,
    onSelectType: (TransactionType) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onSelectFormat: (AmountFormat) -> Unit,
    onMerchantTyped: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxWidth().imePadding()) {
            // The Save button stays outside the scroll: it says what will be saved, so it's always in view.
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimensions.Padding.content)
                    .padding(bottom = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                val context = LocalContext.current
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.teach_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        remember(state.sender) { BankNotificationConfig.displayName(context, state.sender) },
                        style = PennyWiseText.rowSubtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TappableMessage(state, onTap)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    FieldChips(state, onSelectField)
                    Text(
                        stringResource(
                            when (state.active) {
                                TeachField.AMOUNT -> R.string.teach_hint_amount
                                TeachField.MERCHANT -> R.string.teach_hint_merchant
                                TeachField.ACCOUNT -> R.string.teach_hint_account
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TransactionCard(state, onSelectType, onSelectCurrency, onSelectFormat, onMerchantTyped)

                if (state.cannotLearn) {
                    Text(
                        stringResource(R.string.teach_cannot_learn),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        stringResource(R.string.teach_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onSave,
                enabled = state.missing == null,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.Padding.content)
                    .padding(bottom = Spacing.md)
            ) {
                Text(saveLabel(state), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun saveLabel(state: TeachState): String = when (state.missing) {
    TeachMissing.AMOUNT -> stringResource(R.string.teach_need_amount)
    TeachMissing.CURRENCY -> stringResource(R.string.teach_need_currency)
    TeachMissing.FORMAT -> stringResource(R.string.teach_need_format)
    TeachMissing.DIRECTION -> stringResource(R.string.teach_need_direction)
    null -> {
        val amount = state.amountValue?.let { CurrencyFormatter.formatCurrency(it, state.currency.orEmpty()) }.orEmpty()
        if (state.type == TransactionType.INCOME) stringResource(R.string.teach_save_income, amount)
        else stringResource(R.string.teach_save_expense, amount)
    }
}

@Composable
private fun TappableMessage(state: TeachState, onTap: (Int) -> Unit) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val amountColors = TeachField.AMOUNT.colors()
    val merchantColors = TeachField.MERCHANT.colors()
    val accountColors = TeachField.ACCOUNT.colors()
    val text = buildAnnotatedString {
        append(maskedDigits(state.body))
        fun highlight(range: IntRange?, colors: FieldColors) {
            range ?: return
            addStyle(
                SpanStyle(background = colors.container, color = colors.content, fontWeight = FontWeight.SemiBold),
                range.first,
                range.last + 1
            )
        }
        highlight(state.amount, amountColors)
        highlight(state.merchant, merchantColors)
        highlight(state.account, accountColors)
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
            onTextLayout = { layout = it },
            modifier = Modifier
                .padding(Dimensions.Padding.content)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        layout?.let { onTap(it.getOffsetForPosition(position)) }
                    }
                }
                // Tapping a spot in the text is no use to a screen reader: every word is an action instead.
                .semantics {
                    customActions = state.spans.map { span ->
                        CustomAccessibilityAction(maskedDigits(state.body.substring(span.range.first, span.range.last + 1))) {
                            onTap(span.range.first)
                            true
                        }
                    }
                }
        )
    }
}

@Composable
private fun FieldChips(state: TeachState, onSelect: (TeachField) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TeachField.entries.forEach { field ->
            val colors = field.colors()
            val filled = when (field) {
                TeachField.AMOUNT -> state.amount != null
                TeachField.MERCHANT -> state.merchant != null
                TeachField.ACCOUNT -> state.account != null
            }
            FilterChip(
                selected = state.active == field,
                onClick = { onSelect(field) },
                shapes = FilterChipDefaults.shapes(),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.container,
                    selectedLabelColor = colors.content
                ),
                label = { Text(stringResource(field.label())) },
                leadingIcon = {
                    Box(
                        Modifier
                            .size(Dimensions.Icon.tiny)
                            .background(
                                if (filled) colors.content else colors.container,
                                CircleShape
                            )
                    )
                }
            )
        }
    }
}

private fun TeachField.label() = when (this) {
    TeachField.AMOUNT -> R.string.teach_field_amount
    TeachField.MERCHANT -> R.string.teach_field_merchant
    TeachField.ACCOUNT -> R.string.teach_field_account
}

@Composable
private fun TransactionCard(
    state: TeachState,
    onSelectType: (TransactionType) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onSelectFormat: (AmountFormat) -> Unit,
    onMerchantTyped: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.smd)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val value = state.amountValue
                val code = state.currency
                Text(
                    text = when {
                        value != null && code != null -> CurrencyFormatter.formatCurrency(value, code)
                        state.amountText != null -> maskedDigits(state.amountText.orEmpty())
                        else -> stringResource(R.string.teach_amount_placeholder)
                    },
                    style = PennyWiseText.amountLarge,
                    color = if (state.amount == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                CurrencyButton(code, onSelectCurrency)
            }

            state.amountSource?.let { source ->
                Text(
                    stringResource(R.string.teach_amount_source, maskedDigits(source)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (state.canChooseFormat) FormatChoice(state, onSelectFormat)

            ConnectedChoiceRow(
                options = listOf(TransactionType.EXPENSE, TransactionType.INCOME),
                selected = state.type ?: TransactionType.BALANCE_UPDATE,
                onSelect = onSelectType,
                label = {
                    stringResource(if (it == TransactionType.INCOME) R.string.teach_direction_in else R.string.teach_direction_out)
                }
            )

            OutlinedTextField(
                value = state.merchantName,
                onValueChange = onMerchantTyped,
                label = { Text(stringResource(R.string.teach_merchant_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                state.accountDigits?.let { stringResource(R.string.teach_account_value, maskedDigits(it)) }
                    ?: stringResource(R.string.teach_account_none),
                style = PennyWiseText.rowSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CurrencyButton(code: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, shapes = ButtonDefaults.shapes()) {
            Text(code ?: stringResource(R.string.teach_currency))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.inline))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CurrencyFormatter.getSupportedCurrencies().forEach { currency ->
                DropdownMenuItem(
                    text = { Text("${CurrencyFormatter.getCurrencySymbol(currency)} $currency") },
                    onClick = {
                        onSelect(currency)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** "1.234" in a three-decimal currency could be either: show both as money and let them say. */
@Composable
private fun FormatChoice(state: TeachState, onSelect: (AmountFormat) -> Unit) {
    val text = state.amountText ?: return
    val code = state.currency ?: return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.teach_format_question), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AmountFormat.entries.forEach { format ->
                MessageTemplates.parseAmount(text, format, code)?.let { value ->
                    FilterChip(
                        selected = state.amountFormat == format,
                        onClick = { onSelect(format) },
                        shapes = FilterChipDefaults.shapes(),
                        label = { Text(CurrencyFormatter.formatCurrency(value, code)) }
                    )
                }
            }
        }
    }
}

/** Recent messages that look like money and became nothing: pick one to teach it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxPickerSheet(
    messages: List<SmsInboxReader.Message>?,
    onPick: (SmsInboxReader.Message) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(stringResource(R.string.teach_picker_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.teach_picker_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                messages == null -> Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
                messages.isEmpty() -> Text(
                    stringResource(R.string.teach_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = Spacing.lg)
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    contentPadding = PaddingValues(bottom = Spacing.md)
                ) {
                    items(messages, key = { "${it.sender}|${it.timestamp}|${it.body.hashCode()}" }) { message ->
                        InboxRow(message, onClick = { onPick(message) })
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxRow(message: SmsInboxReader.Message, onClick: () -> Unit) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                remember(message.sender) { BankNotificationConfig.displayName(context, message.sender) },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(message.timestamp), ZoneId.systemDefault())
                    .format(AppDates.dateTimeFormatter()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                CurrencyFormatter.maskAmountsIn(message.body),
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** What's been taught, so a wrong one can be removed. The transactions it added stay. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaughtTemplatesSheet(
    templates: List<MessageTemplate>,
    onDelete: (MessageTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(stringResource(R.string.teach_taught_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.teach_taught_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                contentPadding = PaddingValues(bottom = Spacing.md)
            ) {
                items(templates, key = { it.id }) { template ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.animateItem().fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(start = Dimensions.Padding.content, top = Spacing.sm, bottom = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(
                                    template.bankName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    shapeText(template),
                                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content)
                                )
                            }
                            IconButton(onClick = { onDelete(template) }, shapes = IconButtonDefaults.shapes()) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.teach_delete_template))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A template in words: "Purchase of SAR [Amount] at [Merchant] …". */
@Composable
private fun shapeText(template: MessageTemplate): String {
    val amount = stringResource(R.string.teach_field_amount)
    val merchant = stringResource(R.string.teach_field_merchant)
    val account = stringResource(R.string.teach_field_account)
    return template.parts.joinToString("") { part ->
        when (part.field) {
            TemplateField.LITERAL -> part.text
            TemplateField.AMOUNT -> "[$amount]"
            TemplateField.MERCHANT -> "[$merchant]"
            TemplateField.ACCOUNT -> "[$account]"
            TemplateField.NUMBER, TemplateField.TOKEN, TemplateField.DATE_WORD -> "…"
        }
    }
}
