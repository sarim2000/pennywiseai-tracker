package com.pennywiseai.tracker.ui.screens.unrecognized

import androidx.compose.ui.platform.LocalContext
import com.pennywiseai.tracker.receiver.BankNotificationConfig
import com.pennywiseai.tracker.utils.AppDates

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.skeleton.TransactionItemSkeleton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.pennywiseai.tracker.ui.animations.StaggeredEntrance
import com.pennywiseai.tracker.ui.animations.rememberEntranceDone
import com.pennywiseai.tracker.ui.animations.staggerDelayMs
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import com.pennywiseai.tracker.navigation.AddTransaction
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnrecognizedSmsScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
    onAddSuggestion: (AddTransaction) -> Unit,
    viewModel: UnrecognizedSmsViewModel = hiltViewModel()
) {
    val unrecognizedMessages by viewModel.unrecognizedMessages.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val showReported by viewModel.showReported.collectAsStateWithLifecycle()
    val formatChanged by viewModel.formatChanged.collectAsStateWithLifecycle()
    val taughtTemplates by viewModel.taughtTemplates.collectAsStateWithLifecycle()
    val teach by viewModel.teach.collectAsStateWithLifecycle()
    val inboxPicker by viewModel.inboxPicker.collectAsStateWithLifecycle()
    var showTaughtTemplates by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.notices.collect { snackbarHostState.showSnackbar(context.getString(it)) }
    }
    var selectedMessage by remember { mutableStateOf<UnrecognizedSmsEntity?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }
    val lazyListState = rememberLazyListState()

    // Staggered entrance — only animates on first composition
    val hasAnimated = rememberEntranceDone()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.unrecognized_title),
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.unrecognized_back))
                    }
                },
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
        ) {
            // Header card with info (0ms delay)
            item {
                StaggeredEntrance(delayMs = 0L, skip = hasAnimated) {
                    PennyWiseCardV2(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.unrecognized_header_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = stringResource(R.string.unrecognized_header_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Filter toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                FilterChip(
                                    selected = showReported,
                                    shapes = FilterChipDefaults.shapes(),
                                    colors = FilterChipDefaults.filterChipColors(),
                                    onClick = { viewModel.toggleShowReported() },
                                    label = { Text(stringResource(R.string.unrecognized_show_reported)) },
                                    leadingIcon = if (showReported) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small)) }
                                    } else null
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                if (unrecognizedMessages.isNotEmpty()) {
                                    val reportedCount = unrecognizedMessages.count { it.reported }
                                    val unreportedCount = unrecognizedMessages.size - reportedCount

                                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        if (unreportedCount > 0) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ) {
                                                Text(pluralStringResource(R.plurals.unrecognized_new_count, unreportedCount, unreportedCount))
                                            }
                                        }
                                        if (reportedCount > 0) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(pluralStringResource(R.plurals.unrecognized_reported_count, reportedCount, reportedCount))
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                if (viewModel.canReadInbox) {
                                    TextButton(onClick = viewModel::openInboxPicker, shapes = ButtonDefaults.shapes()) {
                                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text(stringResource(R.string.teach_find_message))
                                    }
                                }
                                if (taughtTemplates.isNotEmpty()) {
                                    TextButton(onClick = { showTaughtTemplates = true }, shapes = ButtonDefaults.shapes()) {
                                        Text(stringResource(R.string.teach_taught_count, taughtTemplates.size))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Loading State — skeleton items
            if (isLoading) {
                items(4) {
                    UnrecognizedSmsItemSkeleton()
                }
            } else if (unrecognizedMessages.isEmpty()) {
                // Empty State
                item {
                    PennyWiseEmptyState(
                        icon = Icons.Outlined.MarkEmailRead,
                        headline = stringResource(R.string.unrecognized_empty_title),
                        description = stringResource(R.string.unrecognized_empty_body)
                    )
                }
            } else {
                // Message items with the first-entrance stagger
                itemsIndexed(
                    items = unrecognizedMessages,
                    key = { _, message -> message.id }
                ) { index, message ->
                    StaggeredEntrance(delayMs = staggerDelayMs(index), skip = hasAnimated, modifier = Modifier.animateItem()) {
                        UnrecognizedSmsItem(
                            message = message,
                            suggestion = suggestions[message.id]?.transaction,
                            onAddSuggestion = {
                                suggestions[message.id]?.let {
                                    if (it.taught) viewModel.addTaught(message) else onAddSuggestion(it.draft())
                                }
                            },
                            formatChanged = message.id in formatChanged,
                            onTeach = { viewModel.teachWaiting(message) },
                            onReport = {
                                viewModel.reportMessage(message)
                            },
                            onDelete = {
                                selectedMessage = message
                                showDeleteConfirmation = true
                            }
                        )
                    }
                }
            }
        }
    }

    teach?.let { state ->
        TeachMessageSheet(
            state = state,
            onTap = viewModel::onTeachTap,
            onSelectField = viewModel::onTeachField,
            onSelectType = viewModel::onTeachType,
            onSelectCurrency = viewModel::onTeachCurrency,
            onSelectFormat = viewModel::onTeachFormat,
            onMerchantTyped = viewModel::onTeachMerchantTyped,
            onSave = viewModel::saveTeach,
            onDismiss = viewModel::dismissTeach
        )
    }

    inboxPicker?.let { picker ->
        InboxPickerSheet(
            messages = picker.messages,
            onPick = viewModel::teachFromInbox,
            onDismiss = viewModel::closeInboxPicker
        )
    }

    if (showTaughtTemplates && taughtTemplates.isNotEmpty()) {
        TaughtTemplatesSheet(
            templates = taughtTemplates,
            onDelete = viewModel::deleteTemplate,
            onDismiss = { showTaughtTemplates = false }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation && selectedMessage != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmation = false
                selectedMessage = null
            },
            title = { Text(stringResource(R.string.unrecognized_delete_title)) },
            text = {
                Text(stringResource(R.string.unrecognized_delete_body))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedMessage?.let { viewModel.deleteMessage(it) }
                        showDeleteConfirmation = false
                        selectedMessage = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.unrecognized_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        selectedMessage = null
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.unrecognized_cancel))
                }
            }
        )
    }
}

