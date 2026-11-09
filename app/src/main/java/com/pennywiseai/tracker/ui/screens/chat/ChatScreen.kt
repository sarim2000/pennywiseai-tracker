package com.pennywiseai.tracker.ui.screens.chat

import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.repository.ModelState
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.pennywiseai.tracker.utils.TokenUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.pennywiseai.tracker.ui.icons.categoryLabel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentResponse by viewModel.currentResponse.collectAsStateWithLifecycle()
    val pendingAction by viewModel.pendingAction.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val isConfirming by viewModel.isConfirming.collectAsStateWithLifecycle()
    val isDeveloperMode by viewModel.isDeveloperModeEnabled.collectAsStateWithLifecycle()
    val chatStats by viewModel.chatStats.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadedMB by viewModel.downloadedMB.collectAsStateWithLifecycle()
    val totalMB by viewModel.totalMB.collectAsStateWithLifecycle()
    
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, currentResponse) {
        if (messages.isNotEmpty() || currentResponse.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(
                    index = if (currentResponse.isNotEmpty()) messages.size else messages.size - 1
                )
            }
        }
    }

    // "Receipt settled": only an Add that was saved gets the check + Confirm haptic.
    // Keyed on the count, so the haptic lands even when no hold keeps the check up;
    // only for Adds made since this screen came up, not ones from an earlier visit.
    val haptics = LocalHapticFeedback.current
    val addsAtEntry = remember { uiState.adds }
    LaunchedEffect(uiState.adds) {
        if (uiState.adds > addsAtEntry) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    // Scroll behaviors for TopAppBar
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = scrollBehaviorSmall
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.chat_title),
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .background(MaterialTheme.colorScheme.background)
            .padding(paddingValues)
    ) {
        when (modelState) {
            ModelState.NOT_DOWNLOADED, ModelState.DOWNLOADING, ModelState.ERROR -> {
                val isDownloading = modelState == ModelState.DOWNLOADING
                // Show existing messages if any, but disable input
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // If no messages, show the download prompt centered
                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                                modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                            ) {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(if (isDownloading) R.string.chat_downloading_model else R.string.chat_model_required_title),
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    text = if (isDownloading) stringResource(R.string.chat_download_progress, downloadedMB, totalMB) else stringResource(R.string.chat_model_required_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isDownloading) {
                                    LinearWavyProgressIndicator(
                                        progress = { downloadProgress / 100f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedButton(onClick = { viewModel.cancelDownload() }, shapes = ButtonDefaults.shapes()) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(Dimensions.Icon.small)
                                        )
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text(stringResource(R.string.chat_cancel))
                                    }
                                } else {
                                    Button(onClick = { viewModel.startModelDownload() }, shapes = ButtonDefaults.shapes()) {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(Dimensions.Icon.small)
                                        )
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text(stringResource(R.string.chat_download_with_size, totalMB))
                                    }
                                }
                                TextButton(onClick = onNavigateToSettings, shapes = ButtonDefaults.shapes()) {
                                    Text(stringResource(R.string.chat_settings))
                                }
                            }
                        }
                    } else {
                        // Show existing messages (read-only)
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .overScrollVertical(),
                            contentPadding = PaddingValues(
                                start = Dimensions.Padding.content,
                                end = Dimensions.Padding.content,
                                top = Dimensions.Padding.content,
                                bottom = Spacing.lg
                            ),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            flingBehavior = rememberOverscrollFlingBehavior { listState }
                        ) {
                            items(messages, key = { it.id }) { message ->
                                ChatMessageItem(message = message, modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null))
                            }
                        }
                    }

                    // Show model required banner at bottom
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        tonalElevation = 3.dp
                    ) {
                        if (isDownloading) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Dimensions.Padding.content),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.chat_downloading_model),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = stringResource(R.string.chat_download_progress, downloadedMB, totalMB),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.cancelDownload() },
                                        modifier = Modifier.padding(start = Spacing.sm),
                                        shapes = ButtonDefaults.shapes()
                                    ) {
                                        Text(stringResource(R.string.chat_cancel))
                                    }
                                }
                                LinearWavyProgressIndicator(
                                    progress = { downloadProgress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Dimensions.Padding.content),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.chat_model_required_short),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        // 1.6 GB via the system Download Manager; some phones (Samsung
                                        // with Data saver) won't start it on mobile data.
                                        text = stringResource(R.string.chat_model_size_hint),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Button(
                                    onClick = { viewModel.startModelDownload() },
                                    modifier = Modifier.padding(start = Spacing.sm),
                                    shapes = ButtonDefaults.shapes()
                                ) {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(Dimensions.Icon.small)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text(stringResource(R.string.chat_download))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(Dimensions.Component.bottomBarHeight))
                }
            }

            ModelState.READY, ModelState.LOADING -> {
                // Show loading overlay when model is loading
                if (modelState == ModelState.LOADING) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            LoadingIndicator()
                            Text(
                                text = stringResource(R.string.chat_initializing_model),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.chat_initializing_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Developer info card
                        AnimatedVisibility(
                            visible = isDeveloperMode && messages.isNotEmpty(),
                            enter = pwExpand(),
                            exit = pwCollapse()
                        ) {
                            DeveloperInfoCard(chatStats = chatStats)
                        }
                        
                        // Clear chat button when there are messages
                        AnimatedVisibility(
                            visible = messages.isNotEmpty(),
                            enter = pwExpand(),
                            exit = pwCollapse()
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = Dimensions.Padding.content,
                                            vertical = Spacing.sm
                                        ),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { viewModel.clearChat() },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        shapes = ButtonDefaults.shapes()
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(Dimensions.Icon.small)
                                        )
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text(stringResource(R.string.chat_clear_chat))
                                    }
                                }
                            }
                        }

                        // Messages list
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .overScrollVertical(),
                            contentPadding = PaddingValues(
                                start = Dimensions.Padding.content,
                                end = Dimensions.Padding.content,
                                top = Dimensions.Padding.content,
                                bottom = Spacing.lg
                            ),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            flingBehavior = rememberOverscrollFlingBehavior { listState },
                            reverseLayout = false
                        ) {
                            // Example prompts when no messages
                            if (messages.isEmpty() && currentResponse.isEmpty() && !uiState.isLoading) {
                                item(key = EMPTY_KEY) {
                                    ChatEmptyState(
                                        onPromptClick = { prompt ->
                                            viewModel.sendMessage(prompt)
                                        }
                                    )
                                }
                            }

                            // Rows only slide (placement), never fade: every row swaps in place —
                            // the streamed reply for its saved message, the card for its "Added"
                            // line — and a fading row would draw over or under its replacement.
                            // On device a fade-in on the card also stuck at alpha 0, hiding it.
                            items(messages, key = { it.id }) { message ->
                                ChatMessageItem(message = message, modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null))
                            }

                            // A transaction the model proposed — the user confirms it (#170)
                            // A saved card stays drawn (settled) for a beat after it's done.
                            (pendingAction ?: uiState.settledCard)?.let { action ->
                                item(key = PENDING_KEY) {
                                    if (action is com.pennywiseai.tracker.data.model.PendingChatAction.Add) {
                                        AddReviewCard(
                                            rows = action.rows,
                                            categories = categories,
                                            enabled = !isConfirming,
                                            saved = uiState.cardSaved,
                                            onToggle = viewModel::toggleRow,
                                            onEdit = viewModel::editRow,
                                            onConfirm = viewModel::confirmPendingAction,
                                            onDismiss = viewModel::dismissPendingAction,
                                            modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                                        )
                                    } else {
                                        PendingActionCard(
                                            action = action,
                                            enabled = !isConfirming,
                                            onConfirm = { viewModel.confirmPendingAction() },
                                            onDismiss = { viewModel.dismissPendingAction() },
                                            modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                                        )
                                    }
                                }
                            }

                            // The reply on its way — typing dots, then the streamed text — in one
                            // keyed slot, so the swap happens in place and the saved reply lands
                            // where it was.
                            if (currentResponse.isNotEmpty() || uiState.isLoading) {
                                item(key = REPLY_KEY) {
                                    if (currentResponse.isNotEmpty()) {
                                        ChatMessageItem(
                                            message = com.pennywiseai.tracker.data.database.entity.ChatMessage(
                                                message = currentResponse,
                                                isUser = false,
                                                timestamp = System.currentTimeMillis()
                                            ),
                                            modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                                            isStreaming = true
                                        )
                                    } else {
                                        // One status line per request, picked when the wait starts,
                                        // so a tool call's silent few seconds don't look like a hang.
                                        val status = remember(uiState.isLoading) { THINKING_LINES.random() }
                                        TypingIndicator(status = stringResource(status), modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null))
                                    }
                                }
                            }
                        }

                        // Error message
                        AnimatedVisibility(
                            visible = uiState.error != null,
                            enter = pwExpand(),
                            exit = pwCollapse()
                        ) {
                            PennyWiseCardV2(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Dimensions.Padding.content),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = uiState.error?.asString().orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { viewModel.clearError() }, shapes = IconButtonDefaults.shapes()) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.chat_dismiss),
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }

                        // Input field
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 3.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Dimensions.Padding.content),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                OutlinedTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(focusRequester),
                                    placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
                                    enabled = !uiState.isLoading,
                                    maxLines = 3,
                                    shape = MaterialTheme.shapes.extraLarge
                                )

                                Spacer(modifier = Modifier.width(Spacing.sm))

                                FilledIconButton(
                                    onClick = {
                                        viewModel.sendMessage(inputText)
                                        inputText = ""
                                        // Keep keyboard open by requesting focus
                                        focusRequester.requestFocus()
                                    },
                                    enabled = inputText.isNotBlank() && !uiState.isLoading && !isConfirming,
                                    modifier = Modifier.size(48.dp),
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    if (uiState.isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(Dimensions.Icon.medium),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = stringResource(R.string.chat_send)
                                        )
                                    }
                                }
                            }
                        }
                        // The bottom nav hides while the keyboard is up, so its
                        // clearance would just leave a gap above the keyboard.
                        if (!WindowInsets.isImeVisible) {
                            Spacer(modifier = Modifier.height(Dimensions.Component.bottomBarHeight))
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
fun DeveloperInfoCard(
    chatStats: ChatStats,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val usageHint = remember(chatStats.contextUsagePercent) {
        TokenUtils.getUsageColorHint(chatStats.contextUsagePercent)
    }
    val usageColor = when (usageHint) {
        "critical" -> MaterialTheme.colorScheme.error
        "warning" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    
    PennyWiseCardV2(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content)
            .padding(bottom = Spacing.sm),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        onClick = { isExpanded = !isExpanded }
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = pluralStringResource(R.plurals.chat_stats_model_messages, chatStats.messageCount, chatStats.messageCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.chat_stats_tokens, TokenUtils.formatNumber(chatStats.estimatedTokens)),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = usageColor
                    )
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(if (isExpanded) R.string.chat_collapse else R.string.chat_expand),
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            AnimatedVisibility(
                visible = isExpanded,
                enter = pwExpand(),
                exit = pwCollapse()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    // Context usage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.chat_context_usage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.chat_context_percent, chatStats.contextUsagePercent),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = usageColor
                        )
                    }

                    LinearProgressIndicator(
                        progress = { chatStats.contextUsagePercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Spacing.xs)
                            .clip(CircleShape),
                        color = usageColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        drawStopIndicator = {}
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.chat_stats_tokens_of_max, TokenUtils.formatNumber(chatStats.estimatedTokens), TokenUtils.formatNumber(chatStats.maxTokens)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
    }
}

