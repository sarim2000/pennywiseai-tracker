package com.pennywiseai.tracker.ui.screens.chat

import com.pennywiseai.tracker.ui.theme.pennyWiseButtonShapes
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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.repository.ModelState
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import androidx.compose.ui.graphics.Color
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
    val isConfirming by viewModel.isConfirming.collectAsStateWithLifecycle()
    val baseCurrency by viewModel.baseCurrency.collectAsStateWithLifecycle()
    val isDeveloperMode by viewModel.isDeveloperModeEnabled.collectAsStateWithLifecycle()
    val chatStats by viewModel.chatStats.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadedMB by viewModel.downloadedMB.collectAsStateWithLifecycle()
    val totalMB by viewModel.totalMB.collectAsStateWithLifecycle()
    
    var inputText by remember { mutableStateOf(TextFieldValue()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    
    // A real chat starts with the user's message, so a lone reply is the greeting.
    val isFreshChat = messages.isEmpty() || messages.singleOrNull()?.isUser == false
    val shownMessages = remember(messages) {
        if (messages.size == 1 && !messages.first().isUser) {
            messages.map { it.copy(message = shortGreeting(it.message)) }
        } else {
            messages
        }
    }
    val modelMissing = modelState == ModelState.NOT_DOWNLOADED ||
        modelState == ModelState.DOWNLOADING ||
        modelState == ModelState.ERROR

    // Auto-scroll to bottom when new messages arrive; without a model, to the panel below them
    LaunchedEffect(messages.size, currentResponse, modelMissing) {
        if (messages.isNotEmpty() || currentResponse.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(
                    index = if (currentResponse.isNotEmpty() || modelMissing) messages.size else messages.size - 1
                )
            }
        }
    }

    // Scroll behaviors for TopAppBar
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = scrollBehaviorSmall

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.chat_title),
            )
        }
    ) { paddingValues ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(paddingValues)
    ) {
        when (modelState) {
            ModelState.NOT_DOWNLOADED, ModelState.DOWNLOADING, ModelState.ERROR -> {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Any existing messages stay readable above the panel; there's no composer.
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
                        items(shownMessages) { message ->
                            ChatMessageItem(message = message)
                        }
                        item {
                            ModelActivationPanel(
                                isDownloading = modelState == ModelState.DOWNLOADING,
                                downloadProgress = downloadProgress,
                                downloadedMB = downloadedMB,
                                totalMB = totalMB,
                                error = uiState.error?.asString(),
                                onDownload = {
                                    viewModel.clearError()
                                    viewModel.startModelDownload()
                                },
                                onCancel = { viewModel.cancelDownload() },
                                onOpenSettings = onNavigateToSettings
                            )
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
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            DeveloperInfoCard(chatStats = chatStats)
                        }
                        
                        // Token limit warning
                        AnimatedVisibility(
                            visible = chatStats.contextUsagePercent >= 80,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            TokenLimitWarning(
                                usagePercent = chatStats.contextUsagePercent,
                                onClearChat = { viewModel.clearChat() }
                            )
                        }

                        // Clear chat button when there are messages
                        AnimatedVisibility(
                            visible = messages.isNotEmpty(),
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
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
                                        shapes = pennyWiseButtonShapes()
                                    ) {
                                        Icon(
                                            Icons.Outlined.Delete,
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
                            items(shownMessages) { message ->
                                ChatMessageItem(message = message)
                            }

                            // Example prompts until the user has said something
                            if (isFreshChat && currentResponse.isEmpty() && !uiState.isLoading) {
                                item {
                                    ChatEmptyState(
                                        onPromptClick = { prompt ->
                                            inputText = TextFieldValue(prompt, TextRange(prompt.length))
                                            focusRequester.requestFocus()
                                        }
                                    )
                                }
                            }

                            // A transaction the model proposed — the user confirms it (#170)
                            pendingAction?.let { action ->
                                item {
                                    PendingActionCard(
                                        action = action,
                                        currency = baseCurrency,
                                        enabled = !isConfirming,
                                        onConfirm = { viewModel.confirmPendingAction() },
                                        onDismiss = { viewModel.dismissPendingAction() }
                                    )
                                }
                            }

                            // Show streaming response if available
                            if (currentResponse.isNotEmpty()) {
                                item {
                                    ChatMessageItem(
                                        message = com.pennywiseai.tracker.data.database.entity.ChatMessage(
                                            message = currentResponse,
                                            isUser = false,
                                            timestamp = System.currentTimeMillis()
                                        ),
                                        isStreaming = true
                                    )
                                }
                            } else if (uiState.isLoading) {
                                // Show typing indicator while waiting for response
                                item {
                                    // One status line per request, picked when the wait starts,
                                    // so a tool call's silent few seconds don't look like a hang.
                                    val status = remember(uiState.isLoading) { THINKING_LINES.random() }
                                    TypingIndicator(status = stringResource(status))
                                }
                            }
                        }

                        // Error message
                        AnimatedVisibility(
                            visible = uiState.error != null,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
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
                                            Icons.Outlined.Close,
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
                                        viewModel.sendMessage(inputText.text)
                                        inputText = TextFieldValue()
                                        // Keep keyboard open by requesting focus
                                        focusRequester.requestFocus()
                                    },
                                    enabled = inputText.text.isNotBlank() && !uiState.isLoading,
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
                                            Icons.AutoMirrored.Outlined.Send,
                                            contentDescription = stringResource(R.string.chat_send)
                                        )
                                    }
                                }
                            }
                        }
                        // The nav steps aside for the keyboard, so its room goes too.
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