@Composable
private fun UnrecognizedSmsItem(
    message: UnrecognizedSmsEntity,
    suggestion: ParsedTransaction?,
    onAddSuggestion: (ParsedTransaction) -> Unit,
    formatChanged: Boolean,
    onTeach: () -> Unit,
    onReport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth()
    ) {
        val context = LocalContext.current
        val displayName = remember(message.sender) { BankNotificationConfig.displayName(context, message.sender) }
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Header with sender and date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = message.receivedAt.format(
                            AppDates.dateTimeFormatter()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (message.reported) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            stringResource(R.string.unrecognized_reported_badge),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // Message content (truncated)
            Text(
                text = CurrencyFormatter.maskAmountsIn(message.smsBody),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            suggestion?.let { tx ->
                SuggestionLine(tx, onAdd = { onAddSuggestion(tx) })
            }

            if (formatChanged) {
                Text(
                    text = stringResource(R.string.teach_format_changed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDelete, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.unrecognized_delete),
                        modifier = Modifier.size(Dimensions.Icon.inline)
                    )
                }

                if (!message.reported) {
                    IconButton(onClick = onReport, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            Icons.Default.BugReport,
                            contentDescription = stringResource(R.string.unrecognized_report),
                            modifier = Modifier.size(Dimensions.Icon.inline)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                FilledTonalButton(onClick = onTeach, shapes = ButtonDefaults.shapes()) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.teach_action))
                }
            }
        }
    }
}

/** "Looks like: ₹500.00 debit · Test Store · ••1234" with an Add action (#994). */
@Composable
private fun SuggestionLine(tx: ParsedTransaction, onAdd: () -> Unit) {
    val amount = CurrencyFormatter.formatCurrency(tx.amount, tx.currency)
    val parts = listOfNotNull(
        if (tx.type == TransactionType.INCOME) stringResource(R.string.unrecognized_suggestion_credit, amount)
        else stringResource(R.string.unrecognized_suggestion_debit, amount),
        tx.merchant,
        tx.accountLast4?.let { "••$it" }
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.unrecognized_suggestion, parts.joinToString(" · ")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onAdd, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.unrecognized_suggestion_add))
        }
    }
}

@Composable
private fun UnrecognizedSmsItemSkeleton(
    modifier: Modifier = Modifier
) {
    TransactionItemSkeleton(modifier = modifier)
}