// Keys for the list's non-message rows; message rows key on their saved id.
private const val EMPTY_KEY = "chat-empty"
private const val PENDING_KEY = "chat-pending-card"
private const val REPLY_KEY = "chat-reply"

private val THINKING_LINES = listOf(
    R.string.chat_thinking_reading,
    R.string.chat_thinking_working,
    R.string.chat_thinking_checking,
    R.string.chat_thinking_crunching,
    R.string.chat_thinking_one_moment
)

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    status: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        PennyWiseCardV2(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Three animated dots
                val infiniteTransition = rememberInfiniteTransition(label = "typing")
                
                for (i in 0..2) {
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes {
                                durationMillis = 1200
                                0.3f at 0
                                1f at 400
                                0.3f at 800
                            },
                            repeatMode = RepeatMode.Restart,
                            initialStartOffset = StartOffset(i * 200)
                        ),
                        label = "dot_alpha_$i"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha),
                                shape = RoundedCornerShape(50)
                            )
                    )
                }
                if (status != null) {
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(
    message: com.pennywiseai.tracker.data.database.entity.ChatMessage,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        PennyWiseCardV2(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Text(
                // Amounts are baked into the stored text; mask them on screen only.
                text = com.pennywiseai.tracker.utils.CurrencyFormatter.maskAmountsIn(message.message),
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isUser)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSecondaryContainer
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                if (isStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.dp
                    )
                }
                Text(
                    text = timeFormat.format(Date(message.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.isUser)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatEmptyState(
    onPromptClick: (String) -> Unit
) {
    val examplePrompts = listOf(
        stringResource(R.string.chat_example_prompt_coffee),
        stringResource(R.string.chat_example_prompt_salary),
        stringResource(R.string.chat_example_prompt_groceries),
        stringResource(R.string.chat_example_prompt_spent)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = stringResource(R.string.chat_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = stringResource(R.string.chat_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            examplePrompts.forEach { prompt ->
                SuggestionChip(
                    onClick = { onPromptClick(prompt) },
                    label = { Text(prompt) },
                    icon = {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                    }
                )
            }
        }
    }
}

/** Confirm card for a delete or update the AI proposed (#170). Nothing happens without the tap. */
@Composable
private fun PendingActionCard(
    action: com.pennywiseai.tracker.data.model.PendingChatAction,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val own = { t: com.pennywiseai.tracker.data.database.entity.TransactionEntity -> com.pennywiseai.tracker.utils.CurrencyFormatter.formatCurrency(t.amount, t.currency) }
    val (title, headline, detail, button) = when (action) {
        // AddReviewCard shows these.
        is com.pennywiseai.tracker.data.model.PendingChatAction.Add -> return
        is com.pennywiseai.tracker.data.model.PendingChatAction.Delete -> {
            val t = action.transaction
            listOf(
                stringResource(R.string.chat_action_delete_title),
                "${own(t)} · ${t.merchantName}",
                "${categoryLabel(t.category)} · ${t.dateTime.toLocalDate()}",
                stringResource(R.string.chat_action_delete)
            )
        }
        is com.pennywiseai.tracker.data.model.PendingChatAction.Update -> {
            val t = action.transaction
            val changes = listOfNotNull(
                action.newMerchant?.let { stringResource(R.string.chat_action_change_merchant, it) },
                action.newCategory?.let { stringResource(R.string.chat_action_change_category, categoryLabel(it)) }
            ).joinToString(", ")
            listOf(
                stringResource(R.string.chat_action_update_title),
                "${own(t)} · ${t.merchantName}",
                "${categoryLabel(t.category)} · ${t.dateTime.toLocalDate()}\n$changes",
                stringResource(R.string.chat_action_update)
            )
        }
    }
    val isDelete = action is com.pennywiseai.tracker.data.model.PendingChatAction.Delete
    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDelete) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        val fg = if (isDelete) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = fg)
            Text(text = headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = fg)
            Text(text = detail, style = MaterialTheme.typography.bodySmall, color = fg)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = onDismiss, enabled = enabled, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.chat_cancel)) }
                Button(
                    onClick = onConfirm,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    colors = if (isDelete) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
                    shapes = ButtonDefaults.shapes()
                ) { Text(button) }
            }
        }
    }
}