/**
 * LlmRepository's greeting is a hello, a how-to and this month's snapshot. The how-to is
 * what the example prompts show, and it promises a chat a device without the model can't
 * have yet, so only the first and last paragraphs are shown.
 */
private fun shortGreeting(text: String): String {
    val paragraphs = text.split("\n\n")
    return if (paragraphs.size > 2) "${paragraphs.first()}\n\n${paragraphs.last()}" else text
}

/** Says what the model is and gets it: the only way forward until it's on the device. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ModelActivationPanel(
    isDownloading: Boolean,
    downloadProgress: Int,
    downloadedMB: Long,
    totalMB: Long,
    error: String?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit
) {
    PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = stringResource(if (isDownloading) R.string.chat_downloading_model else R.string.chat_model_required_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isDownloading) {
                Text(
                    text = stringResource(R.string.chat_download_progress, downloadedMB, totalMB),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearWavyProgressIndicator(
                    progress = { downloadProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = stringResource(R.string.chat_activation_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    // 1.6 GB via the system Download Manager; some phones (Samsung
                    // with Data saver) won't start it on mobile data.
                    text = stringResource(R.string.chat_model_size_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // What the download is for: the setup screen shouldn't be a dead end.
                Text(
                    text = stringResource(R.string.chat_preview_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
                listOf(
                    R.string.chat_example_prompt_coffee,
                    R.string.chat_example_prompt_salary,
                    R.string.chat_example_prompt_groceries,
                ).forEach { prompt ->
                    Text(
                        text = stringResource(prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                if (isDownloading) {
                    OutlinedButton(onClick = onCancel, shapes = pennyWiseButtonShapes()) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.chat_cancel))
                    }
                } else {
                    Button(onClick = onDownload, shapes = pennyWiseButtonShapes()) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.chat_download))
                    }
                }
                TextButton(onClick = onOpenSettings, shapes = pennyWiseButtonShapes()) {
                    Text(stringResource(R.string.chat_settings))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenLimitWarning(
    usagePercent: Int,
    onClearChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        usagePercent >= 95 -> MaterialTheme.colorScheme.errorContainer
        usagePercent >= 90 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val contentColor = when {
        usagePercent >= 95 -> MaterialTheme.colorScheme.onErrorContainer
        usagePercent >= 90 -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    
    val icon = when {
        usagePercent >= 95 -> Icons.Outlined.Error
        else -> Icons.Outlined.Warning
    }
    
    val message = when {
        usagePercent >= 95 -> stringResource(R.string.chat_memory_almost_full)
        usagePercent >= 90 -> stringResource(R.string.chat_memory_warning, usagePercent)
        else -> stringResource(R.string.chat_memory_usage, usagePercent)
    }
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(Dimensions.Icon.medium)
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
            }
            if (usagePercent >= 90) {
                TextButton(
                    onClick = onClearChat,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = contentColor
                    ),
                    shapes = pennyWiseButtonShapes()
                ) {
                    Text(stringResource(R.string.chat_clear), fontWeight = FontWeight.Bold)
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
                        Icons.Outlined.Code,
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
                        if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = stringResource(if (isExpanded) R.string.chat_collapse else R.string.chat_expand),
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
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
                        if (chatStats.systemPromptTokens > 0) {
                            Text(
                                text = stringResource(R.string.chat_stats_system_tokens, TokenUtils.formatNumber(chatStats.systemPromptTokens)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
    }
}

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
        PennyWiseCardV2(modifier = Modifier.widthIn(max = 280.dp)) {
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                                shape = RoundedCornerShape(50)
                            )
                    )
                }
                if (status != null) {
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(
    message: com.pennywiseai.tracker.data.database.entity.ChatMessage,
    isStreaming: Boolean = false
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        PennyWiseCardV2(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .animateContentSize(),
            // Tonal steps of the ordinary surface: the side of the screen says who's speaking.
            containerColor = if (message.isUser)
                MaterialTheme.colorScheme.surfaceContainerHigh
            else
                MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Text(
                // Amounts are baked into the stored text; mask them on screen only.
                text = com.pennywiseai.tracker.utils.CurrencyFormatter.maskAmountsIn(message.message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        stringResource(R.string.chat_example_prompt_groceries)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
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

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            examplePrompts.forEach { prompt ->
                SuggestionChip(
                    onClick = { onPromptClick(prompt) },
                    label = { Text(prompt) }
                )
            }
        }
    }
}

/** Confirm card for an action the AI proposed (#170): add, delete or update. Nothing happens without the tap. */
@Composable
private fun PendingActionCard(
    action: com.pennywiseai.tracker.data.model.PendingChatAction,
    currency: String,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // A new draft is in the base currency; an existing row is shown in its own.
    val fmt = { a: java.math.BigDecimal -> com.pennywiseai.tracker.utils.CurrencyFormatter.formatCurrency(a, currency) }
    val own = { t: com.pennywiseai.tracker.data.database.entity.TransactionEntity -> com.pennywiseai.tracker.utils.CurrencyFormatter.formatCurrency(t.amount, t.currency) }
    val income = com.pennywiseai.tracker.data.database.entity.TransactionType.INCOME
    val (title, headline, detail, button) = when (action) {
        is com.pennywiseai.tracker.data.model.PendingChatAction.Add -> {
            val d = action.draft
            listOf(
                stringResource(if (d.type == income) R.string.chat_action_add_income else R.string.chat_action_add_expense),
                "${fmt(d.amount)} · ${d.merchant}",
                stringResource(R.string.chat_action_add_detail, categoryLabel(d.category), d.accountLabel),
                stringResource(R.string.chat_action_add)
            )
        }
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
        modifier = Modifier.fillMaxWidth(),
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
                OutlinedButton(onClick = onDismiss, enabled = enabled, modifier = Modifier.weight(1f), shapes = pennyWiseButtonShapes()) { Text(stringResource(R.string.chat_cancel)) }
                Button(
                    onClick = onConfirm,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    colors = if (isDelete) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
                    shapes = pennyWiseButtonShapes()
                ) { Text(button) }
            }
        }
    }
}
